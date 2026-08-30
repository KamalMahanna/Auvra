package com.mymusic.app.player

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.repository.DownloadRepository
import com.mymusic.app.widget.MusicAppWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isBuffering: Boolean = false
)

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queueManager: QueueManager,
    private val downloadRepository: DownloadRepository,
    private val streamingCacheManager: StreamingCacheManager
) {
    private var exoPlayer: ExoPlayer? = null
    private var forwardingPlayer: Player? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null
    private var cachingJob: Job? = null

    /**
     * Partial wake lock held during song transitions.
     * ExoPlayer's WAKE_MODE_NETWORK keeps the CPU awake while actively playing;
     * this covers any background edge-cases during queue synchronization.
     */
    private val transitionWakeLock: PowerManager.WakeLock by lazy {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyMusic::SongTransition").apply {
            setReferenceCounted(false)
        }
    }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        scope.launch {
            playbackState.collect { state ->
                try {
                    MusicAppWidgetProvider.updateAllWidgets(context, state)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widgets on playback state change: ${e.message}", e)
                }
            }
        }
        queueManager.onQueueAppended = { newSongs ->
            val player = exoPlayer
            if (player != null && newSongs.isNotEmpty()) {
                val newMediaItems = newSongs.map { buildMediaItem(it) }
                player.addMediaItems(newMediaItems)
                Log.d(TAG, "QueueManager onQueueAppended: Appended ${newMediaItems.size} items to ExoPlayer timeline")
            }
        }
        queueManager.onQueueReset = { newQueue, newIndex ->
            val player = exoPlayer
            if (player != null) {
                val isPlaying = player.isPlaying
                val currentPos = player.currentPosition
                if (newQueue.isEmpty()) {
                    player.clearMediaItems()
                } else {
                    val mediaItems = newQueue.map { buildMediaItem(it) }
                    val safeIndex = if (newIndex in mediaItems.indices) newIndex else 0
                    player.setMediaItems(mediaItems, safeIndex, if (safeIndex == newIndex) currentPos else 0L)
                    if (isPlaying) {
                        player.play()
                    }
                }
            }
        }
        queueManager.onQueueRemoved = { index ->
            val player = exoPlayer
            if (player != null && index in 0 until player.mediaItemCount) {
                player.removeMediaItem(index)
            }
        }
    }

    fun getPlayer(): Player {
        return getOrCreatePlayer()
    }

    private fun getOrCreatePlayer(): Player {
        val player = exoPlayer
        if (player != null) return forwardingPlayer ?: player

        val newExoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { exo ->
                Log.d(TAG, "ExoPlayer created and configured")
                exoPlayer = exo
                exo.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        Log.d(TAG, "onMediaItemTransition: mediaId=${mediaItem?.mediaId}, reason=$reason")
                        val currentMediaId = mediaItem?.mediaId ?: return

                        val currentQueue = queueManager.queue.value
                        val newIndex = currentQueue.indexOfFirst { it.id == currentMediaId }
                        if (newIndex != -1) {
                            queueManager.jumpTo(newIndex)
                            val song = currentQueue[newIndex]
                            _playbackState.value = _playbackState.value.copy(
                                currentSong = song,
                                isBuffering = false,
                                duration = exo.duration.coerceAtLeast(0)
                            )

                            // Reset saved position for the new song
                            try {
                                context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
                                    .edit().putLong("KEY_SEEK_POSITION", 0L).apply()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to reset seek position", e)
                            }

                            triggerPreCaching()

                            if (queueManager.isNearEnd()) {
                                Log.d(TAG, "Queue near end on transition, pre-loading suggestions")
                                scope.launch {
                                    try {
                                        queueManager.loadMoreSuggestions()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to load suggestions on transition: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val stateString = when (playbackState) {
                            Player.STATE_BUFFERING -> "STATE_BUFFERING"
                            Player.STATE_READY -> "STATE_READY"
                            Player.STATE_ENDED -> "STATE_ENDED"
                            Player.STATE_IDLE -> "STATE_IDLE"
                            else -> "UNKNOWN"
                        }
                        Log.d(TAG, "onPlaybackStateChanged: $stateString")

                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playbackState.value = _playbackState.value.copy(isBuffering = true)
                            }
                            Player.STATE_READY -> {
                                _playbackState.value = _playbackState.value.copy(
                                    isBuffering = false,
                                    duration = exo.duration.coerceAtLeast(0)
                                )
                                releaseTransitionWakeLock()
                            }
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Playback ended for entire timeline")
                                acquireTransitionWakeLock()
                                scope.launch {
                                    try {
                                        playNext()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to play next song after STATE_ENDED: ${e.message}", e)
                                        releaseTransitionWakeLock()
                                    }
                                }
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "ExoPlayer error (${error.errorCode}): ${error.message}", error)
                        if (exo.hasNextMediaItem()) {
                            Log.d(TAG, "Auto-skipping to next media item due to error")
                            exo.seekToNextMediaItem()
                            exo.prepare()
                            exo.play()
                        } else {
                            Log.d(TAG, "No next media item in timeline on error, invoking playNext()")
                            scope.launch {
                                try {
                                    playNext()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to playNext on player error: ${e.message}", e)
                                }
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
                        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) startProgressUpdate() else stopProgressUpdate()
                    }
                })

                // Auto-play when audio device (headphones/bluetooth) connects
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val callback = object : android.media.AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                        val deviceTypes = addedDevices?.map { it.type }?.joinToString(", ") ?: "None"
                        Log.d(TAG, "Audio devices added: $deviceTypes")
                        val hasHeadphones = addedDevices?.any { device ->
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                        } == true

                        if (hasHeadphones) {
                            Log.d(TAG, "Headphones/Bluetooth detected as added. player.mediaItemCount=${exo.mediaItemCount}, player.isPlaying=${exo.isPlaying}")
                            if (exo.mediaItemCount > 0 && !exo.isPlaying) {
                                Log.d(TAG, "Resuming playback due to audio device addition")
                                try {
                                    val serviceIntent = Intent(context, MusicService::class.java)
                                    ContextCompat.startForegroundService(context, serviceIntent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start MusicService on audio device addition: ${e.message}", e)
                                }
                                if (exo.playbackState == Player.STATE_IDLE) {
                                    exo.prepare()
                                }
                                exo.play()
                            }
                        }
                    }
                }
                audioManager.registerAudioDeviceCallback(callback, null)
                audioDeviceCallback = callback
                restoreLastPlayedSong(exo)
            }

        val newForwardingPlayer = object : ForwardingPlayer(newExoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun seekToNext() {
                scope.launch { playNext() }
            }

            override fun seekToNextMediaItem() {
                scope.launch { playNext() }
            }

            override fun seekToPrevious() {
                playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                playPrevious()
            }
        }
        forwardingPlayer = newForwardingPlayer
        return newForwardingPlayer
    }

    fun playSong(song: Song) {
        Log.d(TAG, "playSong: songId='${song.id}', name='${song.name}', artist='${song.primaryArtistNames}'")
        if (queueManager.currentSong?.id != song.id) {
            Log.d(TAG, "playSong: queueManager current song mismatch. Setting single song queue.")
            queueManager.setQueue(listOf(song), 0)
        }
        syncQueueToPlayer(queueManager.queue.value, queueManager.currentIndex.value)
    }

    fun playSongFromQueue(songs: List<Song>, index: Int) {
        Log.d(TAG, "playSongFromQueue: index=$index, size=${songs.size}")
        queueManager.setQueue(songs, index)
        syncQueueToPlayer(queueManager.queue.value, queueManager.currentIndex.value)
    }

    fun playSongWithRecommendations(song: Song) {
        Log.d(TAG, "playSongWithRecommendations: songId='${song.id}', name='${song.name}'")
        queueManager.setQueue(listOf(song), 0)
        syncQueueToPlayer(queueManager.queue.value, 0)
        scope.launch {
            try {
                queueManager.loadMoreSuggestions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load suggestions: ${e.message}", e)
            }
        }
    }

    private fun syncQueueToPlayer(songs: List<Song>, startIndex: Int, seekPosition: Long = 0L) {
        val player = getOrCreatePlayer()
        acquireTransitionWakeLock()

        try {
            Log.d(TAG, "Starting MusicService")
            val intent = Intent(context, MusicService::class.java)
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
        }

        val safeIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
        val currentSong = if (safeIndex in songs.indices) songs[safeIndex] else null

        // Show buffering state immediately so the UI responds to the tap
        _playbackState.value = PlaybackState(
            currentSong = currentSong,
            isPlaying = true,
            isBuffering = true
        )

        // Reset saved position so the new song always starts from the beginning (or seekPosition).
        try {
            context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
                .edit().putLong("KEY_SEEK_POSITION", seekPosition).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset seek position", e)
        }

        streamingCacheManager.cleanTempFiles()

        val mediaItems = songs.map { buildMediaItem(it) }
        if (mediaItems.isEmpty()) {
            _playbackState.value = PlaybackState(currentSong = currentSong, isPlaying = false, isBuffering = false)
            releaseTransitionWakeLock()
            return
        }

        player.setMediaItems(mediaItems, safeIndex, seekPosition)
        player.prepare()
        player.play()
        Log.d(TAG, "ExoPlayer: setMediaItems (size=${mediaItems.size}, startIndex=$safeIndex), prepared and play() invoked")

        triggerPreCaching()

        if (queueManager.isNearEnd()) {
            Log.d(TAG, "Queue is near the end, loading suggestions")
            scope.launch {
                try {
                    queueManager.loadMoreSuggestions()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load suggestions: ${e.message}", e)
                }
            }
        }
    }

    suspend fun playNext() {
        Log.d(TAG, "playNext() invoked")
        val player = exoPlayer
        if (player != null && player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            return
        }

        if (queueManager.isNearEnd()) {
            Log.d(TAG, "Queue near end, pre-loading suggestions")
            try {
                queueManager.loadMoreSuggestions()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load suggestions in playNext: ${e.message}", e)
            }
        }
        val nextSong = queueManager.moveToNext()
        Log.d(TAG, "playNext: nextSong='${nextSong?.name}'")
        if (nextSong != null) {
            syncQueueToPlayer(queueManager.queue.value, queueManager.currentIndex.value)
        }
    }

    fun playPrevious() {
        Log.d(TAG, "playPrevious() invoked")
        val player = exoPlayer
        if (player != null && player.hasPreviousMediaItem() && player.currentPosition < 3000) {
            player.seekToPreviousMediaItem()
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            return
        } else if (player != null && player.currentPosition >= 3000) {
            player.seekTo(0L)
            return
        }

        val prevSong = queueManager.moveToPrevious()
        Log.d(TAG, "playPrevious: prevSong='${prevSong?.name}'")
        if (prevSong != null) {
            syncQueueToPlayer(queueManager.queue.value, queueManager.currentIndex.value)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer
        if (player == null) {
            Log.w(TAG, "togglePlayPause: player is null")
            return
        }
        Log.d(TAG, "togglePlayPause: currently playing=${player.isPlaying}")
        if (player.isPlaying) {
            player.pause()
        } else {
            try {
                Log.d(TAG, "Starting MusicService on resume")
                val intent = Intent(context, MusicService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MusicService: ${e.message}", e)
            }
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: position=$position")
        exoPlayer?.seekTo(position)
        _playbackState.value = _playbackState.value.copy(currentPosition = position)
        try {
            val sharedPreferences = context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit()
                .putLong("KEY_SEEK_POSITION", position)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save seek position", e)
        }
    }

    fun jumpToQueueIndex(index: Int) {
        Log.d(TAG, "jumpToQueueIndex: index=$index")
        val player = exoPlayer
        if (player != null && index in 0 until player.mediaItemCount) {
            queueManager.jumpTo(index)
            player.seekToDefaultPosition(index)
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
        } else {
            val song = queueManager.jumpTo(index)
            if (song != null) {
                syncQueueToPlayer(queueManager.queue.value, index)
            } else {
                Log.w(TAG, "jumpToQueueIndex: no song at index $index")
            }
        }
    }

    fun setLoadingSong(song: Song) {
        Log.d(TAG, "setLoadingSong: songId='${song.id}', name='${song.name}'")
        exoPlayer?.stop()
        _playbackState.value = PlaybackState(
            currentSong = song,
            isPlaying = false,
            isBuffering = true
        )
    }

    fun clearLoadingState(songId: String) {
        Log.d(TAG, "clearLoadingState: songId='$songId'")
        if (_playbackState.value.currentSong?.id == songId && _playbackState.value.isBuffering) {
            _playbackState.value = PlaybackState(
                currentSong = null,
                isPlaying = false,
                isBuffering = false
            )
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0)
                    _playbackState.value = _playbackState.value.copy(
                        currentPosition = pos,
                        duration = player.duration.coerceAtLeast(0)
                    )
                    try {
                        val sharedPreferences = context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putLong("KEY_SEEK_POSITION", pos)
                            .apply()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save progress seek position", e)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun release() {
        Log.d(TAG, "release() invoked")
        releaseTransitionWakeLock()
        stopProgressUpdate()
        cachingJob?.cancel()
        scope.cancel()
        audioDeviceCallback?.let { callback ->
            try {
                Log.d(TAG, "Unregistering audio device callback")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.unregisterAudioDeviceCallback(callback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister audio device callback: ${e.message}", e)
            }
        }
        audioDeviceCallback = null
        exoPlayer?.release()
        exoPlayer = null
        forwardingPlayer = null
    }

    /**
     * Builds a [MediaItem] for a [Song] synchronously.
     */
    fun buildMediaItem(song: Song): MediaItem {
        val readableFile = downloadRepository.getReadableFileForSong(song)
        val cachedFile = if (readableFile == null) streamingCacheManager.getCachedFileForSong(song) else null

        val uri = when {
            song.url.isNotEmpty() && (song.url.startsWith("/") || song.url.startsWith("file:")) -> {
                val filePath = if (song.url.startsWith("file://")) song.url.substring(7) else song.url
                val customFile = File(filePath)
                if (customFile.exists() && customFile.canRead()) {
                    if (song.url.startsWith("/")) android.net.Uri.fromFile(customFile).toString()
                    else song.url
                } else {
                    when {
                        readableFile != null -> android.net.Uri.fromFile(readableFile).toString()
                        cachedFile != null -> android.net.Uri.fromFile(cachedFile).toString()
                        else -> song.highQualityDownloadUrl ?: ""
                    }
                }
            }
            readableFile != null -> android.net.Uri.fromFile(readableFile).toString()
            cachedFile != null -> android.net.Uri.fromFile(cachedFile).toString()
            else -> song.highQualityDownloadUrl ?: ""
        }

        val localArtworkFile = downloadRepository.getCachedArtworkForSong(song)
        val artworkUri = if (localArtworkFile != null) {
            android.net.Uri.fromFile(localArtworkFile)
        } else {
            song.highQualityImageUrl?.let { it.toUri() }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.name)
            .setArtist(song.primaryArtistNames)
            .setAlbumTitle(song.album.name)
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
            .build()

        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun buildMediaItemForResumption(song: Song): MediaItem {
        return buildMediaItem(song)
    }

    private fun triggerPreCaching() {
        cachingJob?.cancel()
        cachingJob = scope.launch(Dispatchers.IO) {
            val current = queueManager.currentSong
            if (current != null) {
                val readableFile = downloadRepository.getReadableFileForSong(current)
                val cachedFile = if (readableFile == null) streamingCacheManager.getCachedFileForSong(current) else null
                if (readableFile == null && cachedFile == null) {
                    streamingCacheManager.cacheSong(current)
                }
            }
            val upcoming = queueManager.nextSong
            if (upcoming != null) {
                val readableFile = downloadRepository.getReadableFileForSong(upcoming)
                val cachedFile = if (readableFile == null) streamingCacheManager.getCachedFileForSong(upcoming) else null
                if (readableFile == null && cachedFile == null) {
                    streamingCacheManager.cacheSong(upcoming)
                }
            }
        }
    }

    private fun restoreLastPlayedSong(player: Player) {
        val queue = queueManager.queue.value
        val currentIndex = queueManager.currentIndex.value
        if (queue.isNotEmpty() && currentIndex in queue.indices) {
            val lastSong = queue[currentIndex]
            val sharedPreferences = context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)
            val savedPos = sharedPreferences.getLong("KEY_SEEK_POSITION", 0L)

            _playbackState.value = PlaybackState(
                currentSong = lastSong,
                isPlaying = false,
                isBuffering = false,
                currentPosition = savedPos,
                duration = 0L
            )
            Log.d(TAG, "restoreLastPlayedSong: eagerly set playback state for '${lastSong.name}' at pos=$savedPos")

            val mediaItems = queue.map { buildMediaItem(it) }
            player.setMediaItems(mediaItems, currentIndex, savedPos)
            player.prepare()
            Log.d(TAG, "restoreLastPlayedSong: ExoPlayer prepared with ${mediaItems.size} items at index $currentIndex, pos=$savedPos")
        }
    }

    private fun acquireTransitionWakeLock() {
        try {
            if (!transitionWakeLock.isHeld) {
                transitionWakeLock.acquire(120_000L) // 120s timeout safety net
                Log.d(TAG, "Transition wake lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire transition wake lock: ${e.message}", e)
        }
    }

    private fun releaseTransitionWakeLock() {
        try {
            if (transitionWakeLock.isHeld) {
                transitionWakeLock.release()
                Log.d(TAG, "Transition wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release transition wake lock: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MusicPlayerManager"
    }
}
