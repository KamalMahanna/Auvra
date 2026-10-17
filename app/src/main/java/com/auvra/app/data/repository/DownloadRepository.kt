package com.auvra.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.os.Environment
import com.auvra.app.data.local.DownloadedSongDao
import com.auvra.app.data.model.DownloadedSong
import com.auvra.app.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages downloaded songs stored in Music/Auvra/.
 *
 * Saves songs into the public Music directory:
 *  - Full audio files accessible to user and other players
 *  - Room DB keeps fast indexed records
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedSongDao: DownloadedSongDao,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: Flow<List<DownloadedSong>> = _downloadedSongs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @Volatile
    private var downloadedFileNames = emptySet<String>()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Storage directory in public Music/Auvra
     */
    val downloadDir: File
        get() {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "Auvra"
            )
            if (!dir.exists()) {
                val created = dir.mkdirs()
                Log.d(TAG, "downloadDir: Created directory Auvra: $created")
            }
            return dir
        }

    /**
     * Dedicated persistent directory for song artwork: Android/data/com.auvra.app/files/artwork
     * Avoids EPERM issues on Android 11+ which restricts non-audio files in public Music/
     */
    val artworkDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "artwork")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    init {
        // Keep the StateFlow in sync with the Room DB
        repositoryScope.launch {
            downloadedSongDao.getAllDownloadedSongs().collect { songs ->
                val allowedPrefix = downloadDir.absolutePath.trimEnd(File.separatorChar) + File.separator
                val validSongs = songs.filter { it.filePath.startsWith(allowedPrefix) }
                val sorted = validSongs.sortedByDescending { it.filePath }
                _downloadedSongs.value = sorted
                downloadedFileNames = validSongs.map { File(it.filePath).name.lowercase() }.toSet()
                Log.d(TAG, "DB flow: ${validSongs.size} songs (raw: ${songs.size})")
            }
        }

        // Sync DB with disk on startup
        repositoryScope.launch {
            refreshDownloadedSongs()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // File helpers
    // ──────────────────────────────────────────────────────────────────────────

    fun getFileForSong(song: Song): File {
        val sanitizedArtist = song.primaryArtistNames.replace(SANITIZE_REGEX, "").trim()
        val sanitizedName   = song.name.replace(SANITIZE_REGEX, "").trim()
        return File(downloadDir, "$sanitizedName - $sanitizedArtist.m4a")
    }

    fun getReadableFileForSong(song: Song): File? {
        val allowedPrefix = downloadDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        // Direct file path stored in DB / Song.url
        if (song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:"))) {
            val path = if (song.url.startsWith("file://")) song.url.substring(7) else song.url
            val f = File(path)
            if (f.absolutePath.startsWith(allowedPrefix) && f.exists() && f.canRead()) return f
        }
        val primary = getFileForSong(song)
        if (primary.exists() && primary.canRead()) return primary
        return null
    }

    fun isSongDownloaded(song: Song): Boolean {
        val names = downloadedFileNames
        if (names.isNotEmpty()) {
            val sanitizedArtist = song.primaryArtistNames.replace(SANITIZE_REGEX, "").trim()
            val sanitizedName   = song.name.replace(SANITIZE_REGEX, "").trim()
            return names.contains("$sanitizedName - $sanitizedArtist.m4a".lowercase())
        }
        return getReadableFileForSong(song) != null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Artwork helpers  (stored as <name>.jpg alongside the audio file)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Save artwork bytes to <downloadDir>/<fileNameWithoutExtension>.jpg.
     * Returns the saved File, or null on failure.
     */
    fun saveArtwork(songId: String, fileNameWithoutExtension: String?, imageBytes: ByteArray): File? {
        if (imageBytes.isEmpty()) return null
        return try {
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val jpegBytes = if (bitmap != null) {
                val bos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                bos.toByteArray()
            } else {
                imageBytes
            }

            val name = fileNameWithoutExtension?.ifBlank { null }
                ?: songId.replace(SANITIZE_REGEX, "").trim().ifEmpty { "artwork_${System.currentTimeMillis()}" }
            val artFile = File(artworkDir, "$name.jpg")
            artFile.writeBytes(jpegBytes)
            Log.d(TAG, "saveArtwork: ${jpegBytes.size} bytes → ${artFile.absolutePath}")
            artFile
        } catch (e: Exception) {
            Log.e(TAG, "saveArtwork: failed", e)
            null
        }
    }

    /**
     * Find the artwork file for a song by looking for <name>.jpg next to the audio file or in artworkDir.
     */
    fun getArtworkFile(songId: String?, fileNameWithoutExtension: String?): File? {
        val searchDirs = listOfNotNull(
            artworkDir,
            downloadDir,
            File(context.cacheDir, "artwork")
        )
        for (dir in searchDirs) {
            if (!fileNameWithoutExtension.isNullOrBlank()) {
                val f = File(dir, "$fileNameWithoutExtension.jpg")
                if (f.exists() && f.canRead() && f.length() > 0) return f
            }
            if (!songId.isNullOrBlank()) {
                val sanitized = songId.replace(SANITIZE_REGEX, "").trim()
                val f = File(dir, "$sanitized.jpg")
                if (f.exists() && f.canRead() && f.length() > 0) return f
            }
        }
        return null
    }

    fun getCachedArtworkForSong(song: Song): File? {
        // Check imageUrl stored in DB first
        val allowedPrefix = downloadDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        song.highQualityImageUrl?.let { url ->
            if (url.startsWith("/") || url.startsWith("file:")) {
                val path = if (url.startsWith("file://")) url.substring(7) else url
                val f = File(path)
                val isAllowed = f.absolutePath.startsWith(artworkDir.absolutePath) ||
                        f.absolutePath.startsWith(allowedPrefix) ||
                        f.absolutePath.startsWith(context.cacheDir.absolutePath)
                if (isAllowed && f.exists() && f.canRead() && f.length() > 0) return f
            }
        }
        val audioFile = getReadableFileForSong(song)
        return getArtworkFile(song.id, audioFile?.nameWithoutExtension)
    }

    fun downloadArtworkBytes(url: String?): ByteArray? {
        if (url.isNullOrEmpty()) return null
        val safeUrl = if (url.startsWith("//")) "https:$url" else url
        return try {
            val req = Request.Builder()
                .url(safeUrl)
                .cacheControl(CacheControl.Builder().noStore().noCache().build())
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val raw = resp.body?.bytes()
                    if (raw != null && raw.isNotEmpty()) {
                        try {
                            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                            if (bmp != null) {
                                val bos = ByteArrayOutputStream()
                                bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                                bos.toByteArray()
                            } else raw
                        } catch (_: Exception) { raw }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadArtworkBytes failed for '$safeUrl': ${e.message}")
            null
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DB helpers
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun saveDownloadedSong(song: DownloadedSong) = withContext(Dispatchers.IO) {
        try {
            downloadedSongDao.insertDownloadedSong(song)
            Log.d(TAG, "saveDownloadedSong: '${song.name}' inserted")
        } catch (e: Exception) {
            Log.e(TAG, "saveDownloadedSong: failed for '${song.name}'", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Sync: disk ↔ DB
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun refreshDownloadedSongs() = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        try {
            val dir = downloadDir
            val allowedPrefix = dir.absolutePath.trimEnd(File.separatorChar) + File.separator
            Log.d(TAG, "refreshDownloadedSongs: scanning strictly in '$allowedPrefix'")

            // 1. Purge any database records not strictly in Music/Auvra/
            try {
                downloadedSongDao.deleteSongsNotInDirectory(allowedPrefix)
            } catch (e: Exception) {
                Log.w(TAG, "deleteSongsNotInDirectory failed: ${e.message}")
            }

            // Migrate any files from app-private files dir (if downloaded previously)
            try {
                val appPrivateDir = File(context.getExternalFilesDir("Music"), "Auvra")
                if (appPrivateDir.exists()) {
                    appPrivateDir.listFiles()?.forEach { privateFile ->
                        val isImage = privateFile.extension.lowercase() in listOf("jpg", "jpeg", "png")
                        val targetDir = if (isImage) artworkDir else dir
                        val dest = File(targetDir, privateFile.name)
                        if (!dest.exists()) {
                            privateFile.copyTo(dest, overwrite = true)
                        }
                        privateFile.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshDownloadedSongs: Error migrating private downloads: ${e.message}")
            }

            val audioFiles = dir.listFiles()?.filter {
                val ext = it.extension.lowercase()
                (ext == "m4a" || ext == "mp3" || ext == "webm") && it.canRead()
            } ?: emptyList()

            Log.d(TAG, "refreshDownloadedSongs: found ${audioFiles.size} audio files in '${dir.absolutePath}'")

            val dbSongs    = downloadedSongDao.getAllDownloadedSongsList()
            val dbByPath   = dbSongs.associateBy { it.filePath }

            // 2. Remove DB entries for files that no longer exist or are not strictly in downloadDir
            dbSongs.filter { !File(it.filePath).exists() || !it.filePath.startsWith(allowedPrefix) }.forEach { song ->
                downloadedSongDao.deleteDownloadedSong(song)
                song.imageUrl?.removePrefix("file://")?.let { path ->
                    val art = File(path)
                    if (art.exists() && (art.absolutePath.startsWith(allowedPrefix) || art.absolutePath.contains("cache"))) {
                        art.delete()
                    }
                }
            }

            // 2. Fix any DB songs missing valid artwork (but file still exists)
            for (dbSong in dbSongs) {
                val audioFile = File(dbSong.filePath)
                if (!audioFile.exists()) continue

                val currentArtPath = dbSong.imageUrl?.removePrefix("file://")
                val artValid = !currentArtPath.isNullOrBlank() &&
                        File(currentArtPath).let { it.exists() && it.length() > 0 }
                if (artValid) continue

                val artFile = getArtworkFile(dbSong.id, audioFile.nameWithoutExtension)
                if (artFile != null) {
                    downloadedSongDao.insertDownloadedSong(
                        dbSong.copy(imageUrl = artFile.absolutePath)
                    )
                    Log.d(TAG, "refreshDownloadedSongs: healed artwork for '${dbSong.name}'")
                }
            }

            // 3. Add new files not yet in DB
            val newFiles = audioFiles.filter { !dbByPath.containsKey(it.absolutePath) }
            if (newFiles.isEmpty()) {
                Log.d(TAG, "refreshDownloadedSongs: fully synced, nothing new")
                return@withContext
            }

            Log.d(TAG, "refreshDownloadedSongs: adding ${newFiles.size} new files")
            val batch = mutableListOf<DownloadedSong>()

            for (file in newFiles) {
                var name     = file.nameWithoutExtension
                var artist   = "Unknown Artist"
                var album: String? = null
                var duration: Int? = null
                var embeddedArtBytes: ByteArray? = null

                // Extract metadata from the audio file itself
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val metaTitle  = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val metaAlbum  = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val durStr     = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    embeddedArtBytes = retriever.embeddedPicture

                    // Fall back to filename parsing (e.g. "Title - Artist")
                    val parts      = file.nameWithoutExtension.split(" - ", limit = 2)
                    val fileTitle  = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                    val fileArtist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"

                    name   = if (!metaTitle.isNullOrBlank()) metaTitle else fileTitle
                    artist = if (!metaArtist.isNullOrBlank()) metaArtist else fileArtist
                    album  = metaAlbum
                    duration = durStr?.toIntOrNull()?.let { it / 1000 }
                } catch (e: Exception) {
                    Log.w(TAG, "refreshDownloadedSongs: MediaMetadataRetriever failed for '${file.name}': ${e.message}")
                    val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                    if (parts.size > 1) { name = parts[0].trim(); artist = parts[1].trim() }
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                // Look for companion artwork first, then embedded artwork
                val artFile = getArtworkFile(null, file.nameWithoutExtension)
                var imageUrl = artFile?.absolutePath
                if (imageUrl == null && embeddedArtBytes != null) {
                    try {
                        val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
                        if (!cacheFile.exists() || cacheFile.length() != embeddedArtBytes.size.toLong()) {
                            cacheFile.writeBytes(embeddedArtBytes)
                        }
                        imageUrl = cacheFile.absolutePath
                    } catch (_: Exception) {}
                }

                val syntheticId = file.absolutePath.hashCode().toString()

                batch.add(DownloadedSong(
                    id       = syntheticId,
                    name     = name,
                    artist   = artist,
                    album    = album,
                    duration = duration,
                    filePath = file.absolutePath,
                    imageUrl = imageUrl,
                    fileSize = file.length()
                ))

                if (batch.size >= 5) {
                    downloadedSongDao.insertDownloadedSongs(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                downloadedSongDao.insertDownloadedSongs(batch.toList())
            }
        } finally {
            _isRefreshing.value = false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────────

    suspend fun deleteSong(song: DownloadedSong): Boolean = withContext(Dispatchers.IO) {
        val file = File(song.filePath)
        val allowedPrefix = downloadDir.absolutePath.trimEnd(File.separatorChar) + File.separator
        if (!file.absolutePath.startsWith(allowedPrefix)) {
            Log.w(TAG, "deleteSong: Refusing to delete file outside downloadDir: ${file.absolutePath}")
            downloadedSongDao.deleteDownloadedSong(song)
            return@withContext true
        }
        Log.d(TAG, "deleteSong: '${file.absolutePath}'")
        val deleted = file.delete()

        if (deleted || !file.exists()) {
            // Remove the artwork .jpg in artworkDir
            try { File(artworkDir, "${file.nameWithoutExtension}.jpg").delete() } catch (_: Exception) {}
            try { File(downloadDir, "${file.nameWithoutExtension}.jpg").delete() } catch (_: Exception) {}
            // Also remove by song ID in case it was saved that way
            val sanitizedId = song.id.replace(SANITIZE_REGEX, "").trim()
            if (sanitizedId.isNotEmpty()) {
                try { File(artworkDir, "$sanitizedId.jpg").delete() } catch (_: Exception) {}
                try { File(downloadDir, "$sanitizedId.jpg").delete() } catch (_: Exception) {}
            }
            try { File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg").delete() } catch (_: Exception) {}
            downloadedSongDao.deleteDownloadedSong(song)
        }
        deleted || !file.exists()
    }

    // ──────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "DownloadRepository"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9\\s]")
    }
}
