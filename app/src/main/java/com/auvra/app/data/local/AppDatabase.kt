package com.auvra.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.auvra.app.data.local.entities.PlaybackEventEntity
import com.auvra.app.data.local.entities.SongEntity
import com.auvra.app.data.local.entities.SpeedDialItemEntity
import com.auvra.app.data.model.DownloadedSong

@Database(
    entities = [
        DownloadedSong::class,
        SongEntity::class,
        PlaybackEventEntity::class,
        SpeedDialItemEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedSongDao(): DownloadedSongDao
    abstract fun historyDao(): HistoryDao
    abstract fun speedDialDao(): SpeedDialDao
}
