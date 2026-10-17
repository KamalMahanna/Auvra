package com.auvra.app.data.repository

import android.util.Log
import com.auvra.app.data.model.*
import com.auvra.app.data.model.Album
import com.auvra.app.utils.resize
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor() {

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
    private var cachedHomeFeed: HomeFeed? = null
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
    fun getCachedHomeFeed(): HomeFeed? {
        val current = cachedHomeFeed
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
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            result.map { searchResult ->
                val songs = searchResult.items.filterIsInstance<SongItem>().map { it.toAuvraSong() }
                val searchSongResult = SearchSongResult(total = songs.size, results = songs).clean()
                searchCache[key] = searchSongResult
                searchSongResult
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
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST)
            result.map { searchResult ->
                val artists = searchResult.items.filterIsInstance<ArtistItem>().map { it.toAuvraSearchArtist() }
                val searchArtistResult = SearchArtistResult(total = artists.size, results = artists).clean()
                searchCache[key] = searchArtistResult
                searchArtistResult
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
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM)
            result.map { searchResult ->
                val albums = searchResult.items.filterIsInstance<AlbumItem>().map { it.toAuvraAlbum() }
                val searchAlbumResult = SearchAlbumResult(total = albums.size, results = albums).clean()
                searchCache[key] = searchAlbumResult
                searchAlbumResult
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
            val result = YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
            result.map { searchResult ->
                val playlists = searchResult.items.filterIsInstance<PlaylistItem>().map { it.toAuvraPlaylist() }
                val searchPlaylistResult = SearchPlaylistResult(total = playlists.size, results = playlists).clean()
                searchCache[key] = searchPlaylistResult
                searchPlaylistResult
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
            val nextResult = YouTube.next(WatchEndpoint(videoId = id))
            val songItem = nextResult.getOrNull()?.items?.firstOrNull { it.id == id }
                ?: nextResult.getOrNull()?.items?.firstOrNull()
            if (songItem != null) {
                val cleanSong = songItem.toAuvraSong().clean()
                songCache[id] = cleanSong
                Result.success(cleanSong)
            } else {
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
            val nextResult = YouTube.next(WatchEndpoint(videoId = songId, playlistId = "RDAMVM$songId"))
            nextResult.map { next ->
                val songs = next.items.filter { it.id != songId }.take(limit).map { it.toAuvraSong().clean() }
                suggestionCache[songId] = songs
                songs
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
            val result = YouTube.artist(id)
            result.map { page ->
                val detail = page.toAuvraArtistDetail().clean()
                artistCache[id] = detail
                detail
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
            val result = YouTube.album(id)
            result.map { page ->
                val album = page.toAuvraAlbum().clean()
                albumCache[id] = album
                album
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
            val result = YouTube.playlist(id)
            result.map { page ->
                val playlist = page.toAuvraPlaylist().clean()
                playlistCache[id] = playlist
                playlist
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylistById error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getHomeFeed(
        params: String? = null,
        continuation: String? = null,
        forceRefresh: Boolean = false
    ): Result<HomeFeed> = withContext(Dispatchers.IO) {
        if (!forceRefresh && params == null && continuation == null) {
            val cached = getCachedHomeFeed()
            if (cached != null) {
                Log.d(TAG, "getHomeFeed cache hit: returning ${cached.sections.size} cached sections")
                return@withContext Result.success(cached)
            }
        }
        Log.d(TAG, "getHomeFeed(params=$params, continuation=$continuation)")
        try {
            val result = YouTube.home(continuation = continuation, params = params)
            result.map { homePage ->
                val chips = homePage.chips?.map { chip ->
                    HomeChip(
                        title = chip.title,
                        params = chip.endpoint?.params,
                        browseId = chip.endpoint?.browseId
                    )
                } ?: emptyList()

                val sections = homePage.sections.mapIndexed { index, section ->
                    ModuleSection(
                        title = section.title,
                        subtitle = section.label,
                        thumbnail = section.thumbnail,
                        position = index,
                        data = section.items.mapNotNull { it.toAuvraModuleItem()?.clean() },
                        endpointBrowseId = section.endpoint?.browseId,
                        endpointParams = section.endpoint?.params
                    ).clean()
                }.filter { it.data.isNotEmpty() }

                val feed = HomeFeed(
                    chips = chips,
                    sections = sections,
                    continuation = homePage.continuation
                )

                if (params == null && continuation == null) {
                    cachedHomeFeed = feed
                    cachedModules = sections
                    modulesTimestamp = System.currentTimeMillis()
                }

                feed
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHomeFeed error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getModules(forceRefresh: Boolean = false): Result<List<ModuleSection>> =
        getHomeFeed(forceRefresh = forceRefresh).map { it.sections }

    suspend fun getUsTopChartSongs(): Result<List<Song>> = withContext(Dispatchers.IO) {
        val cached = suggestionCache["us_top_chart_songs"]
        if (!cached.isNullOrEmpty()) {
            return@withContext Result.success(cached)
        }
        val usTop50Id = "PLgzTt0k8mXzFvh4YO7yehTBJb13ps_LUG" // Top 50 USA
        val billboardHot100Id = "PLg48S-qywklvkpUTFRQ3MLaAbHdl_BXMY" // Billboard Hot 100
        try {
            val res = YouTube.playlist(usTop50Id)
            val songs = res.getOrNull()?.songs?.map { it.toAuvraSong().clean() }.orEmpty()
            if (songs.isNotEmpty()) {
                suggestionCache["us_top_chart_songs"] = songs
                Result.success(songs)
            } else {
                val fallbackRes = YouTube.playlist(billboardHot100Id)
                val fallbackSongs = fallbackRes.getOrNull()?.songs?.map { it.toAuvraSong().clean() }.orEmpty()
                if (fallbackSongs.isNotEmpty()) {
                    suggestionCache["us_top_chart_songs"] = fallbackSongs
                    Result.success(fallbackSongs)
                } else {
                    Result.failure(Exception("Failed to load US charts"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getUsTopChartSongs error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getNewReleases(): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val searchAlbums = YouTube.search("Latest English albums", YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                ?.items?.filterIsInstance<AlbumItem>()?.map { it.toAuvraAlbum().clean() }.orEmpty()

            val directResult = YouTube.newReleaseAlbums()
            val directAlbums = directResult.getOrNull()?.map { it.toAuvraAlbum().clean() }.orEmpty()

            val combined = (searchAlbums + directAlbums)
                .filter { album ->
                    val n = album.name.lowercase()
                    !n.contains("hindi") && !n.contains("bollywood") && !n.contains("bhajan") && !n.contains("stotra")
                }
                .distinctBy { it.id }

            if (combined.isNotEmpty()) {
                Result.success(combined)
            } else {
                val explore = YouTube.explore().getOrNull()
                val exploreAlbums = explore?.newReleaseAlbums?.map { it.toAuvraAlbum().clean() }.orEmpty()
                Result.success(exploreAlbums)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getNewReleases error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getExploreMoodAndGenres(): Result<List<MoodAndGenreItem>> = withContext(Dispatchers.IO) {
        try {
            val result = YouTube.explore()
            result.map { explorePage ->
                explorePage.moodAndGenres.map { item ->
                    MoodAndGenreItem(
                        title = item.title,
                        stripeColor = item.stripeColor,
                        browseId = item.endpoint.browseId,
                        params = item.endpoint.params
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getExploreMoodAndGenres error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getDailyDiscover(seeds: List<Song>): Result<List<DailyDiscoverItem>> = withContext(Dispatchers.IO) {
        try {
            val discoverItems = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())
            val targetSeeds = seeds.shuffled().distinctBy { it.id }.take(6)

            coroutineScope {
                targetSeeds.map { seed ->
                    launch(Dispatchers.IO) {
                        val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                        if (endpoint != null) {
                            YouTube.related(endpoint).onSuccess { page ->
                                val recommendation = page.songs
                                    .filter { it.id != seed.id && !it.explicit }
                                    .shuffled()
                                    .firstOrNull()
                                if (recommendation != null) {
                                    discoverItems.add(
                                        DailyDiscoverItem(
                                            seedTitle = seed.name,
                                            seedArtist = seed.primaryArtistNames,
                                            song = recommendation.toAuvraSong().clean()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }.forEach { it.join() }
            }
            Result.success(discoverItems.toList().distinctBy { it.song.id })
        } catch (e: Exception) {
            Log.e(TAG, "getDailyDiscover error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getCommunityPlaylists(): Result<List<CommunityPlaylistItem>> = withContext(Dispatchers.IO) {
        try {
            val candidatePlaylists = mutableListOf<PlaylistItem>()
            val searchResult = YouTube.search("Global Hits playlist", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
            searchResult.onSuccess { res ->
                val filtered = res.items.filterIsInstance<PlaylistItem>().filter { playlist ->
                    val authorName = playlist.author?.name
                    val title = playlist.title.lowercase()
                    authorName != null &&
                        authorName != "YouTube Music" &&
                        authorName != "YouTube" &&
                        authorName != "Playlist" &&
                        !playlist.id.startsWith("RD") &&
                        !playlist.id.startsWith("OLAK") &&
                        !title.contains("bollywood") &&
                        !title.contains("hindi") &&
                        !title.contains("punjabi") &&
                        !title.contains("bhojpuri") &&
                        !title.contains("bhajan")
                }
                candidatePlaylists.addAll(filtered)
            }

            if (candidatePlaylists.isEmpty()) {
                val fallbackSearch = YouTube.search("Top Hits playlist", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST)
                fallbackSearch.onSuccess { res ->
                    val filtered = res.items.filterIsInstance<PlaylistItem>().filter { playlist ->
                        val authorName = playlist.author?.name
                        val title = playlist.title.lowercase()
                        authorName != null &&
                            authorName != "YouTube Music" &&
                            authorName != "YouTube" &&
                            authorName != "Playlist" &&
                            !playlist.id.startsWith("RD") &&
                            !playlist.id.startsWith("OLAK") &&
                            !title.contains("bollywood") &&
                            !title.contains("hindi")
                    }
                    candidatePlaylists.addAll(filtered)
                }
            }

            val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(6)
            val communityItems = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

            coroutineScope {
                uniqueCandidates.map { playlistItem ->
                    launch(Dispatchers.IO) {
                        YouTube.playlist(playlistItem.id).onSuccess { page ->
                            val songs = page.songs.take(4).map { it.toAuvraSong().clean() }
                            if (songs.isNotEmpty()) {
                                communityItems.add(
                                    CommunityPlaylistItem(
                                        playlist = playlistItem.toAuvraPlaylist(songs).clean(),
                                        previewSongs = songs
                                    )
                                )
                            }
                        }
                    }
                }.forEach { it.join() }
            }
            Result.success(communityItems.toList().shuffled())
        } catch (e: Exception) {
            Log.e(TAG, "getCommunityPlaylists error: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MusicRepository"
    }
}

// ----------------- Extension Mappers -----------------

fun createThumbnailDownloadLinks(url: String?): List<DownloadLink> {
    if (url.isNullOrEmpty()) return emptyList()
    return listOf(
        DownloadLink(quality = "200x200", url = url.resize(200, 200)),
        DownloadLink(quality = "544x544", url = url.resize(544, 544)),
        DownloadLink(quality = "1080x1080", url = url.resize(1080, 1080))
    )
}

fun SongItem.toAuvraSong(): Song = Song(
    id = id,
    name = title,
    duration = duration,
    explicitContent = explicit,
    album = SongAlbum(id = album?.id, name = album?.name, url = null),
    artists = SongArtists(
        primary = artists.map { ArtistMap(id = it.id.orEmpty(), name = it.name) },
        all = artists.map { ArtistMap(id = it.id.orEmpty(), name = it.name) }
    ),
    image = createThumbnailDownloadLinks(thumbnail),
    downloadUrl = emptyList()
)

fun PlayerResponse.VideoDetails.toAuvraSong(): Song {
    val thumbUrl = thumbnail?.thumbnails?.lastOrNull()?.url
    return Song(
        id = videoId,
        name = title.orEmpty(),
        duration = lengthSeconds?.toIntOrNull(),
        album = SongAlbum(id = null, name = null, url = null),
        artists = SongArtists(
            primary = listOf(ArtistMap(name = author.orEmpty())),
            all = listOf(ArtistMap(name = author.orEmpty()))
        ),
        image = createThumbnailDownloadLinks(thumbUrl),
        downloadUrl = emptyList()
    )
}

fun AlbumItem.toAuvraAlbum(songs: List<Song>? = null): Album = Album(
    id = browseId,
    name = title,
    year = year,
    explicitContent = explicit,
    artists = SongArtists(
        primary = artists?.map { ArtistMap(id = it.id.orEmpty(), name = it.name) } ?: emptyList()
    ),
    image = createThumbnailDownloadLinks(thumbnail),
    songs = songs
)

fun PlaylistItem.toAuvraPlaylist(songs: List<Song>? = null): Playlist = Playlist(
    id = id,
    name = title,
    description = description,
    image = createThumbnailDownloadLinks(thumbnail),
    songs = songs
)

fun ArtistItem.toAuvraSearchArtist(): SearchArtist = SearchArtist(
    id = id,
    name = title,
    image = createThumbnailDownloadLinks(thumbnail)
)

fun AlbumPage.toAuvraAlbum(): Album = Album(
    id = album.browseId,
    name = album.title,
    year = album.year,
    explicitContent = album.explicit,
    artists = SongArtists(
        primary = album.artists?.map { ArtistMap(id = it.id.orEmpty(), name = it.name) } ?: emptyList()
    ),
    image = createThumbnailDownloadLinks(album.thumbnail),
    songs = songs.map { it.toAuvraSong() }
)

fun PlaylistPage.toAuvraPlaylist(): Playlist = Playlist(
    id = playlist.id,
    name = playlist.title,
    description = playlist.description,
    image = createThumbnailDownloadLinks(playlist.thumbnail),
    songs = songs.map { it.toAuvraSong() }
)

fun ArtistPage.toAuvraArtistDetail(): ArtistDetail {
    val allSongs = mutableListOf<Song>()
    val allAlbums = mutableListOf<Album>()
    sections.forEach { section ->
        section.items.forEach { item ->
            when (item) {
                is SongItem -> allSongs.add(item.toAuvraSong())
                is AlbumItem -> allAlbums.add(item.toAuvraAlbum())
                else -> {}
            }
        }
    }
    return ArtistDetail(
        id = artist.id,
        name = artist.title,
        image = createThumbnailDownloadLinks(artist.thumbnail),
        topSongs = allSongs.ifEmpty { null },
        topAlbums = allAlbums.ifEmpty { null },
        followerCount = subscriberCountText?.replace("[^0-9]".toRegex(), "")?.toIntOrNull()
    )
}

fun YTItem.toAuvraModuleItem(): ModuleItem? = when (this) {
    is SongItem -> ModuleItem(
        id = id,
        name = title,
        subtitle = artists.joinToString(", ") { it.name },
        type = "song",
        image = createThumbnailDownloadLinks(thumbnail),
        explicitContent = explicit,
        duration = duration,
        endpointBrowseId = null,
        endpointParams = endpoint?.playlistId
    )
    is AlbumItem -> ModuleItem(
        id = browseId.ifEmpty { id },
        name = title,
        subtitle = artists?.joinToString(", ") { it.name } ?: year?.toString(),
        type = "album",
        image = createThumbnailDownloadLinks(thumbnail),
        explicitContent = explicit,
        endpointBrowseId = browseId.ifEmpty { id }
    )
    is PlaylistItem -> ModuleItem(
        id = id,
        name = title,
        subtitle = listOfNotNull(author?.name, songCountText).joinToString(" • ").ifEmpty { null },
        type = "playlist",
        image = createThumbnailDownloadLinks(thumbnail),
        explicitContent = explicit,
        endpointBrowseId = id
    )
    is ArtistItem -> ModuleItem(
        id = id,
        name = title,
        subtitle = "Artist",
        type = "artist",
        image = createThumbnailDownloadLinks(thumbnail),
        explicitContent = explicit,
        endpointBrowseId = id
    )
    else -> null
}
