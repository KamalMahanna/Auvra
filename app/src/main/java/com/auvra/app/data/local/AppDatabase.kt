package com.auvra.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.auvra.app.data.model.DownloadedSong

@Database(entities = [DownloadedSong::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedSongDao(): DownloadedSongDao
}
