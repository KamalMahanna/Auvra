package com.auvra.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.auvra.app.data.local.entities.SpeedDialItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial_items WHERE isPinned = 1 ORDER BY position ASC")
    fun getAllPinned(): Flow<List<SpeedDialItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialItemEntity)

    @Delete
    suspend fun delete(item: SpeedDialItemEntity)

    @Query("DELETE FROM speed_dial_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

