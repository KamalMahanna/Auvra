package com.auvra.app.data.local.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_events",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("songId"),
        Index("timestamp")
    ]
)
@Immutable
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "songId") val songId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val playTimeMs: Long = 0L,
)

