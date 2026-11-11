package com.auvra.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auvra.app.data.innertube.InnertubeHomeParser
import com.auvra.app.data.model.Album
import com.auvra.app.data.model.ArtistDetail
import com.auvra.app.data.model.HomeShelf
import com.auvra.app.data.model.Playlist
import com.auvra.app.data.model.ShelfItem
import com.auvra.app.data.model.ShelfLayout
import com.auvra.app.data.model.Song
import com.auvra.app.data.model.UiState
import com.auvra.app.data.model.withoutRepeatsOf
import com.auvra.app.data.repository.DownloadRepository
import com.auvra.app.data.repository.MusicRepository
import com.auvra.app.data.repository.PlaybackHistoryRepository
import com.auvra.app.player.MusicPlayerManager
import com.auvra.app.utils.SongDeduplicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val downloadRepository: DownloadRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository
) : ViewModel() {

    private val _home = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val home: StateFlow<UiState<List<HomeShelf>>> = _home.asStateFlow()

    private val _homeLoadingMore = MutableStateFlow(false)
    val homeLoadingMore: StateFlow<Boolean> = _homeLoadingMore.asStateFlow()

    private val _homeRecentlyPlayedLoading = MutableStateFlow(false)
    val homeRecentlyPlayedLoading: StateFlow<Boolean> = _homeRecentlyPlayedLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _selectedAlbum = MutableStateFlow<Album?>(null)
    val selectedAlbum: StateFlow<Album?> = _selectedAlbum.asStateFlow()

    private val _selectedArtistDetail = MutableStateFlow<ArtistDetail?>(null)
    val selectedArtistDetail: StateFlow<ArtistDetail?> = _selectedArtistDetail.asStateFlow()

    private var homeContinuation: String? = null
    private val homeSeenTitles = Collections.synchronizedSet(HashSet<String>())
    private val homeLoadGeneration = AtomicLong(0L)

    init {
        loadHome()
    }

    fun loadHome() {
        val generation = homeLoadGeneration.incrementAndGet()
        _home.value = UiState.Loading
        homeContinuation = null
        homeSeenTitles.clear()
        _homeLoadingMore.value = false
        _homeRecentlyPlayedLoading.value = true

        viewModelScope.launch {
            // 1. Dedicated Recent history from local Room database
            launch {
                try {
                    val recentSongs = try {
                        playbackHistoryRepository.recentlyPlayedSongs.first()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (isCurrentHomeLoad(generation)) {
                        _homeRecentlyPlayedLoading.value = false
                        if (recentSongs.isNotEmpty()) {
                            val shelfItems = recentSongs.take(20).map { song ->
                                ShelfItem(
                                    title = song.name,
                                    subtitle = song.primaryArtistNames,
                                    thumbnailUrl = song.mediumQualityImageUrl ?: song.highQualityImageUrl,
                                    videoId = song.id,
                                    browseId = null,
                                )
                            }
                            val recentsShelf = HomeShelf(
                                title = "Recents",
                                items = shelfItems,
                                layout = ShelfLayout.LIST,
                            )
                            publishHomeShelves(listOf(recentsShelf), prepend = true)
                        }
                    }
                } catch (e: Exception) {
                    if (isCurrentHomeLoad(generation)) _homeRecentlyPlayedLoading.value = false
                }
            }

            // 2. Core personalized YouTube Music feed (FEmusic_home)
            launch {
                musicRepository.getHomeShelves()
                    .onSuccess { feed ->
                        if (!isCurrentHomeLoad(generation)) return@onSuccess
                        homeContinuation = feed.continuation
                        publishHomeShelves(feed.shelves)
                    }
                    .onFailure { failure ->
                        if (isCurrentHomeLoad(generation) && _home.value !is UiState.Success) {
                            _home.value = UiState.Error(failure.message ?: "Failed to load home feed")
                        }
                    }
            }

            // 3. Parallel supplements: FEmusic_new_releases, FEmusic_explore
            HOME_SUPPLEMENT_BROWSE_IDS.forEach { browseId ->
                launch {
                    musicRepository.getHomeSupplementShelves(browseId).onSuccess { shelves ->
                        if (isCurrentHomeLoad(generation)) {
                            publishHomeShelves(shelves)
                        }
                    }
                }
            }
        }
    }

    private fun isCurrentHomeLoad(generation: Long) = generation == homeLoadGeneration.get()

    private fun publishHomeShelves(shelves: List<HomeShelf>, prepend: Boolean = false) {
        val existing = (_home.value as? UiState.Success)?.data.orEmpty()
        if (prepend) {
            val replacing = shelves.map { it.title.lowercase(Locale.ROOT) }.toSet()
            homeSeenTitles.addAll(replacing)
            _home.value = UiState.Success(shelves + existing.filterNot {
                it.title.lowercase(Locale.ROOT) in replacing
            })
            return
        }
        val added = shelves.withoutRepeatsOf(existing)
            .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
        if (added.isNotEmpty()) {
            _home.value = UiState.Success(existing + added)
        }
    }

    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (_homeLoadingMore.value) return
        _homeLoadingMore.value = true

        viewModelScope.launch {
            musicRepository.getMoreHomeShelves(token).onSuccess { feed ->
                val existing = (_home.value as? UiState.Success)?.data ?: emptyList()
                val added = feed.shelves.withoutRepeatsOf(existing)
                    .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                if (added.isNotEmpty()) {
                    _home.value = UiState.Success(existing + added)
                }
            }
            _homeLoadingMore.value = false
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    val recentDeferred = async {
                        try { playbackHistoryRepository.recentlyPlayedSongs.first() } catch (e: Exception) { emptyList() }
                    }
                    val homeDeferred = async {
                        musicRepository.getHomeShelves()
                    }

                    homeDeferred.await().onSuccess { feed ->
                        homeContinuation = feed.continuation
                        homeSeenTitles.clear()
                        val shelves = feed.shelves.withoutRepeatsOf(emptyList())
                            .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                        if (shelves.isNotEmpty()) {
                            _home.value = UiState.Success(shelves)
                        }
                    }

                    val recentSongs = recentDeferred.await()
                    if (recentSongs.isNotEmpty()) {
                        val shelfItems = recentSongs.take(20).map { song ->
                            ShelfItem(
                                title = song.name,
                                subtitle = song.primaryArtistNames,
                                thumbnailUrl = song.mediumQualityImageUrl ?: song.highQualityImageUrl,
                                videoId = song.id,
                                browseId = null,
                            )
                        }
                        val recentsShelf = HomeShelf(
                            title = "Recents",
                            items = shelfItems,
                            layout = ShelfLayout.LIST,
                        )
                        publishHomeShelves(listOf(recentsShelf), prepend = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing home", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun onItemClick(item: ShelfItem, shelfTitle: String, onPlay: () -> Unit) {
        val videoId = item.videoId
        val browseId = item.browseId
        when {
            videoId != null -> {
                val song = Song(
                    id = videoId,
                    name = item.title,
                    artists = com.auvra.app.data.model.SongArtists(
                        primary = listOf(com.auvra.app.data.model.ArtistMap(name = InnertubeHomeParser.artistFromSubtitle(item.subtitle)))
                    ),
                    image = if (item.thumbnailUrl != null) listOf(com.auvra.app.data.model.DownloadLink("544x544", item.thumbnailUrl)) else emptyList(),
                )
                musicPlayerManager.playSongWithRecommendations(song)
                onPlay()
            }
            browseId != null -> {
                openDetail(browseId, item.title, item.thumbnailUrl)
            }
        }
    }

    fun openDetail(browseId: String, title: String, thumbnailUrl: String?) {
        viewModelScope.launch {
            val imageList = if (thumbnailUrl != null) listOf(com.auvra.app.data.model.DownloadLink("544x544", thumbnailUrl)) else emptyList()
            when {
                browseId.startsWith("MPRE") || browseId.startsWith("album") -> {
                    val cached = musicRepository.getCachedAlbum(browseId)
                    _selectedAlbum.value = cached ?: Album(
                        id = browseId,
                        name = title,
                        image = imageList,
                        songs = null
                    )
                    musicRepository.getAlbumById(browseId).onSuccess { album ->
                        if (_selectedAlbum.value?.id == browseId) {
                            _selectedAlbum.value = album
                        }
                    }
                }
                browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("playlist") -> {
                    val cleanPlaylistId = browseId.removePrefix("VL")
                    val cached = musicRepository.getCachedPlaylist(cleanPlaylistId)
                    _selectedPlaylist.value = cached ?: Playlist(
                        id = cleanPlaylistId,
                        name = title,
                        image = imageList,
                        songs = null
                    )
                    musicRepository.getPlaylistById(cleanPlaylistId).onSuccess { playlist ->
                        if (_selectedPlaylist.value?.id == cleanPlaylistId) {
                            _selectedPlaylist.value = playlist
                        }
                    }
                }
                browseId.startsWith("UC") || browseId.startsWith("artist") -> {
                    val cached = musicRepository.getCachedArtist(browseId)
                    _selectedArtistDetail.value = cached ?: ArtistDetail(
                        id = browseId,
                        name = title,
                        image = imageList,
                        topSongs = null
                    )
                    musicRepository.getArtistById(browseId).onSuccess { detail ->
                        if (_selectedArtistDetail.value?.id == browseId) {
                            val cleanSongs = detail.topSongs?.let(SongDeduplicator::deduplicate)
                            _selectedArtistDetail.value = if (cleanSongs != null) detail.copy(topSongs = cleanSongs) else detail
                        }
                    }
                }
                else -> {
                    // Fallback to playlist / album inspection
                    val result = musicRepository.getPlaylistById(browseId)
                    result.onSuccess { playlist ->
                        _selectedPlaylist.value = playlist
                    }.onFailure {
                        musicRepository.getAlbumById(browseId).onSuccess { album ->
                            _selectedAlbum.value = album
                        }
                    }
                }
            }
        }
    }

    fun clearSelectedPlaylist() {
        _selectedPlaylist.value = null
    }

    fun clearSelectedAlbum() {
        _selectedAlbum.value = null
    }

    fun clearSelectedArtist() {
        _selectedArtistDetail.value = null
    }

    companion object {
        private const val TAG = "HomeViewModel"
        val HOME_SUPPLEMENT_BROWSE_IDS = listOf(
            "FEmusic_new_releases",
            "FEmusic_explore"
        )
    }
}
