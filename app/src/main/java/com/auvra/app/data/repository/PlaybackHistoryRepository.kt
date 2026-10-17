package com.auvra.app.data.repository

import com.auvra.app.data.local.HistoryDao
import com.auvra.app.data.local.SpeedDialDao
import com.auvra.app.data.local.entities.PlaybackEventEntity
import com.auvra.app.data.local.entities.SongEntity
import com.auvra.app.data.local.entities.SpeedDialItemEntity
import com.auvra.app.data.model.ModuleItem
import com.auvra.app.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackHistoryRepository @Inject constructor(
    private val historyDao: HistoryDao,
    private val speedDialDao: SpeedDialDao
) {
    val recentlyPlayedSongs: Flow<List<Song>> = historyDao.getRecentlyPlayedSongs(limit = 50)
        .map { list -> list.map { it.toSong() } }

    val recentlyPlayedModuleItems: Flow<List<ModuleItem>> = historyDao.getRecentlyPlayedSongs(limit = 27)
        .map { list -> list.map { it.toModuleItem() } }

    val quickPicks: Flow<List<Song>> = historyDao.getQuickPicks(limit = 20)
        .map { list -> list.map { it.toSong() } }

    val quickPicksModuleItems: Flow<List<ModuleItem>> = historyDao.getQuickPicks(limit = 20)
        .map { list -> list.map { it.toModuleItem() } }

    val pinnedSpeedDialItems: Flow<List<ModuleItem>> = speedDialDao.getAllPinned()
        .map { list -> list.map { it.toModuleItem() } }

    val eventCount: Flow<Int> = historyDao.getEventCount()

    suspend fun recordPlayback(song: Song, playTimeMs: Long = 0L) {
        val entity = SongEntity.fromSong(song)
        historyDao.recordPlayback(entity, playTimeMs)
    }

    suspend fun pinSpeedDialItem(item: SpeedDialItemEntity) {
        speedDialDao.insert(item)
    }

    suspend fun unpinSpeedDialItem(id: String) {
        speedDialDao.deleteById(id)
    }
}

