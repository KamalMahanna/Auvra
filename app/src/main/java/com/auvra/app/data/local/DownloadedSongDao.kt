package com.auvra.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auvra.app.data.model.DownloadedSong
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedSongDao {
    @Query("SELECT * FROM downloaded_songs")
    fun getAllDownloadedSongs(): Flow<List<DownloadedSong>>

    @Query("SELECT * FROM downloaded_songs")
    suspend fun getAllDownloadedSongsList(): List<DownloadedSong>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedSongs(songs: List<DownloadedSong>)

    @Delete
    suspend fun deleteDownloadedSong(song: DownloadedSong)
}
