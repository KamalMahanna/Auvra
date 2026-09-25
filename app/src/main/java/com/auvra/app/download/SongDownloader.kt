package com.auvra.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory

data class DownloadState(
    val songId: String = "",
    val songName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

@Singleton
class SongDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auvra Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music download progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun downloadSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "downloadSong: songId='${song.id}', name='${song.name}'")
        val downloadUrl = song.highQualityDownloadUrl
        if (downloadUrl == null) {
            Log.e(TAG, "downloadSong: highQualityDownloadUrl is null for song='${song.name}'")
            return@withContext false
        }
        val file = downloadRepository.getFileForSong(song)

        if (file.exists() && file.canRead()) {
            Log.d(TAG, "downloadSong: song already downloaded and readable, file exists at '${file.absolutePath}'")
            return@withContext true
        }

        val startState = DownloadState(
            songId = song.id,
            songName = song.name,
            isDownloading = true
        )
        _downloadStates.update { it + (song.id to startState) }

        val tempFile = File(context.cacheDir, "temp_download_${song.id}.m4a")
        if (tempFile.exists()) {
            val deleted = tempFile.delete()
            Log.d(TAG, "downloadSong: Cleaned up pre-existing temp file: $deleted")
        }

        try {
            Log.d(TAG, "downloadSong: Downloading to temp file: ${tempFile.absolutePath} from URL: $downloadUrl")
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "downloadSong: Download request failed with HTTP ${response.code} for URL: $downloadUrl")
                val errorState = (_downloadStates.value[song.id] ?: DownloadState(songId = song.id, songName = song.name)).copy(
                    isDownloading = false,
                    error = "Download failed: ${response.code}"
                )
                _downloadStates.update { it + (song.id to errorState) }
                return@withContext false
            }

            val body = response.body
            if (body == null) {
                Log.e(TAG, "downloadSong: Response body is null")
                val errorState = (_downloadStates.value[song.id] ?: DownloadState(songId = song.id, songName = song.name)).copy(
                    isDownloading = false,
                    error = "Empty response body"
                )
                _downloadStates.update { it + (song.id to errorState) }
                return@withContext false
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            Log.d(TAG, "downloadSong: Response Content-Length = $totalBytes bytes")

            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else 0f

                        val progressState = (_downloadStates.value[song.id] ?: DownloadState(songId = song.id, songName = song.name, isDownloading = true)).copy(progress = progress)
                        _downloadStates.update { it + (song.id to progressState) }
                        updateNotification(song.name, (progress * 100).toInt())
                    }
                }
            }

            Log.d(TAG, "downloadSong: Finished downloading file body ($downloadedBytes bytes). Writing metadata tags.")
            // Write metadata and download/embed artwork
            writeMetadataTags(tempFile, song)

            Log.d(TAG, "downloadSong: Copying tagged file to final destination: ${file.absolutePath}")
            file.parentFile?.mkdirs()
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()

            Log.d(TAG, "downloadSong: Complete! Saved to ${file.absolutePath}")
            val completeState = DownloadState(
                songId = song.id,
                songName = song.name,
                progress = 1f,
                isDownloading = false,
                isComplete = true
            )
            _downloadStates.update { it + (song.id to completeState) }

            showCompleteNotification(song.name)
            downloadRepository.refreshDownloadedSongs()
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadSong: Error during download/tagging", e)
            if (tempFile.exists()) tempFile.delete()
            if (file.exists()) file.delete()
            val exceptionState = DownloadState(
                songId = song.id,
                songName = song.name,
                isDownloading = false,
                error = e.message
            )
            _downloadStates.update { it + (song.id to exceptionState) }
            false
        }
    }

    private fun updateNotification(songName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(songName)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCompleteNotification(songName: String) {
        notificationManager.cancel(NOTIFICATION_ID)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(songName)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun writeMetadataTags(file: File, song: Song) {
        try {
            // Suppress verbose jaudiotagger logging
            Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF

            Log.d(TAG, "writeMetadataTags: Reading audio file: ${file.absolutePath}")
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault

            Log.d(TAG, "writeMetadataTags: Setting metadata: Title='${song.name}', Artist='${song.primaryArtistNames}', Album='${song.album.name}'")
            tag.setField(FieldKey.TITLE, song.name)
            tag.setField(FieldKey.ARTIST, song.primaryArtistNames)
            tag.setField(FieldKey.ALBUM, song.album.name ?: "")

            // Download and embed cover art
            val imageBytes = downloadArtworkBytes(song.highQualityImageUrl)
            if (imageBytes != null) {
                try {
                    Log.d(TAG, "writeMetadataTags: Embedding artwork bytes (size=${imageBytes.size} bytes)")
                    // Write image to a temp file in cache for ArtworkFactory
                    val tempImageFile = File(context.cacheDir, "temp_artwork_${song.id}.jpg")
                    tempImageFile.writeBytes(imageBytes)
                    val artwork = ArtworkFactory.createArtworkFromFile(tempImageFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                    tempImageFile.delete()
                    Log.d(TAG, "writeMetadataTags: Artwork successfully embedded in tag")
                } catch (artEx: Exception) {
                    Log.e(TAG, "writeMetadataTags: Failed to embed artwork tag", artEx)
                }
            } else {
                Log.w(TAG, "writeMetadataTags: No artwork bytes downloaded")
            }

            Log.d(TAG, "writeMetadataTags: Committing audio file...")
            audioFile.commit()
            Log.d(TAG, "writeMetadataTags: Successfully committed tags to file.")
        } catch (e: Exception) {
            Log.e(TAG, "writeMetadataTags: Failed to write metadata tags", e)
        }
    }

    private fun downloadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) {
            Log.d(TAG, "downloadArtworkBytes: URL is null or empty")
            return null
        }
        return try {
            Log.d(TAG, "downloadArtworkBytes: Downloading from $url")
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    Log.w(TAG, "downloadArtworkBytes: Failed, HTTP response code ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadArtworkBytes: Error fetching artwork from $url: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "SongDownloader"
        private const val CHANNEL_ID = "auvra_downloads"
        private const val NOTIFICATION_ID = 1001
    }
}
