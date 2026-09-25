package com.auvra.app.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadedSongDao: DownloadedSongDao
) {
    private val _downloadedSongs = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloadedSongs: Flow<List<DownloadedSong>> = _downloadedSongs.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @Volatile
    private var downloadedFileNames = emptySet<String>()

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun formatArtistNames(rawArtist: String): String {
        if (rawArtist.isBlank()) return rawArtist
        return rawArtist.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    init {
        // Collect DB changes and update StateFlow and in-memory caches
        repositoryScope.launch {
            downloadedSongDao.getAllDownloadedSongs().collect { songs ->
                val sortedSongs = songs.map { song ->
                    val formattedArtist = formatArtistNames(song.artist)
                    if (formattedArtist != song.artist) song.copy(artist = formattedArtist) else song
                }.sortedByDescending { it.filePath }
                _downloadedSongs.value = sortedSongs
                downloadedFileNames = songs.map { File(it.filePath).name.lowercase() }.toSet()
                Log.d(TAG, "Database flow emitted ${songs.size} songs. In-memory cache updated.")
            }
        }
        
        // Initial scan/sync on startup
        repositoryScope.launch {
            refreshDownloadedSongs()
        }
    }

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

    fun getFileForSong(song: Song): File {
        val sanitizedArtist = song.primaryArtistNames.replace(SANITIZE_REGEX, "").trim()
        val sanitizedName = song.name.replace(SANITIZE_REGEX, "").trim()
        val fileName = "$sanitizedName - $sanitizedArtist.m4a"
        val targetFile = File(downloadDir, fileName)
        Log.d(TAG, "getFileForSong: Target file path '${targetFile.absolutePath}'")
        return targetFile
    }

    fun getReadableFileForSong(song: Song): File? {
        if (song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:"))) {
            val path = if (song.url.startsWith("file://")) song.url.substring(7) else song.url
            val directFile = File(path)
            if (directFile.exists() && directFile.canRead()) {
                return directFile
            }
        }
        val primaryFile = getFileForSong(song)
        if (primaryFile.exists() && primaryFile.canRead()) {
            return primaryFile
        }
        // Backward compatibility: check old .mp3 extension
        val sanitizedArtist = song.primaryArtistNames.replace(SANITIZE_REGEX, "").trim()
        val sanitizedName = song.name.replace(SANITIZE_REGEX, "").trim()
        val mp3File = File(downloadDir, "$sanitizedName - $sanitizedArtist.mp3")
        if (mp3File.exists() && mp3File.canRead()) {
            return mp3File
        }
        return null
    }

    fun isSongDownloaded(song: Song): Boolean {
        val names = downloadedFileNames
        if (names.isNotEmpty()) {
            val sanitizedArtist = song.primaryArtistNames.replace(SANITIZE_REGEX, "").trim()
            val sanitizedName = song.name.replace(SANITIZE_REGEX, "").trim()
            val m4aFileName = "$sanitizedName - $sanitizedArtist.m4a".lowercase()
            val mp3FileName = "$sanitizedName - $sanitizedArtist.mp3".lowercase()
            return names.contains(m4aFileName) || names.contains(mp3FileName)
        }
        
        // Fallback to disk check if scan is not done yet
        val readableFile = getReadableFileForSong(song)
        return readableFile != null
    }

    suspend fun refreshDownloadedSongs() = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        try {
            val dir = downloadDir
            Log.d(TAG, "refreshDownloadedSongs: Syncing directory '${dir.absolutePath}' with SQLite database")

            if (!dir.exists()) {
                Log.w(TAG, "refreshDownloadedSongs: directory does not exist, clearing database records")
                val dbSongs = downloadedSongDao.getAllDownloadedSongsList()
                dbSongs.forEach { downloadedSongDao.deleteDownloadedSong(it) }
                return@withContext
            }

            val files = dir.listFiles()?.filter {
                val ext = it.extension.lowercase()
                (ext == "m4a" || ext == "mp3" || ext == "mp4") && it.canRead()
            } ?: emptyList()

        Log.d(TAG, "refreshDownloadedSongs: Found ${files.size} readable audio files on disk")

        val dbSongs = downloadedSongDao.getAllDownloadedSongsList()
        val dbSongsByPath = dbSongs.associateBy { it.filePath }

        // 1. Delete DB entries for files that no longer exist on disk
        val dbSongsToDelete = dbSongs.filter { !File(it.filePath).exists() }
        if (dbSongsToDelete.isNotEmpty()) {
            Log.d(TAG, "refreshDownloadedSongs: Deleting ${dbSongsToDelete.size} missing database entries")
            dbSongsToDelete.forEach {
                downloadedSongDao.deleteDownloadedSong(it)
                if (!it.imageUrl.isNullOrEmpty()) {
                    val artworkFile = File(it.imageUrl)
                    if (artworkFile.exists() && artworkFile.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                        artworkFile.delete()
                    }
                }
            }
        }

        // 2. Scan and add files that are on disk but not in the database (new or unmigrated downloads)
        val filesToScan = files.filter { !dbSongsByPath.containsKey(it.absolutePath) }
        if (filesToScan.isNotEmpty()) {
            Log.d(TAG, "refreshDownloadedSongs: Scanning ${filesToScan.size} new/untracked files on disk progressively")
            val batch = mutableListOf<DownloadedSong>()
            for (file in filesToScan) {
                val retriever = android.media.MediaMetadataRetriever()
                var name = file.nameWithoutExtension
                var artist = "Unknown Artist"
                var album: String? = null
                var duration: Int? = null
                var imageUrl: String? = null

                try {
                    retriever.setDataSource(file.absolutePath)
                    val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    duration = durStr?.toIntOrNull()?.let { it / 1000 }

                    val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                    val fileTitle = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                    val fileArtist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"

                    name = if (!metaTitle.isNullOrBlank()) metaTitle else fileTitle
                    artist = if (!metaArtist.isNullOrBlank() && metaArtist != "Unknown Artist") metaArtist else fileArtist

                    if ((artist == "Unknown Artist" || artist.isBlank()) && parts.size > 1) {
                        name = fileTitle
                        artist = fileArtist
                    }

                    val artworkBytes = retriever.embeddedPicture
                    if (artworkBytes != null) {
                        val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
                        if (!cacheFile.exists() || cacheFile.length() != artworkBytes.size.toLong()) {
                            cacheFile.writeBytes(artworkBytes)
                        }
                        imageUrl = cacheFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "refreshDownloadedSongs: Error reading metadata for '${file.name}': ${e.message}", e)
                    val parts = file.nameWithoutExtension.split(" - ", limit = 2)
                    name = if (parts.size > 1) parts[0].trim() else file.nameWithoutExtension
                    artist = if (parts.size > 1) parts[1].trim() else "Unknown Artist"
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "refreshDownloadedSongs: Error releasing MediaMetadataRetriever", e)
                    }
                }

                val ds = DownloadedSong(
                    id = file.absolutePath.hashCode().toString(),
                    name = name,
                    artist = formatArtistNames(artist),
                    album = album,
                    duration = duration,
                    filePath = file.absolutePath,
                    imageUrl = imageUrl,
                    fileSize = file.length()
                )
                batch.add(ds)
                if (batch.size >= 3) {
                    downloadedSongDao.insertDownloadedSongs(batch.toList())
                    Log.d(TAG, "refreshDownloadedSongs: Progressive batch inserted ${batch.size} songs")
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                downloadedSongDao.insertDownloadedSongs(batch.toList())
                Log.d(TAG, "refreshDownloadedSongs: Final progressive batch inserted ${batch.size} songs")
                batch.clear()
            }
        } else {
            Log.d(TAG, "refreshDownloadedSongs: Cache is fully synchronized with disk. No scans needed.")
        }
    } finally {
        _isRefreshing.value = false
    }
    }

    fun getCachedArtworkForSong(song: Song): File? {
        song.highQualityImageUrl?.let { url ->
            if (url.startsWith("/") || url.startsWith("file:")) {
                val path = if (url.startsWith("file://")) url.substring(7) else url
                val imgFile = File(path)
                if (imgFile.exists() && imgFile.canRead()) {
                    Log.d(TAG, "getCachedArtworkForSong: Found artwork directly from highQualityImageUrl at '${imgFile.absolutePath}'")
                    return imgFile
                }
            }
        }

        val file = getReadableFileForSong(song)
        if (file == null) {
            Log.d(TAG, "getCachedArtworkForSong: Song file does not exist or is unreadable")
            return null
        }
        val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
        val cacheExists = cacheFile.exists() && cacheFile.canRead()
        Log.d(TAG, "getCachedArtworkForSong: Checking cached artwork at '${cacheFile.absolutePath}', exists=$cacheExists")
        return if (cacheExists) cacheFile else null
    }

    suspend fun deleteSong(song: DownloadedSong): Boolean = withContext(Dispatchers.IO) {
        val file = File(song.filePath)
        Log.d(TAG, "deleteSong: Deleting file '${file.absolutePath}'")
        var deleted = file.delete()
        Log.d(TAG, "deleteSong: File.delete() status: $deleted")

        // If File.delete() fails (e.g. scoped storage — file was created by a
        // previous app installation), try deleting via MediaStore ContentResolver.
        if (!deleted && file.exists()) {
            deleted = deleteViaMediaStore(file)
            Log.d(TAG, "deleteSong: MediaStore deletion status: $deleted")
        }

        if (deleted || !file.exists()) {
            // Also clean up cached artwork
            val cacheFile = File(context.cacheDir, "artwork_${file.nameWithoutExtension}.jpg")
            if (cacheFile.exists()) {
                val artworkDeleted = cacheFile.delete()
                Log.d(TAG, "deleteSong: Deleted cached artwork: $artworkDeleted")
            }
            downloadedSongDao.deleteDownloadedSong(song)
        }
        deleted || !file.exists()
    }

    /**
     * Delete a media file via the MediaStore ContentResolver.
     * This handles files not owned by the current app installation (scoped storage).
     */
    private fun deleteViaMediaStore(file: File): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            // Find the MediaStore entry for this file
            val selection = MediaStore.Audio.Media.DATA + "=?"
            val selectionArgs = arrayOf(file.absolutePath)

            // Try direct deletion first
            val rowsDeleted = contentResolver.delete(collection, selection, selectionArgs)
            Log.d(TAG, "deleteViaMediaStore: ContentResolver.delete() removed $rowsDeleted rows")
            rowsDeleted > 0
        } catch (e: SecurityException) {
            // On Android 11+, deleting files owned by others throws RecoverableSecurityException.
            // Since we have MANAGE_EXTERNAL_STORAGE or the file is in our app's contributed media,
            // this fallback should rarely be needed. Log it for debugging.
            Log.e(TAG, "deleteViaMediaStore: SecurityException — cannot delete file", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "deleteViaMediaStore: Unexpected error", e)
            false
        }
    }

    companion object {
        private const val TAG = "DownloadRepository"
        private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9\\s]")
    }
}
