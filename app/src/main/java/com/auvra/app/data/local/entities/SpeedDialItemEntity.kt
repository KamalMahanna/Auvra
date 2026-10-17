package com.auvra.app.data.local.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auvra.app.data.model.DownloadLink
import com.auvra.app.data.model.ModuleItem

@Entity(tableName = "speed_dial_items")
@Immutable
data class SpeedDialItemEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val type: String = "song", // "song", "album", "playlist"
    val position: Int = 0,
    val isPinned: Boolean = true
) {
    fun toModuleItem(): ModuleItem = ModuleItem(
        id = id,
        name = title,
        subtitle = subtitle,
        type = type,
        image = if (thumbnailUrl != null) listOf(DownloadLink("500x500", thumbnailUrl)) else emptyList()
    )
}

