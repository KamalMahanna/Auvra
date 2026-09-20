package com.mymusic.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.repository.DownloadRepository
import com.mymusic.app.download.SongDownloader
import com.mymusic.app.player.MusicPlayerManager
import com.mymusic.app.player.QueueManager
import com.mymusic.app.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicPlayerManager: MusicPlayerManager,
    private val songDownloader: SongDownloader,
    private val downloadRepository: DownloadRepository,
    private val queueManager: QueueManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline = networkMonitor.isOnline
    val playbackState = musicPlayerManager.playbackState
    val downloadStates = songDownloader.downloadStates
    val queue = queueManager.queue
    val queueIndex = queueManager.currentIndex
    val downloadedSongs = downloadRepository.downloadedSongs
    val isShuffleEnabled = queueManager.isShuffleEnabled

    val currentSong: StateFlow<Song?> = musicPlayerManager.playbackState
        .map { it.currentSong }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentSongId: StateFlow<String?> = musicPlayerManager.playbackState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isPlaying: StateFlow<Boolean> = musicPlayerManager.playbackState
        .map { it.isPlaying }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val isBuffering: StateFlow<Boolean> = musicPlayerManager.playbackState
        .map { it.isBuffering }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun playSongFromList(songs: List<Song>, index: Int) {
        musicPlayerManager.playSongFromQueue(songs, index)
    }

    fun playSongWithRecommendations(song: Song) {
        musicPlayerManager.playSongWithRecommendations(song)
    }


    fun playNext() {
        viewModelScope.launch {
            musicPlayerManager.playNext()
        }
    }

    fun playPrevious() {
        musicPlayerManager.playPrevious()
    }

    fun togglePlayPause() {
        musicPlayerManager.togglePlayPause()
    }

    fun seekTo(position: Long) {
        musicPlayerManager.seekTo(position)
    }

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            songDownloader.downloadSong(song)
        }
    }

    fun isSongDownloaded(song: Song): Boolean {
        return downloadRepository.isSongDownloaded(song) || (songDownloader.downloadStates.value[song.id]?.isComplete == true)
    }

    fun playQueueIndex(index: Int) {
        musicPlayerManager.jumpToQueueIndex(index)
    }

    fun removeFromQueue(index: Int) {
        queueManager.removeAt(index)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }
}
