package com.auvra.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auvra.app.data.model.DownloadedSong
import com.auvra.app.data.model.Song
import com.auvra.app.data.repository.DownloadRepository
import com.auvra.app.download.SongDownloader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val songDownloader: SongDownloader
) : ViewModel() {

    val downloadedSongs: StateFlow<List<DownloadedSong>> = downloadRepository.downloadedSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isUpdating: StateFlow<Boolean> = combine(
        downloadRepository.isRefreshing,
        songDownloader.downloadStates
    ) { refreshing, states ->
        refreshing || states.values.any { it.isDownloading }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            downloadRepository.refreshDownloadedSongs()
        }
    }

    fun deleteSong(song: DownloadedSong) {
        viewModelScope.launch {
            downloadRepository.deleteSong(song)
        }
    }
    
    fun getAsSongList(): List<Song> {
        return downloadedSongs.value.map { ds ->
            val artistList = ds.artist.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { com.auvra.app.data.model.ArtistMap(name = it) }

            val cleanPath = ds.imageUrl?.removePrefix("file://")
            val rawArtworkPath = if (!cleanPath.isNullOrEmpty() && java.io.File(cleanPath).exists()) {
                ds.imageUrl
            } else {
                downloadRepository.getArtworkFile(ds.id, java.io.File(ds.filePath).nameWithoutExtension)?.absolutePath
                    ?: ds.imageUrl
            }

            val artworkUri = rawArtworkPath?.let {
                if (it.startsWith("/") && !it.startsWith("file://")) "file://$it" else it
            }

            Song(
                id = ds.id,
                name = ds.name,
                url = ds.filePath, // Use file path as URL for local playback
                duration = ds.duration,
                artists = com.auvra.app.data.model.SongArtists(
                    primary = artistList
                ),
                image = if (!artworkUri.isNullOrEmpty()) {
                    listOf(com.auvra.app.data.model.DownloadLink(quality = "500x500", url = artworkUri))
                } else {
                    emptyList()
                },
                downloadUrl = emptyList() // It's already local
            )
        }
    }
}
