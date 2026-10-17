package com.auvra.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auvra.app.data.model.DownloadedSong
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.DownloadRepository
import com.auvra.app.utils.resize
import com.auvra.app.player.AudioQuality
import com.auvra.app.player.InnerTubeXPlayer
import com.auvra.app.player.StreamingCacheManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import android.media.MediaScannerConnection
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.util.logging.Logger
import kotlin.math.abs

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
    private val streamingCacheManager: StreamingCacheManager,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val downloaderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSemaphore = Semaphore(permits = 2)

    private val downloadHttpClient by lazy {
        okHttpClient.newBuilder()
            .cache(null)
            .build()
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music download progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun enqueueDownload(song: Song) {
        val current = _downloadStates.value[song.id]
        if (current?.isDownloading == true) {
            Log.d(TAG, "enqueueDownload: '${song.name}' is already in download queue/progress")
            return
        }

        val file = downloadRepository.getFileForSong(song)
        if (file.exists() && file.canRead()) {
            Log.d(TAG, "enqueueDownload: '${song.name}' already downloaded on disk")
            _downloadStates.update {
                it + (song.id to DownloadState(
                    songId = song.id,
                    songName = song.name,
                    progress = 1f,
                    isDownloading = false,
                    isComplete = true
                ))
            }
            return
        }

        val startState = DownloadState(
            songId = song.id,
            songName = song.name,
            progress = 0f,
            isDownloading = true
        )
        _downloadStates.update { it + (song.id to startState) }

        downloaderScope.launch {
            downloadSemaphore.withPermit {
                executeDownload(song)
            }
        }
    }

    fun enqueueDownloads(songs: List<Song>) {
        songs.forEach { enqueueDownload(it) }
    }

    suspend fun downloadSong(song: Song): Boolean {
        enqueueDownload(song)
        return withContext(Dispatchers.IO) {
            while (isActive) {
                val state = _downloadStates.value[song.id] ?: return@withContext false
                if (state.isComplete) return@withContext true
                if (state.error != null) return@withContext false
                if (!state.isDownloading) return@withContext false
                delay(150)
            }
            false
        }
    }

    private suspend fun executeDownload(song: Song): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "executeDownload: songId='${song.id}', name='${song.name}'")

        val file = downloadRepository.getFileForSong(song)
        if (file.exists() && file.canRead()) {
            Log.d(TAG, "executeDownload: song already downloaded, file='${file.absolutePath}'")
            _downloadStates.update {
                it + (song.id to DownloadState(
                    songId = song.id,
                    songName = song.name,
                    progress = 1f,
                    isDownloading = false,
                    isComplete = true
                ))
            }
            return@withContext true
        }

        // Fast-path: Check if song is already cached from playback/streaming and is a valid MP4 container
        val cachedFile = streamingCacheManager.getCachedFileForSong(song)
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0 && isMp4Container(cachedFile)) {
            Log.d(TAG, "executeDownload: Found cached MP4 audio in StreamingCacheManager (${cachedFile.length()} bytes). Reusing local cache!")
            _downloadStates.update { it + (song.id to (_downloadStates.value[song.id] ?: DownloadState(song.id, song.name)).copy(progress = 0.5f, isDownloading = true)) }
            updateNotification(song.id, song.name, 50)

            val tempFile = File(context.cacheDir, ".temp_download_${song.id}.m4a")
            if (tempFile.exists()) tempFile.delete()

            try {
                cachedFile.copyTo(tempFile, overwrite = true)
                val artworkUrl = resolveArtworkUrl(song, null)
                val artworkBytes = downloadArtworkBytes(artworkUrl)
                val savedArtwork = if (artworkBytes != null) {
                    downloadRepository.saveArtwork(song.id, file.nameWithoutExtension, artworkBytes)
                } else {
                    downloadRepository.getArtworkFile(song.id, file.nameWithoutExtension)
                }

                // 1. Tag & embed artwork into tempFile while still in internal cacheDir
                writeMetadataTags(tempFile, song, artworkBytes)

                // 2. Move fully tagged file to destination
                file.parentFile?.mkdirs()
                val renamed = tempFile.renameTo(file)
                if (!renamed) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }

                // 3. Scan with MediaScannerConnection so external players index metadata & cover art
                try {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("audio/mp4"),
                        null
                    )
                } catch (_: Exception) {}

                val ds = DownloadedSong(
                    id = song.id,
                    name = song.name,
                    artist = song.primaryArtistNames,
                    album = song.album.name,
                    duration = song.duration,
                    filePath = file.absolutePath,
                    imageUrl = savedArtwork?.absolutePath,
                    fileSize = file.length()
                )
                downloadRepository.saveDownloadedSong(ds)

                Log.d(TAG, "executeDownload: Instant download complete from cache! Saved to ${file.absolutePath}")
                val completeState = DownloadState(
                    songId = song.id,
                    songName = song.name,
                    progress = 1f,
                    isDownloading = false,
                    isComplete = true
                )
                _downloadStates.update { it + (song.id to completeState) }
                showCompleteNotification(song.id, song.name)
                downloadRepository.refreshDownloadedSongs()
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "executeDownload: Failed to reuse cached file, falling back to network download", e)
                if (tempFile.exists()) tempFile.delete()
            }
        }

        // Start downloading artwork concurrently while audio stream resolves
        val initialArtworkUrl = resolveArtworkUrl(song, null)
        val artworkDeferred = async(Dispatchers.IO) {
            downloadArtworkBytes(initialArtworkUrl)
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val playbackData = InnerTubeXPlayer.playerResponseForDownload(
            videoId = song.id,
            connectivityManager = cm,
        ).getOrNull()

        val downloadUrl = song.highQualityDownloadUrl ?: playbackData?.streamUrl
        if (downloadUrl == null) {
            artworkDeferred.cancel()
            Log.e(TAG, "executeDownload: failed to resolve download URL for song='${song.name}'")
            val errorState = DownloadState(
                songId = song.id,
                songName = song.name,
                isDownloading = false,
                error = "Failed to resolve stream URL"
            )
            _downloadStates.update { it + (song.id to errorState) }
            cancelNotification(song.id)
            return@withContext false
        }

        var totalBytes = playbackData?.format?.contentLength?.takeIf { it > 0L } ?: -1L
        if (totalBytes <= 0L) {
            val probeRequest = Request.Builder()
                .url(downloadUrl)
                .header("Range", "bytes=0-0")
                .cacheControl(CacheControl.Builder().noStore().noCache().build())
                .apply {
                    playbackData?.streamHeaders?.forEach { (key, value) -> header(key, value) }
                }
                .build()
            try {
                downloadHttpClient.newCall(probeRequest).execute().use { probeResponse ->
                    totalBytes = downloadContentLength(
                        statusCode = probeResponse.code,
                        contentRange = probeResponse.header("Content-Range"),
                        contentLength = probeResponse.header("Content-Length")
                    ) ?: -1L
                }
            } catch (e: Exception) {
                Log.w(TAG, "executeDownload: Failed to probe content length for '${song.name}': ${e.message}")
            }
        }

        val tempFile = File(context.cacheDir, ".temp_download_${song.id}.m4a")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        try {
            // Optimized chunk size: at least 3MB per request to minimize HTTP roundtrips
            val chunkSize = maxOf(
                playbackData?.rangeChunkSizeBytes ?: 0L,
                3 * 1024 * 1024L
            )
            Log.d(TAG, "executeDownload: Downloading to ${tempFile.absolutePath} (totalBytes=$totalBytes, chunkSize=$chunkSize)")

            var downloadedBytes = 0L
            var lastNotifiedTime = 0L
            var lastNotifiedPercent = -1
            var lastStateUpdateTime = 0L
            var lastStateProgress = 0f

            if (totalBytes > 0L) {
                BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024).use { output ->
                    var startByte = 0L
                    val buffer = ByteArray(64 * 1024)

                    while (startByte < totalBytes) {
                        val endByte = minOf(startByte + chunkSize - 1, totalBytes - 1)
                        val chunkRequest = Request.Builder()
                            .url(downloadUrl)
                            .header("Range", "bytes=$startByte-$endByte")
                            .cacheControl(CacheControl.Builder().noStore().noCache().build())
                            .apply {
                                playbackData?.streamHeaders?.forEach { (key, value) -> header(key, value) }
                            }
                            .build()

                        val chunkResponse = downloadHttpClient.newCall(chunkRequest).execute()
                        if (!chunkResponse.isSuccessful && chunkResponse.code != 206) {
                            throw IOException("Failed chunk request $startByte-$endByte, HTTP ${chunkResponse.code}")
                        }

                        val chunkBody = chunkResponse.body ?: throw IOException("Empty body for chunk $startByte-$endByte")
                        chunkBody.byteStream().use { input ->
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                output.write(buffer, 0, n)
                                downloadedBytes += n
                            }
                        }
                        output.flush()

                        if (chunkResponse.code == 200) {
                            break
                        }

                        startByte = endByte + 1

                        val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                        val percent = (progress * 100).toInt().coerceIn(0, 100)
                        val now = SystemClock.elapsedRealtime()

                        if (now - lastStateUpdateTime >= 100L || progress - lastStateProgress >= 0.01f || progress >= 1f) {
                            lastStateUpdateTime = now
                            lastStateProgress = progress
                            val progressState = (_downloadStates.value[song.id] ?: DownloadState(songId = song.id, songName = song.name, isDownloading = true)).copy(progress = progress)
                            _downloadStates.update { it + (song.id to progressState) }
                        }

                        if (percent != lastNotifiedPercent && (now - lastNotifiedTime >= 250L || percent == 100)) {
                            lastNotifiedPercent = percent
                            lastNotifiedTime = now
                            updateNotification(song.id, song.name, percent)
                        }
                    }
                }
            } else {
                // Fallback for unbounded streams
                val requestBuilder = Request.Builder()
                    .url(downloadUrl)
                    .cacheControl(CacheControl.Builder().noStore().noCache().build())

                playbackData?.streamHeaders?.forEach { (key, value) ->
                    requestBuilder.header(key, value)
                }

                val response = downloadHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    throw IOException("Download request failed with HTTP ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val streamLength = body.contentLength()
                val buffer = ByteArray(64 * 1024)

                BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024).use { output ->
                    BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (streamLength > 0) downloadedBytes.toFloat() / streamLength.toFloat() else 0f
                            val percent = (progress * 100).toInt().coerceIn(0, 100)
                            val now = SystemClock.elapsedRealtime()

                            if (now - lastStateUpdateTime >= 100L || progress - lastStateProgress >= 0.01f || progress >= 1f) {
                                lastStateUpdateTime = now
                                lastStateProgress = progress
                                val progressState = (_downloadStates.value[song.id] ?: DownloadState(songId = song.id, songName = song.name, isDownloading = true)).copy(progress = progress)
                                _downloadStates.update { it + (song.id to progressState) }
                            }

                            if (percent != lastNotifiedPercent && (now - lastNotifiedTime >= 250L || percent == 100)) {
                                lastNotifiedPercent = percent
                                lastNotifiedTime = now
                                updateNotification(song.id, song.name, percent)
                            }
                        }
                        output.flush()
                    }
                }
            }

            Log.d(TAG, "executeDownload: Finished downloading audio body ($downloadedBytes bytes). Tagging file with metadata and artwork.")
            var artworkBytes = artworkDeferred.await()
            if (artworkBytes == null) {
                val fallbackUrl = playbackData?.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url
                    ?: playbackData?.videoDetails?.thumbnail?.thumbnails?.firstOrNull()?.url
                    ?: "https://i.ytimg.com/vi/${song.id}/hqdefault.jpg"
                artworkBytes = downloadArtworkBytes(fallbackUrl)
            }

            var savedArtwork: File? = null
            if (artworkBytes != null) {
                savedArtwork = downloadRepository.saveArtwork(song.id, file.nameWithoutExtension, artworkBytes)
            } else {
                savedArtwork = downloadRepository.getArtworkFile(song.id, file.nameWithoutExtension)
            }

            // 1. Tag & embed artwork into tempFile in context.cacheDir BEFORE moving to destination
            writeMetadataTags(tempFile, song, artworkBytes)

            // 2. Move fully tagged file to final destination
            file.parentFile?.mkdirs()
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }

            // 3. Scan file so system media indexer and external players detect tags & artwork
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )
            } catch (_: Exception) {}

            if (savedArtwork == null) {
                savedArtwork = downloadRepository.getArtworkFile(song.id, file.nameWithoutExtension)
            }

            val ds = DownloadedSong(
                id = song.id,
                name = song.name,
                artist = song.primaryArtistNames,
                album = song.album.name,
                duration = song.duration,
                filePath = file.absolutePath,
                imageUrl = savedArtwork?.absolutePath,
                fileSize = file.length()
            )
            downloadRepository.saveDownloadedSong(ds)

            Log.d(TAG, "executeDownload: Complete! Saved to ${file.absolutePath}, artwork=${savedArtwork?.absolutePath}")
            val completeState = DownloadState(
                songId = song.id,
                songName = song.name,
                progress = 1f,
                isDownloading = false,
                isComplete = true
            )
            _downloadStates.update { it + (song.id to completeState) }

            showCompleteNotification(song.id, song.name)
            downloadRepository.refreshDownloadedSongs()
            true
        } catch (e: Exception) {
            Log.e(TAG, "executeDownload: Error during download", e)
            if (tempFile.exists()) tempFile.delete()
            val exceptionState = DownloadState(
                songId = song.id,
                songName = song.name,
                isDownloading = false,
                error = e.message
            )
            _downloadStates.update { it + (song.id to exceptionState) }
            cancelNotification(song.id)
            false
        }
    }

    private fun getNotificationIdForSong(songId: String): Int {
        return (abs(songId.hashCode()) % 100000) + 2000
    }

    private fun updateNotification(songId: String, songName: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(songName)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setSilent(true)
            .build()

        notificationManager.notify(getNotificationIdForSong(songId), notification)
    }

    private fun showCompleteNotification(songId: String, songName: String) {
        val notifId = getNotificationIdForSong(songId)
        notificationManager.cancel(notifId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText(songName)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId, notification)
    }

    private fun cancelNotification(songId: String) {
        notificationManager.cancel(getNotificationIdForSong(songId))
    }

    private fun downloadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) {
            return null
        }
        val safeUrl = normalizeUrl(url)
        return try {
            val request = Request.Builder()
                .url(safeUrl)
                .cacheControl(CacheControl.Builder().noStore().noCache().build())
                .build()
            downloadHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val rawBytes = response.body?.bytes()
                    if (rawBytes != null && rawBytes.isNotEmpty()) {
                        try {
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                            if (bitmap != null) {
                                val stream = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, stream)
                                stream.toByteArray()
                            } else {
                                rawBytes
                            }
                        } catch (_: Exception) {
                            rawBytes
                        }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadArtworkBytes: Error fetching artwork from $safeUrl: ${e.message}", e)
            null
        }
    }

    private fun resolveArtworkUrl(song: Song, playbackData: InnerTubeXPlayer.PlaybackData?): String? {
        val candidate = song.highQualityImageUrl
            ?: song.mediumQualityImageUrl
            ?: song.image.firstOrNull()?.url?.resize(1080, 1080)
            ?: playbackData?.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url?.resize(1080, 1080)
            ?: playbackData?.videoDetails?.thumbnail?.thumbnails?.firstOrNull()?.url?.resize(1080, 1080)
        return candidate?.let { normalizeUrl(it) }
    }

    private fun normalizeUrl(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun isMp4Container(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@use false
                header[4] == 'f'.code.toByte() &&
                header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() &&
                header[7] == 'p'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun writeMetadataTags(file: File, song: Song, artworkBytes: ByteArray?) {
        try {
            Logger.getLogger("org.jaudiotagger").level = java.util.logging.Level.OFF
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateAndSetDefault
            tag.setField(FieldKey.TITLE, song.name)
            tag.setField(FieldKey.ARTIST, song.primaryArtistNames)
            song.album.name?.let { if (it.isNotBlank()) tag.setField(FieldKey.ALBUM, it) }

            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                try {
                    if (tag is org.jaudiotagger.tag.mp4.Mp4Tag) {
                        try { tag.deleteField(FieldKey.COVER_ART) } catch (_: Exception) {}
                        val artworkField = tag.createArtworkField(artworkBytes)
                        tag.setField(artworkField)
                    } else {
                        val tempArt = File(context.cacheDir, "temp_art_${System.currentTimeMillis()}_${file.nameWithoutExtension}.jpg")
                        tempArt.writeBytes(artworkBytes)
                        try {
                            val artwork = ArtworkFactory.createArtworkFromFile(tempArt)
                            try { tag.deleteArtworkField() } catch (_: Exception) {}
                            tag.setField(artwork)
                        } finally {
                            tempArt.delete()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "writeMetadataTags: Failed setting artwork: ${e.message}")
                }
            }
            audioFile.commit()
            Log.d(TAG, "writeMetadataTags: Successfully embedded ID3/MP4 metadata & cover art for '${song.name}'")
        } catch (e: Throwable) {
            Log.w(TAG, "writeMetadataTags: Failed for '${file.name}': ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SongDownloader"
        private const val CHANNEL_ID = "auvra_downloads"

        private val PARTIAL_CONTENT_RANGE = Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE)
        private val UNSATISFIED_CONTENT_RANGE = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

        private fun downloadContentLength(
            statusCode: Int,
            contentRange: String?,
            contentLength: String?,
        ): Long? {
            val rangePattern = when (statusCode) {
                206 -> PARTIAL_CONTENT_RANGE
                416 -> UNSATISFIED_CONTENT_RANGE
                else -> null
            }
            if (rangePattern != null) {
                return contentRange
                    ?.trim()
                    ?.let(rangePattern::matchEntire)
                    ?.groupValues
                    ?.get(1)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
            }
            return if (statusCode == 200) contentLength?.toLongOrNull()?.takeIf { it > 0L } else null
        }
    }
}
