package com.auvra.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.auvra.app.data.local.entities.PlaybackEventEntity
import com.auvra.app.data.local.entities.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Insert
    suspend fun insertEvent(event: PlaybackEventEntity)

    @Transaction
    suspend fun recordPlayback(song: SongEntity, playTimeMs: Long = 0L) {
        insertSong(song)
        insertEvent(PlaybackEventEntity(songId = song.id, playTimeMs = playTimeMs))
    }

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, MAX(timestamp) AS lastPlayed
            FROM playback_events
            GROUP BY songId
            ORDER BY lastPlayed DESC
            LIMIT :limit
        ) e ON s.id = e.songId
        ORDER BY e.lastPlayed DESC
    """)
    fun getRecentlyPlayedSongs(limit: Int = 50): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, COUNT(*) AS playCount
            FROM playback_events
            WHERE timestamp >= :fromTimestamp
            GROUP BY songId
            ORDER BY playCount DESC
            LIMIT :limit
        ) e ON s.id = e.songId
        ORDER BY e.playCount DESC
    """)
    fun getMostPlayedSongs(fromTimestamp: Long = 0L, limit: Int = 20): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, COUNT(*) AS playCount, MAX(timestamp) AS lastPlayed
            FROM playback_events
            GROUP BY songId
            ORDER BY (playCount * 2 + lastPlayed / 86400000) DESC
            LIMIT :limit
        ) e ON s.id = e.songId
    """)
    fun getQuickPicks(limit: Int = 20): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM playback_events")
    fun getEventCount(): Flow<Int>

    @Query("DELETE FROM playback_events WHERE songId = :songId")
    suspend fun deleteEventsForSong(songId: String)
}

