package com.auvra.app.data.repository

import android.util.Log
import com.auvra.app.data.api.SaavnApi
import com.auvra.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val api: SaavnApi
) {
    private suspend fun <T> runWithRetry(
        maxRetries: Int = 5,
        delayMs: Long = 1000L,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Network attempt $attempt/$maxRetries failed: ${e.message}. Retrying in ${delayMs}ms...")
                if (attempt < maxRetries) {
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: Exception("Unknown error during retry execution")
    }

    private val songCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Song>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Song>?): Boolean = size > 50
        }
    )

    private val suggestionCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, List<Song>>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Song>>?): Boolean = size > 50
        }
    )

    private val searchCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Any>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean = size > 50
        }
    )

    private val playlistCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Playlist>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Playlist>?): Boolean = size > 50
        }
    )

    private val albumCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, Album>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Album>?): Boolean = size > 50
        }
    )

    private val artistCache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ArtistDetail>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistDetail>?): Boolean = size > 50
        }
    )

    @Volatile
    private var cachedModules: List<ModuleSection>? = null
    @Volatile
    private var modulesTimestamp: Long = 0L

    fun getCachedPlaylist(id: String): Playlist? = playlistCache[id]
    fun getCachedAlbum(id: String): Album? = albumCache[id]
    fun getCachedArtist(id: String): ArtistDetail? = artistCache[id]
    fun getCachedSong(id: String): Song? = songCache[id]
    fun getCachedSuggestions(songId: String): List<Song>? = suggestionCache[songId]
    @Suppress("UNCHECKED_CAST")
    fun <T> getCachedSearchResult(key: String): T? = searchCache[key] as? T
    fun getCachedModules(): List<ModuleSection>? {
        val current = cachedModules
        return if (current != null && (System.currentTimeMillis() - modulesTimestamp < 30 * 60 * 1000L)) {
            current
        } else null
    }

    suspend fun searchSongs(query: String, page: Int = 0, limit: Int = 50, forceRefresh: Boolean = false): Result<SearchSongResult> = withContext(Dispatchers.IO) {
        val key = "songs_${query}_${page}_$limit"
        if (!forceRefresh) {
            val cached: SearchSongResult? = getCachedSearchResult(key)
            if (cached != null) {
                Log.d(TAG, "searchSongs cache hit for '$query'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "searchSongs(query='$query', page=$page, limit=$limit)")
        try {
            val response = runWithRetry { api.searchSongs(query, page, limit) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                searchCache[key] = cleanData
                Log.d(TAG, "searchSongs success: found ${cleanData.results.size} songs")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "searchSongs failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchSongs error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchArtists(query: String, page: Int = 0, limit: Int = 50, forceRefresh: Boolean = false): Result<SearchArtistResult> = withContext(Dispatchers.IO) {
        val key = "artists_${query}_${page}_$limit"
        if (!forceRefresh) {
            val cached: SearchArtistResult? = getCachedSearchResult(key)
            if (cached != null) {
                Log.d(TAG, "searchArtists cache hit for '$query'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "searchArtists(query='$query', page=$page, limit=$limit)")
        try {
            val response = runWithRetry { api.searchArtists(query, page, limit) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                searchCache[key] = cleanData
                Log.d(TAG, "searchArtists success: found ${cleanData.results.size} artists")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "searchArtists failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Artist search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchArtists error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchAlbums(query: String, page: Int = 0, limit: Int = 50, forceRefresh: Boolean = false): Result<SearchAlbumResult> = withContext(Dispatchers.IO) {
        val key = "albums_${query}_${page}_$limit"
        if (!forceRefresh) {
            val cached: SearchAlbumResult? = getCachedSearchResult(key)
            if (cached != null) {
                Log.d(TAG, "searchAlbums cache hit for '$query'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "searchAlbums(query='$query', page=$page, limit=$limit)")
        try {
            val response = runWithRetry { api.searchAlbums(query, page, limit) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                searchCache[key] = cleanData
                Log.d(TAG, "searchAlbums success: found ${cleanData.results.size} albums")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "searchAlbums failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Album search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchAlbums error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchPlaylists(query: String, page: Int = 0, limit: Int = 50, forceRefresh: Boolean = false): Result<SearchPlaylistResult> = withContext(Dispatchers.IO) {
        val key = "playlists_${query}_${page}_$limit"
        if (!forceRefresh) {
            val cached: SearchPlaylistResult? = getCachedSearchResult(key)
            if (cached != null) {
                Log.d(TAG, "searchPlaylists cache hit for '$query'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "searchPlaylists(query='$query', page=$page, limit=$limit)")
        try {
            val response = runWithRetry { api.searchPlaylists(query, page, limit) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                searchCache[key] = cleanData
                Log.d(TAG, "searchPlaylists success: found ${cleanData.results.size} playlists")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "searchPlaylists failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Playlist search failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchPlaylists error: ${e.message}", e)
            Result.failure(e)
        }
    }


    suspend fun getSongById(id: String, forceRefresh: Boolean = false): Result<Song> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = songCache[id]
            if (cached != null) {
                Log.d(TAG, "getSongById cache hit for '${cached.name}'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getSongById(id='$id')")
        try {
            val response = runWithRetry { api.getSongById(id) }
            if (response.success && response.data != null && response.data.isNotEmpty()) {
                val cleanSong = response.data.first().clean()
                songCache[id] = cleanSong
                Log.d(TAG, "getSongById success: loaded song name='${cleanSong.name}'")
                Result.success(cleanSong)
            } else {
                Log.e(TAG, "getSongById failed: success=${response.success}, empty/null data: ${response.data.isNullOrEmpty()}")
                Result.failure(Exception("Song not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSongById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSongSuggestions(songId: String, limit: Int = 20, forceRefresh: Boolean = false): Result<List<Song>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = suggestionCache[songId]
            if (cached != null) {
                Log.d(TAG, "getSongSuggestions cache hit for '$songId'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getSongSuggestions(songId='$songId', limit=$limit)")
        try {
            val response = runWithRetry { api.getSongSuggestions(songId, limit) }
            if (response.success && response.data != null) {
                val cleanSongs = response.data.map { it.clean() }
                suggestionCache[songId] = cleanSongs
                Log.d(TAG, "getSongSuggestions success: loaded ${cleanSongs.size} suggestions")
                Result.success(cleanSongs)
            } else {
                Log.e(TAG, "getSongSuggestions failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("No suggestions found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSongSuggestions error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getArtistById(id: String, forceRefresh: Boolean = false): Result<ArtistDetail> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = artistCache[id]
            if (cached != null) {
                Log.d(TAG, "getArtistById cache hit for '${cached.name}'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getArtistById(id='$id')")
        try {
            val response = runWithRetry { api.getArtistById(id) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                artistCache[id] = cleanData
                Log.d(TAG, "getArtistById success: loaded artist='${cleanData.name}'")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "getArtistById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Artist not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getArtistById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getAlbumById(id: String, forceRefresh: Boolean = false): Result<Album> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = albumCache[id]
            if (cached != null) {
                Log.d(TAG, "getAlbumById cache hit for '${cached.name}'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getAlbumById(id='$id')")
        try {
            val response = runWithRetry { api.getAlbumById(id) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                albumCache[id] = cleanData
                Log.d(TAG, "getAlbumById success: loaded album='${cleanData.name}'")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "getAlbumById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Album not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAlbumById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getPlaylistById(id: String, limit: Int = 1000, forceRefresh: Boolean = false): Result<Playlist> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = playlistCache[id]
            if (cached != null) {
                Log.d(TAG, "getPlaylistById cache hit for '${cached.name}'")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getPlaylistById(id='$id', limit=$limit)")
        try {
            val response = runWithRetry { api.getPlaylistById(id, limit = limit) }
            if (response.success && response.data != null) {
                val cleanData = response.data.clean()
                playlistCache[id] = cleanData
                Log.d(TAG, "getPlaylistById success: loaded playlist='${cleanData.name}', songs=${cleanData.songs?.size ?: 0}")
                Result.success(cleanData)
            } else {
                Log.e(TAG, "getPlaylistById failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Playlist not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getModules(forceRefresh: Boolean = false): Result<List<ModuleSection>> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = getCachedModules()
            if (cached != null) {
                Log.d(TAG, "getModules cache hit: returning ${cached.size} cached sections")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getModules()")
        try {
            val response = runWithRetry { api.getModules() }
            if (response.success && response.data != null) {
                val cleanSections = response.data.map { it.clean() }
                cachedModules = cleanSections
                modulesTimestamp = System.currentTimeMillis()
                Log.d(TAG, "getModules success: loaded ${cleanSections.size} sections")
                Result.success(cleanSections)
            } else {
                Log.e(TAG, "getModules failed: success=${response.success}, data is null: ${response.data == null}")
                Result.failure(Exception("Failed to load modules"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getModules error: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}
