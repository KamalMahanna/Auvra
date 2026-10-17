package com.auvra.app.data.local.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auvra.app.data.model.ArtistMap
import com.auvra.app.data.model.DownloadLink
import com.auvra.app.data.model.ModuleItem
import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongAlbum
import com.auvra.app.data.model.SongArtists

@Entity(tableName = "songs")
@Immutable
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistNames: String,
    val albumName: String? = null,
    val albumId: String? = null,
    val duration: Int? = null,
    val thumbnailUrl: String? = null,
) {
    fun toSong(): Song = Song(
        id = id,
        name = title,
        duration = duration,
        album = SongAlbum(id = albumId, name = albumName, url = null),
        artists = SongArtists(
            primary = listOf(ArtistMap(name = artistNames))
        ),
        image = if (thumbnailUrl != null) listOf(DownloadLink("500x500", thumbnailUrl)) else emptyList()
    )

    fun toModuleItem(): ModuleItem = ModuleItem(
        id = id,
        name = title,
        subtitle = artistNames,
        type = "song",
        image = if (thumbnailUrl != null) listOf(DownloadLink("500x500", thumbnailUrl)) else emptyList()
    )

    companion object {
        fun fromSong(song: Song): SongEntity = SongEntity(
            id = song.id,
            title = song.name,
            artistNames = song.primaryArtistNames.ifEmpty { "Unknown Artist" },
            albumName = song.album.name,
            albumId = song.album.id,
            duration = song.duration,
            thumbnailUrl = song.highQualityImageUrl ?: song.mediumQualityImageUrl ?: song.lowQualityImageUrl
        )
    }
}

