package com.auvra.app.player

import android.content.Context
import android.util.Log
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

@Singleton
class StreamingCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val okHttpClient: OkHttpClient
) {
    private val downloadHttpClient by lazy {
        okHttpClient.newBuilder()
            .cache(null)
            .build()
    }
    private val cacheDir: File
        get() = File(context.cacheDir, "streaming_cache").apply {
            if (!exists()) {
                val created = mkdirs()
                Log.d(TAG, "cacheDir: Created streaming_cache directory: $created")
            }
        }

    companion object {
        private const val TAG = "StreamingCacheManager"
        private const val MAX_CACHED_SONGS = 50
    }

    /**
     * Checks if a song is cached in the streaming cache.
     * If it is cached, updates its last modified time (LRU) and returns the cached File.
     */
    fun getCachedFileForSong(song: Song): File? {
        val cacheFile = File(cacheDir, "${song.id}.cache")
        return if (cacheFile.exists() && cacheFile.canRead()) {
            val updated = cacheFile.setLastModified(System.currentTimeMillis())
            Log.d(TAG, "getCachedFileForSong: Cache hit for '${song.name}', updated last modified: $updated")
            cacheFile
        } else {
            Log.d(TAG, "getCachedFileForSong: Cache miss for '${song.name}'")
            null
        }
    }

    /**
     * Downloads and caches the song in the background.
     * Returns true if successfully cached, false otherwise.
     */
    suspend fun cacheSong(song: Song): Boolean = withContext(Dispatchers.IO) {
        if (downloadRepository.isSongDownloaded(song)) {
            Log.d(TAG, "cacheSong: '${song.name}' is already downloaded, skipping caching.")
            return@withContext false
        }

        val cacheFile = File(cacheDir, "${song.id}.cache")
        if (cacheFile.exists()) {
            cacheFile.setLastModified(System.currentTimeMillis())
            Log.d(TAG, "cacheSong: '${song.name}' is already in cache.")
            return@withContext true
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val playbackData = InnerTubeXPlayer.playerResponseForPlayback(
            videoId = song.id,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = cm,
            allowBoundedRange = false,
        ).getOrNull()

        val downloadUrl = song.highQualityDownloadUrl ?: playbackData?.streamUrl
        if (downloadUrl.isNullOrEmpty()) {
            Log.w(TAG, "cacheSong: failed to resolve stream URL for '${song.name}'")
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
                    val contentRange = probeResponse.header("Content-Range")
                    val match = Regex("""bytes\s+0-0/(\d+)""", RegexOption.IGNORE_CASE).matchEntire(contentRange.orEmpty())
                    totalBytes = match?.groupValues?.get(1)?.toLongOrNull()
                        ?: probeResponse.header("Content-Length")?.toLongOrNull()
                        ?: -1L
                }
            } catch (_: Exception) { }
        }

        val tempFile = File(cacheDir, "${song.id}.tmp")
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val chunkSize = maxOf(
                playbackData?.rangeChunkSizeBytes ?: 0L,
                2 * 1024 * 1024L // 2 MB chunks bypass YouTube pacing with fewer HTTP roundtrips
            )

            Log.d(TAG, "cacheSong: Start caching '${song.name}' from $downloadUrl (totalBytes=$totalBytes, chunkSize=$chunkSize)")

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
                                playbackData?.streamHeaders?.forEach { (k, v) -> header(k, v) }
                            }
                            .build()

                        val chunkResponse = downloadHttpClient.newCall(chunkRequest).execute()
                        if (!chunkResponse.isSuccessful && chunkResponse.code != 206) {
                            throw IOException("Failed chunk $startByte-$endByte, HTTP ${chunkResponse.code}")
                        }

                        val chunkBody = chunkResponse.body ?: throw IOException("Empty body for chunk $startByte-$endByte")
                        chunkBody.byteStream().use { input ->
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                output.write(buffer, 0, n)
                            }
                        }
                        output.flush()

                        if (chunkResponse.code == 200) break
                        startByte = endByte + 1
                    }
                }
            } else {
                val requestBuilder = Request.Builder()
                    .url(downloadUrl)
                    .cacheControl(CacheControl.Builder().noStore().noCache().build())
                playbackData?.streamHeaders?.forEach { (key, value) -> requestBuilder.header(key, value) }

                val response = downloadHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "cacheSong: failed to download '${song.name}', HTTP ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: return@withContext false
                BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024).use { output ->
                    BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                        input.copyTo(output)
                    }
                }
            }

            if (tempFile.renameTo(cacheFile)) {
                cacheFile.setLastModified(System.currentTimeMillis())
                Log.d(TAG, "cacheSong: Successfully cached '${song.name}' (${cacheFile.length()} bytes)")
                enforceCacheLimit()
                return@withContext true
            } else {
                Log.e(TAG, "cacheSong: Failed to rename temp file for '${song.name}'")
                tempFile.delete()
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "cacheSong: Error caching '${song.name}': ${e.message}", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            return@withContext false
        }
    }

    /**
     * Cleans up any unfinished temporary downloads in the cache directory.
     */
    fun cleanTempFiles() {
        try {
            val files = cacheDir.listFiles { _, name -> name.endsWith(".tmp") } ?: return
            for (file in files) {
                val deleted = file.delete()
                Log.d(TAG, "cleanTempFiles: Deleted temp file '${file.name}': $deleted")
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanTempFiles: Error cleaning up temp files: ${e.message}")
        }
    }

    /**
     * Enforces the limit of MAX_CACHED_SONGS (50) by deleting the oldest cached files.
     */
    private fun enforceCacheLimit() {
        try {
            val cacheFiles = cacheDir.listFiles { _, name -> name.endsWith(".cache") } ?: return
            if (cacheFiles.size > MAX_CACHED_SONGS) {
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
                val toDeleteCount = cacheFiles.size - MAX_CACHED_SONGS
                Log.d(TAG, "enforceCacheLimit: cache size is ${cacheFiles.size}, deleting $toDeleteCount oldest files.")
                for (i in 0 until toDeleteCount) {
                    val fileToDelete = sortedFiles[i]
                    val deleted = fileToDelete.delete()
                    Log.d(TAG, "enforceCacheLimit: Deleted oldest cache file '${fileToDelete.name}': $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "enforceCacheLimit: Error cleaning up cache: ${e.message}", e)
        }
    }
}
