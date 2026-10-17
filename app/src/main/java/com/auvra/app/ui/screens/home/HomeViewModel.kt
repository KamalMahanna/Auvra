package com.auvra.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auvra.app.data.model.Album
import com.auvra.app.data.model.ArtistDetail
import com.auvra.app.data.model.CommunityPlaylistItem
import com.auvra.app.data.model.DailyDiscoverItem
import com.auvra.app.data.model.DownloadLink
import com.auvra.app.data.model.DownloadedSong
import com.auvra.app.data.model.ModuleItem
import com.auvra.app.data.model.ModuleSection
import com.auvra.app.data.model.MoodAndGenreItem
import com.auvra.app.data.model.Playlist
import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongArtists
import com.auvra.app.data.model.ArtistMap
import com.auvra.app.data.model.toSong
import com.auvra.app.data.model.toModuleItem
import com.auvra.app.data.repository.DownloadRepository
import com.auvra.app.data.repository.MusicRepository
import com.auvra.app.data.repository.PlaybackHistoryRepository
import com.auvra.app.player.MusicPlayerManager
import com.auvra.app.utils.SongDeduplicator
import com.auvra.app.utils.resize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val speedDialItems: List<ModuleItem> = emptyList(),
    val trendingSongs: List<Song> = emptyList(),
    val communityPlaylists: List<CommunityPlaylistItem> = emptyList(),
    val newReleases: List<Album> = emptyList(),
    val isRandomizing: Boolean = false,
    val error: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedAlbum: Album? = null,
    val selectedArtistDetail: ArtistDetail? = null
) {
    val quickPicks: List<Song> get() = trendingSongs
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val downloadRepository: DownloadRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var chartFallbackSongs: List<Song> = emptyList()

    init {
        loadHomeFeed()
        observePlaybackHistory()
    }

    private fun observePlaybackHistory() {
        viewModelScope.launch {
            combine(
                playbackHistoryRepository.pinnedSpeedDialItems,
                playbackHistoryRepository.recentlyPlayedModuleItems,
                playbackHistoryRepository.quickPicks,
                downloadRepository.downloadedSongs
            ) { pinned, recent, quickPicks, downloads ->
                buildFeedData(pinned, recent, quickPicks, downloads, chartFallbackSongs)
            }.collect { (speedDial, trending) ->
                if (speedDial.isNotEmpty() || trending.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        speedDialItems = speedDial,
                        trendingSongs = trending
                    )
                }
            }
        }
    }

    private fun buildFeedData(
        pinned: List<ModuleItem>,
        recent: List<ModuleItem>,
        quickPicks: List<Song>,
        downloads: List<DownloadedSong>,
        fallbackCharts: List<Song>
    ): Pair<List<ModuleItem>, List<Song>> {
        val downloadedSongs = downloads.map { it.toSong() }
        val downloadedModuleItems = downloads.map { it.toModuleItem() }
        val fallbackModuleItems = fallbackCharts.map { it.toModuleItem() }

        // Trending songs / Quick Picks: local quick picks first, then downloaded songs, padded by US charts up to 20
        val trending = (quickPicks + downloadedSongs + fallbackCharts)
            .distinctBy { it.id }
            .take(20)

        // Speed dial: pinned items first, then recently played, then quick picks, then downloads, padded by US charts up to 27
        val speedDial = (pinned + recent + quickPicks.map { it.toModuleItem() } + downloadedModuleItems + fallbackModuleItems)
            .distinctBy { it.id }
            .take(27)

        return Pair(speedDial, trending)
    }

    fun loadHomeFeed(forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            if (_uiState.value.speedDialItems.isEmpty() && _uiState.value.trendingSongs.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
        } else {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
        }

        viewModelScope.launch {
            try {
                // Step 1: Load local data immediately from Room / Download repository (instant local DB)
                val downloaded = try { downloadRepository.downloadedSongs.first() } catch (e: Exception) { emptyList() }
                val localQuickPicks = try { playbackHistoryRepository.quickPicks.first() } catch (e: Exception) { emptyList() }
                val localRecent = try { playbackHistoryRepository.recentlyPlayedModuleItems.first() } catch (e: Exception) { emptyList() }
                val pinned = try { playbackHistoryRepository.pinnedSpeedDialItems.first() } catch (e: Exception) { emptyList() }

                // If local items exist, immediately render them (instant local-first launch)
                if (localRecent.isNotEmpty() || localQuickPicks.isNotEmpty() || downloaded.isNotEmpty()) {
                    val (speedDial, trending) = buildFeedData(pinned, localRecent, localQuickPicks, downloaded, chartFallbackSongs)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        speedDialItems = speedDial,
                        trendingSongs = trending
                    )
                }

                // Step 2: Fetch US Top 50 Chart songs (curated US chart, bypasses GeoIP)
                val chartResult = musicRepository.getUsTopChartSongs()
                val chartSongs = chartResult.getOrDefault(emptyList())
                if (chartSongs.isNotEmpty()) {
                    chartFallbackSongs = chartSongs
                }

                // Step 3: Rebuild with US Chart fallback
                val (speedDial, trending) = buildFeedData(pinned, localRecent, localQuickPicks, downloaded, chartFallbackSongs)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    speedDialItems = speedDial,
                    trendingSongs = trending,
                    error = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading home feed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = if (_uiState.value.speedDialItems.isEmpty()) e.message else null
                )
            }

            // Step 4: Concurrently load Community Playlists and New Releases
            launch(Dispatchers.IO) {
                loadCommunityPlaylists()
            }
            launch(Dispatchers.IO) {
                loadNewReleases()
            }
        }
    }

    private suspend fun loadCommunityPlaylists() {
        val result = musicRepository.getCommunityPlaylists()
        result.onSuccess { playlists ->
            if (playlists.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(communityPlaylists = playlists)
            }
        }
    }

    private suspend fun loadNewReleases() {
        val result = musicRepository.getNewReleases()
        result.onSuccess { albums ->
            if (albums.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(newReleases = albums)
            }
        }
    }

    fun refresh() {
        loadHomeFeed(forceRefresh = true)
    }

    fun loadMore() {
        // Decluttered feed has fixed curated sections
    }

    fun openAlbum(album: Album) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedAlbum = album)
            val result = musicRepository.getAlbumById(album.id)
            result.onSuccess { fullAlbum ->
                if (_uiState.value.selectedAlbum?.id == album.id) {
                    _uiState.value = _uiState.value.copy(selectedAlbum = fullAlbum)
                }
            }
        }
    }

    fun getRandomItem(onPlay: () -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isRandomizing = true)
                delay(700) // Visual dice roll animation

                val allAvailableSongs = mutableListOf<Song>()
                allAvailableSongs.addAll(_uiState.value.trendingSongs)
                _uiState.value.communityPlaylists.forEach {
                    allAvailableSongs.addAll(it.previewSongs)
                }

                val randomSong = allAvailableSongs.distinctBy { it.id }.shuffled().firstOrNull()
                if (randomSong != null) {
                    playSong(randomSong)
                    onPlay()
                }
            } finally {
                _uiState.value = _uiState.value.copy(isRandomizing = false)
            }
        }
    }

    fun playSong(song: Song) {
        musicPlayerManager.playSongWithRecommendations(song)
    }

    fun playSongFromList(songs: List<Song>, index: Int) {
        if (songs.isNotEmpty()) {
            musicPlayerManager.playSongFromQueue(songs, index.coerceIn(0, songs.size - 1))
        }
    }

    fun playAllSongs(songs: List<Song>) {
        if (songs.isNotEmpty()) {
            musicPlayerManager.playSongFromQueue(songs, 0)
        }
    }

    fun playAllSongs(section: ModuleSection) {
        val songs = section.data
            .filter { it.type.lowercase().trim() == "song" }
            .map { it.toSong() }
        if (songs.isNotEmpty()) {
            musicPlayerManager.playSongFromQueue(songs, 0)
        }
    }

    fun playModuleItem(item: ModuleItem) {
        Log.d(TAG, "playModuleItem: id='${item.id}', type='${item.type}', name='${item.name}'")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                when (item.type.lowercase().trim()) {
                    "song" -> {
                        val placeholderSong = Song(
                            id = item.id,
                            name = item.name,
                            image = item.image
                        )
                        musicPlayerManager.setLoadingSong(placeholderSong)

                        val result = musicRepository.getSongById(item.id)
                        result.onSuccess { song ->
                            musicPlayerManager.playSongWithRecommendations(song)
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load song", e)
                            _uiState.value = _uiState.value.copy(error = "Failed to load song: ${e.message}")
                            musicPlayerManager.clearLoadingState(item.id)
                        }
                    }
                    "playlist", "channel" -> {
                        val cached = musicRepository.getCachedPlaylist(item.id)
                        val initialPlaylist = cached ?: Playlist(
                            id = item.id,
                            name = item.name,
                            image = item.image,
                            songs = null
                        )
                        _uiState.value = _uiState.value.copy(selectedPlaylist = initialPlaylist)

                        val result = musicRepository.getPlaylistById(item.id)
                        result.onSuccess { playlist ->
                            val current = _uiState.value.selectedPlaylist
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(selectedPlaylist = playlist)
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load playlist", e)
                            val current = _uiState.value.selectedPlaylist
                            if (current != null && current.id == item.id && current.songs == null) {
                                _uiState.value = _uiState.value.copy(
                                    selectedPlaylist = null,
                                    error = "Failed to load playlist: ${e.message}"
                                )
                            }
                        }
                    }
                    "album" -> {
                        val cached = musicRepository.getCachedAlbum(item.id)
                        val initialAlbum = cached ?: Album(
                            id = item.id,
                            name = item.name,
                            image = item.image,
                            songs = null
                        )
                        _uiState.value = _uiState.value.copy(selectedAlbum = initialAlbum)

                        val result = musicRepository.getAlbumById(item.id)
                        result.onSuccess { album ->
                            val current = _uiState.value.selectedAlbum
                            if (current != null && current.id == item.id) {
                                _uiState.value = _uiState.value.copy(selectedAlbum = album)
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load album", e)
                            val current = _uiState.value.selectedAlbum
                            if (current != null && current.id == item.id && current.songs == null) {
                                _uiState.value = _uiState.value.copy(
                                    selectedAlbum = null,
                                    error = "Failed to load album: ${e.message}"
                                )
                            }
                        }
                    }
                    "artist" -> {
                        val cached = musicRepository.getCachedArtist(item.id)
                        val initialArtist = cached ?: ArtistDetail(
                            id = item.id,
                            name = item.name,
                            image = item.image,
                            topSongs = null
                        )
                        _uiState.value = _uiState.value.copy(selectedArtistDetail = initialArtist)

                        val result = musicRepository.getArtistById(item.id)
                        result.onSuccess { detail ->
                            val current = _uiState.value.selectedArtistDetail
                            if (current != null && current.id == item.id) {
                                val deduplicatedTopSongs = SongDeduplicator.deduplicate(detail.topSongs)
                                val cleanDetail = if (detail.topSongs != null) detail.copy(topSongs = deduplicatedTopSongs) else detail
                                _uiState.value = _uiState.value.copy(selectedArtistDetail = cleanDetail)
                            }
                        }.onFailure { e ->
                            Log.e(TAG, "Failed to load artist", e)
                            val current = _uiState.value.selectedArtistDetail
                            if (current != null && current.id == item.id && current.topSongs == null) {
                                _uiState.value = _uiState.value.copy(
                                    selectedArtistDetail = null,
                                    error = "Failed to load artist: ${e.message}"
                                )
                            }
                        }
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(error = "Unsupported type: ${item.type}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in playModuleItem", e)
                _uiState.value = _uiState.value.copy(error = "An error occurred: ${e.message}")
            }
        }
    }

    fun openMoodOrGenre(item: MoodAndGenreItem) {
        viewModelScope.launch {
            if (item.params != null || item.browseId.isNotEmpty()) {
                val feedResult = musicRepository.getHomeFeed(params = item.params, forceRefresh = true)
                feedResult.onSuccess { feed ->
                    val playlistSongs = feed.sections.flatMap { it.data }.filter { it.type.lowercase().trim() == "song" }.map { it.toSong() }
                    if (playlistSongs.isNotEmpty()) {
                        val pseudoPlaylist = Playlist(
                            id = item.browseId,
                            name = item.title,
                            songs = playlistSongs
                        )
                        _uiState.value = _uiState.value.copy(selectedPlaylist = pseudoPlaylist)
                    }
                }
            }
        }
    }

    fun clearSelectedPlaylist() {
        _uiState.value = _uiState.value.copy(selectedPlaylist = null)
    }

    fun clearSelectedAlbum() {
        _uiState.value = _uiState.value.copy(selectedAlbum = null)
    }

    fun clearSelectedArtist() {
        _uiState.value = _uiState.value.copy(selectedArtistDetail = null)
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
