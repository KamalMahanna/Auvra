package com.mymusic.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.app.data.model.DownloadedSong
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.repository.DownloadRepository
import com.mymusic.app.download.SongDownloader
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
                .map { com.mymusic.app.data.model.ArtistMap(name = it) }

            Song(
                id = ds.id,
                name = ds.name,
                url = ds.filePath, // Use file path as URL for local playback
                duration = ds.duration,
                artists = com.mymusic.app.data.model.SongArtists(
                    primary = artistList
                ),
                image = if (!ds.imageUrl.isNullOrEmpty()) {
                    listOf(com.mymusic.app.data.model.DownloadLink(quality = "500x500", url = ds.imageUrl))
                } else {
                    emptyList()
                },
                downloadUrl = emptyList() // It's already local
            )
        }
    }
}
