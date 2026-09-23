package com.auvra.app.player

import android.content.Context
import android.util.Log
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamingCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val okHttpClient: OkHttpClient
) {
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

        val downloadUrl = song.highQualityDownloadUrl
        if (downloadUrl.isNullOrEmpty()) {
            Log.w(TAG, "cacheSong: no download URL for '${song.name}'")
            return@withContext false
        }

        val tempFile = File(cacheDir, "${song.id}.tmp")
        try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            Log.d(TAG, "cacheSong: Start caching '${song.name}' from $downloadUrl")
            val request = Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "cacheSong: failed to download '${song.name}', HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }

            if (tempFile.renameTo(cacheFile)) {
                cacheFile.setLastModified(System.currentTimeMillis())
                Log.d(TAG, "cacheSong: Successfully cached '${song.name}'")
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
