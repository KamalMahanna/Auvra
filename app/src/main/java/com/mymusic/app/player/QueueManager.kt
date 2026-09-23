package com.mymusic.app.player

import android.content.Context
import android.util.Log
import com.mymusic.app.data.model.Song
import com.mymusic.app.data.repository.DownloadRepository
import com.mymusic.app.data.repository.MusicRepository
import com.mymusic.app.utils.SongDeduplicator
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueManager @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val moshi: Moshi,
    @ApplicationContext private val context: Context
) {
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    var onQueueAppended: ((List<Song>) -> Unit)? = null
    var onQueueReset: ((List<Song>, Int) -> Unit)? = null
    var onQueueRemoved: ((Int) -> Unit)? = null

    private var originalQueue = emptyList<Song>()

    private val playedSongIds = mutableSetOf<String>()
    private val playedKeys = mutableSetOf<Pair<String, String>>()
    private var isLoadingSuggestions = false

    // Downloaded Music Paging & Sliding Window state
    private var fullDownloadedSourceList: List<Song> = emptyList()
    private var downloadedSourceNextIndex: Int = 0
    private var isDownloadedQueueMode: Boolean = false

    private val saveScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPreferences = context.getSharedPreferences("mymusic_playback_prefs", Context.MODE_PRIVATE)

    val currentSong: Song?
        get() {
            val idx = _currentIndex.value
            val q = _queue.value
            return if (idx in q.indices) q[idx] else null
        }

    val nextSong: Song?
        get() {
            val nextIdx = _currentIndex.value + 1
            val q = _queue.value
            return if (nextIdx in q.indices) q[nextIdx] else null
        }

    init {
        restoreState()
    }

    private fun saveState() {
        saveScope.launch {
            try {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Song::class.java)
                val json = moshi.adapter<List<Song>>(listType).toJson(_queue.value)
                val originalJson = moshi.adapter<List<Song>>(listType).toJson(originalQueue)
                sharedPreferences.edit()
                    .putString("KEY_QUEUE", json)
                    .putInt("KEY_CURRENT_INDEX", _currentIndex.value)
                    .putBoolean("KEY_SHUFFLE_ENABLED", _isShuffleEnabled.value)
                    .putString("KEY_ORIGINAL_QUEUE", originalJson)
                    .apply()
                Log.d(TAG, "saveState success: queue size=${_queue.value.size}, index=${_currentIndex.value}, shuffle=${_isShuffleEnabled.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save queue state", e)
            }
        }
    }

    private fun restoreState() {
        try {
            val json = sharedPreferences.getString("KEY_QUEUE", null)
            val index = sharedPreferences.getInt("KEY_CURRENT_INDEX", -1)
            val shuffleEnabled = sharedPreferences.getBoolean("KEY_SHUFFLE_ENABLED", false)
            val originalJson = sharedPreferences.getString("KEY_ORIGINAL_QUEUE", null)
            
            _isShuffleEnabled.value = shuffleEnabled
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Song::class.java)
            if (!originalJson.isNullOrEmpty()) {
                val restoredOriginal: List<Song>? = moshi.adapter<List<Song>>(listType).fromJson(originalJson)
                if (restoredOriginal != null) {
                    originalQueue = restoredOriginal
                }
            }

            if (!json.isNullOrEmpty()) {
                val restored: List<Song>? = moshi.adapter<List<Song>>(listType).fromJson(json)
                if (!restored.isNullOrEmpty()) {
                    _queue.value = restored
                    _currentIndex.value = index
                    playedSongIds.clear()
                    playedKeys.clear()
                    restored.forEach {
                        playedSongIds.add(it.id)
                        playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
                    }
                    Log.d(TAG, "restored state success: restored queue of size ${restored.size} at index $index, shuffle=$shuffleEnabled")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore queue state", e)
        }
    }

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        val isDownloaded = songs.isNotEmpty() && songs.any {
            it.url.startsWith("/") || it.url.startsWith("file:") || downloadRepository.isSongDownloaded(it)
        }
        if (isDownloaded) {
            setDownloadedQueue(songs, startIndex)
            return
        }

        isDownloadedQueueMode = false
        fullDownloadedSourceList = emptyList()

        Log.d(TAG, "setQueue: input size=${songs.size}, startIndex=$startIndex")
        val originalTargetSong = if (startIndex in songs.indices) songs[startIndex] else null

        val uniqueSongs = SongDeduplicator.deduplicate(songs)
        Log.d(TAG, "setQueue: after deduplication, queue size=${uniqueSongs.size}")

        originalQueue = uniqueSongs

        val finalQueue = if (_isShuffleEnabled.value && originalTargetSong != null) {
            val rest = uniqueSongs.toMutableList()
            rest.remove(originalTargetSong)
            rest.shuffle()
            listOf(originalTargetSong) + rest
        } else {
            uniqueSongs
        }

        _queue.value = finalQueue
        playedSongIds.clear()
        playedKeys.clear()
        finalQueue.forEach {
            playedSongIds.add(it.id)
            playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
        }

        val newIndex = if (originalTargetSong != null) {
            finalQueue.indexOfFirst { it.id == originalTargetSong.id }
        } else {
            -1
        }
        _currentIndex.value = if (newIndex != -1) newIndex else startIndex.coerceIn(-1, finalQueue.size - 1)
        Log.d(TAG, "setQueue: new index=${_currentIndex.value}, song='${currentSong?.name}'")
        saveState()
    }

    /**
     * Sets the queue in streaming mode, bypassing the downloaded-song detection.
     * Used by [com.mymusic.app.player.MusicPlayerManager.playSongWithRecommendations] so that
     * clicking a downloaded song outside the Library still fetches API recommendations instead
     * of switching into downloaded-queue mode.
     */
    fun setStreamingQueue(songs: List<Song>, startIndex: Int = 0) {
        isDownloadedQueueMode = false
        fullDownloadedSourceList = emptyList()

        Log.d(TAG, "setStreamingQueue: input size=${songs.size}, startIndex=$startIndex")
        val originalTargetSong = if (startIndex in songs.indices) songs[startIndex] else null

        val uniqueSongs = SongDeduplicator.deduplicate(songs)
        Log.d(TAG, "setStreamingQueue: after deduplication, queue size=${uniqueSongs.size}")

        originalQueue = uniqueSongs

        val finalQueue = if (_isShuffleEnabled.value && originalTargetSong != null) {
            val rest = uniqueSongs.toMutableList()
            rest.remove(originalTargetSong)
            rest.shuffle()
            listOf(originalTargetSong) + rest
        } else {
            uniqueSongs
        }

        _queue.value = finalQueue
        playedSongIds.clear()
        playedKeys.clear()
        finalQueue.forEach {
            playedSongIds.add(it.id)
            playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
        }

        val newIndex = if (originalTargetSong != null) {
            finalQueue.indexOfFirst { it.id == originalTargetSong.id }
        } else {
            -1
        }
        _currentIndex.value = if (newIndex != -1) newIndex else startIndex.coerceIn(-1, finalQueue.size - 1)
        Log.d(TAG, "setStreamingQueue: new index=${_currentIndex.value}, song='${currentSong?.name}'")
        saveState()
    }

    fun setDownloadedQueue(songs: List<Song>, startIndex: Int = 0) {
        Log.d(TAG, "setDownloadedQueue: total downloaded songs=${songs.size}, startIndex=$startIndex, shuffle=${_isShuffleEnabled.value}")
        if (songs.isEmpty()) return

        isDownloadedQueueMode = true
        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val targetSong = songs[safeIndex]

        val orderedSource = if (_isShuffleEnabled.value) {
            val rest = songs.filter { it.id != targetSong.id }.shuffled()
            listOf(targetSong) + rest
        } else {
            val subAfter = songs.subList(safeIndex, songs.size)
            val subBefore = songs.subList(0, safeIndex)
            subAfter + subBefore
        }

        fullDownloadedSourceList = orderedSource

        // Initial queue batch: clicked song + next 30 downloaded songs (total max 31)
        val initialBatch = orderedSource.take(31)
        downloadedSourceNextIndex = if (orderedSource.size > 31) 31 else orderedSource.size

        originalQueue = orderedSource
        _queue.value = initialBatch
        _currentIndex.value = 0

        playedSongIds.clear()
        playedKeys.clear()
        initialBatch.forEach {
            playedSongIds.add(it.id)
            playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
        }

        Log.d(TAG, "setDownloadedQueue initialized: active queue size=${initialBatch.size}, targetSong='${targetSong.name}', nextIndex=$downloadedSourceNextIndex")
        saveState()
    }

    fun addToQueue(songs: List<Song>) {
        val newSongs = songs.filter { song ->
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            !playedSongIds.contains(song.id) && !playedKeys.contains(key)
        }
        Log.d(TAG, "addToQueue (bulk): filtered ${songs.size} down to ${newSongs.size} new unique songs")
        if (newSongs.isNotEmpty()) {
            val uniqueIncoming = mutableListOf<Song>()
            val incomingKeys = mutableSetOf<Pair<String, String>>()
            newSongs.forEach { song ->
                val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
                if (incomingKeys.add(key)) {
                    uniqueIncoming.add(song)
                }
            }
            
            var updatedQueue = _queue.value + uniqueIncoming
            var updatedIndex = _currentIndex.value

            uniqueIncoming.forEach {
                playedSongIds.add(it.id)
                playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
            }

            // Cap total queue size at max 60 items by trimming older played items
            val maxQueueSize = 60
            if (updatedQueue.size > maxQueueSize && updatedIndex > 0) {
                val excess = updatedQueue.size - maxQueueSize
                val trimCount = excess.coerceAtMost(updatedIndex)
                if (trimCount > 0) {
                    val evicted = updatedQueue.take(trimCount)
                    updatedQueue = updatedQueue.drop(trimCount)
                    updatedIndex -= trimCount
                    evicted.forEach {
                        playedSongIds.remove(it.id)
                        playedKeys.remove(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
                    }
                }
            }

            _queue.value = updatedQueue
            _currentIndex.value = updatedIndex
            originalQueue = if (_isShuffleEnabled.value) {
                originalQueue + uniqueIncoming
            } else {
                _queue.value
            }
            Log.d(TAG, "addToQueue (bulk): new total queue size=${_queue.value.size}")
            saveState()
            onQueueAppended?.invoke(uniqueIncoming)
        }
    }

    fun moveToNext(): Song? {
        val nextIndex = _currentIndex.value + 1
        Log.d(TAG, "moveToNext: target index=$nextIndex, queue size=${_queue.value.size}")
        if (isNearEnd()) {
            if (isDownloadedQueueMode) {
                loadMoreDownloadedSongs()
            }
        }
        val idx = _currentIndex.value + 1
        return if (idx < _queue.value.size) {
            _currentIndex.value = idx
            Log.d(TAG, "moveToNext: moved to '${_queue.value[idx].name}'")
            saveState()
            _queue.value[idx]
        } else {
            Log.d(TAG, "moveToNext: already at end of queue")
            null
        }
    }

    fun moveToPrevious(): Song? {
        val prevIndex = _currentIndex.value - 1
        Log.d(TAG, "moveToPrevious: target index=$prevIndex")
        return if (prevIndex >= 0) {
            _currentIndex.value = prevIndex
            Log.d(TAG, "moveToPrevious: moved to '${_queue.value[prevIndex].name}'")
            saveState()
            _queue.value[prevIndex]
        } else {
            Log.d(TAG, "moveToPrevious: already at beginning of queue")
            null
        }
    }

    fun jumpTo(index: Int): Song? {
        Log.d(TAG, "jumpTo: index=$index, queue size=${_queue.value.size}")
        return if (index in _queue.value.indices) {
            _currentIndex.value = index
            Log.d(TAG, "jumpTo: moved to '${_queue.value[index].name}'")
            saveState()
            _queue.value[index]
        } else {
            Log.w(TAG, "jumpTo: index out of bounds")
            null
        }
    }

    fun isNearEnd(): Boolean {
        val near = _currentIndex.value >= _queue.value.size - 3
        Log.d(TAG, "isNearEnd: index=${_currentIndex.value}, size=${_queue.value.size}, result=$near")
        return near
    }

    suspend fun loadMoreSuggestions() {
        if (isLoadingSuggestions) {
            Log.d(TAG, "loadMoreSuggestions: already loading, ignore")
            return
        }
        val current = currentSong
        if (current == null) {
            Log.w(TAG, "loadMoreSuggestions: current song is null, cannot load suggestions")
            return
        }

        if (isDownloadedQueueMode) {
            Log.d(TAG, "loadMoreSuggestions: appending next batch of downloaded songs")
            isLoadingSuggestions = true
            try {
                loadMoreDownloadedSongs()
            } catch (e: Exception) {
                Log.e(TAG, "loadMoreSuggestions: error in loadMoreDownloadedSongs: ${e.message}", e)
            } finally {
                isLoadingSuggestions = false
            }
            return
        }

        if (current.id.matches(Regex("^-?\\d+$"))) {
            Log.d(TAG, "loadMoreSuggestions: skipping for local song (hash ID='${current.id}')")
            return
        }
        Log.d(TAG, "loadMoreSuggestions: triggering suggestions request for songId='${current.id}'")
        isLoadingSuggestions = true

        try {
            val result = musicRepository.getSongSuggestions(current.id, 20)
            result.onSuccess { suggestions ->
                Log.d(TAG, "loadMoreSuggestions: successfully retrieved ${suggestions.size} suggestions")
                addToQueue(suggestions)
            }.onFailure { exception ->
                Log.e(TAG, "loadMoreSuggestions: failed to fetch suggestions: ${exception.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMoreSuggestions: error fetching suggestions: ${e.message}")
        } finally {
            isLoadingSuggestions = false
        }
    }

    private fun loadMoreDownloadedSongs() {
        if (fullDownloadedSourceList.isEmpty()) return
        
        val batch = mutableListOf<Song>()
        var count = 0
        var pointer = downloadedSourceNextIndex
        val total = fullDownloadedSourceList.size

        while (count < 30 && count < total) {
            val song = fullDownloadedSourceList[pointer]
            val key = song.name.lowercase().trim() to song.primaryArtistNames.lowercase().trim()
            if (!_queue.value.any { it.id == song.id } && !playedKeys.contains(key)) {
                batch.add(song)
            }
            pointer = (pointer + 1) % total
            count++
            if (pointer == downloadedSourceNextIndex) break
        }
        downloadedSourceNextIndex = pointer

        if (batch.isEmpty()) {
            Log.d(TAG, "loadMoreDownloadedSongs: no new songs to append from source list")
            return
        }

        Log.d(TAG, "loadMoreDownloadedSongs: Appending ${batch.size} downloaded songs to queue")
        var updatedQueue = _queue.value + batch
        var updatedIndex = _currentIndex.value

        batch.forEach {
            playedSongIds.add(it.id)
            playedKeys.add(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
        }

        // Cap total queue size at 60 max by evicting older played songs from the front
        val maxQueueSize = 60
        if (updatedQueue.size > maxQueueSize && updatedIndex > 0) {
            val excess = updatedQueue.size - maxQueueSize
            val trimCount = excess.coerceAtMost(updatedIndex)
            if (trimCount > 0) {
                val evicted = updatedQueue.take(trimCount)
                updatedQueue = updatedQueue.drop(trimCount)
                updatedIndex -= trimCount
                evicted.forEach {
                    playedSongIds.remove(it.id)
                    playedKeys.remove(it.name.lowercase().trim() to it.primaryArtistNames.lowercase().trim())
                }
                Log.d(TAG, "loadMoreDownloadedSongs: Trimmed $trimCount played songs from head. New queue size=${updatedQueue.size}, new index=$updatedIndex")
            }
        }

        _queue.value = updatedQueue
        _currentIndex.value = updatedIndex
        originalQueue = if (_isShuffleEnabled.value) originalQueue + batch else updatedQueue
        saveState()
        onQueueAppended?.invoke(batch)
    }

    fun toggleShuffle() {
        val enabled = !_isShuffleEnabled.value
        _isShuffleEnabled.value = enabled
        
        val current = currentSong
        if (isDownloadedQueueMode && fullDownloadedSourceList.isNotEmpty()) {
            if (enabled) {
                if (current != null) {
                    val rest = fullDownloadedSourceList.filter { it.id != current.id }.shuffled()
                    fullDownloadedSourceList = listOf(current) + rest
                } else {
                    fullDownloadedSourceList = fullDownloadedSourceList.shuffled()
                }
            } else {
                if (originalQueue.isNotEmpty()) {
                    fullDownloadedSourceList = originalQueue
                }
            }
            val initialBatch = fullDownloadedSourceList.take(31)
            downloadedSourceNextIndex = if (fullDownloadedSourceList.size > 31) 31 else fullDownloadedSourceList.size
            _queue.value = initialBatch
            _currentIndex.value = if (current != null) initialBatch.indexOfFirst { it.id == current.id }.coerceAtLeast(0) else 0
            saveState()
            onQueueReset?.invoke(_queue.value, _currentIndex.value)
            return
        }

        if (enabled) {
            originalQueue = _queue.value
            if (current != null) {
                val rest = _queue.value.filter { it.id != current.id }.shuffled()
                _queue.value = listOf(current) + rest
                _currentIndex.value = 0
            } else {
                _queue.value = _queue.value.shuffled()
                _currentIndex.value = -1
            }
            Log.d(TAG, "toggleShuffle: enabled, queue size=${_queue.value.size}")
        } else {
            if (originalQueue.isNotEmpty()) {
                _queue.value = originalQueue
                if (current != null) {
                    val newIndex = originalQueue.indexOfFirst { it.id == current.id }
                    _currentIndex.value = if (newIndex != -1) newIndex else 0
                } else {
                    _currentIndex.value = -1
                }
            }
            Log.d(TAG, "toggleShuffle: disabled, queue size=${_queue.value.size}")
        }
        saveState()
        onQueueReset?.invoke(_queue.value, _currentIndex.value)
    }

    fun clear() {
        Log.d(TAG, "clear: clearing play queue")
        _queue.value = emptyList()
        originalQueue = emptyList()
        _currentIndex.value = -1
        isDownloadedQueueMode = false
        fullDownloadedSourceList = emptyList()
        playedSongIds.clear()
        playedKeys.clear()
        saveState()
        onQueueReset?.invoke(emptyList(), -1)
    }

    fun removeAt(index: Int) {
        Log.d(TAG, "removeAt: index=$index, queue size=${_queue.value.size}")
        val mutableQueue = _queue.value.toMutableList()
        if (index in mutableQueue.indices) {
            val removedSong = mutableQueue.removeAt(index)
            _queue.value = mutableQueue
            originalQueue = if (_isShuffleEnabled.value) {
                originalQueue.filter { it.id != removedSong.id }
            } else {
                mutableQueue
            }
            playedSongIds.remove(removedSong.id)
            playedKeys.remove(removedSong.name.lowercase().trim() to removedSong.primaryArtistNames.lowercase().trim())
            Log.d(TAG, "removeAt: removed song='${removedSong.name}'")
            if (index < _currentIndex.value) {
                _currentIndex.value = _currentIndex.value - 1
                Log.d(TAG, "removeAt: decremented current index to ${_currentIndex.value}")
            } else if (index == _currentIndex.value) {
                Log.d(TAG, "removeAt: removed current song, index remains ${_currentIndex.value}")
            }
            saveState()
            onQueueRemoved?.invoke(index)
        } else {
            Log.w(TAG, "removeAt: index out of bounds")
        }
    }

    companion object {
        private const val TAG = "QueueManager"
    }
}
