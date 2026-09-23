package com.auvra.app.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auvra.app.data.model.Album
import com.auvra.app.data.model.ArtistDetail
import com.auvra.app.data.model.ModuleItem
import com.auvra.app.data.model.ModuleSection
import com.auvra.app.data.model.Playlist
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.MusicRepository
import com.auvra.app.player.MusicPlayerManager
import com.auvra.app.utils.SongDeduplicator
import com.auvra.app.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val sections: List<ModuleSection> = emptyList(),
    val error: String? = null,
    val selectedPlaylist: Playlist? = null,
    val selectedAlbum: Album? = null,
    val selectedArtistDetail: ArtistDetail? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val musicPlayerManager: MusicPlayerManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadModules()
    }

    fun retry() {
        loadModules()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun loadModules() {
        val cached = musicRepository.getCachedModules()
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                sections = cached.sortedBy { it.position },
                error = null
            )
        } else {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        }
        viewModelScope.launch {
            val result = musicRepository.getModules()
            result.onSuccess { sections ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sections = sections.sortedBy { it.position }
                )
            }.onFailure { e ->
                if (_uiState.value.sections.isEmpty()) {
                    val errorMsg = if (!networkMonitor.isOnlineNow() || NetworkMonitor.isNetworkError(e)) {
                        "No internet connection"
                    } else {
                        e.message ?: "Failed to load music"
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    private fun sanitizeError(e: Throwable, fallback: String): String {
        return if (!networkMonitor.isOnlineNow() || NetworkMonitor.isNetworkError(e)) {
            "No internet connection. Connect to stream music."
        } else {
            e.message ?: fallback
        }
    }

    fun playModuleItem(item: ModuleItem) {
        Log.d(TAG, "playModuleItem: id='${item.id}', type='${item.type}', name='${item.name}'")
        if (!networkMonitor.isOnlineNow()) {
            _uiState.value = _uiState.value.copy(error = "No internet connection. Connect to stream music.")
            return
        }
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
                            _uiState.value = _uiState.value.copy(error = sanitizeError(e, "Failed to load song"))
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
                                    error = sanitizeError(e, "Failed to load playlist")
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
                                    error = sanitizeError(e, "Failed to load album")
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
                                    error = sanitizeError(e, "Failed to load artist")
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
