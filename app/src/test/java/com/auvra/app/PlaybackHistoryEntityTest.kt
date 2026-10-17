package com.auvra.app

import com.auvra.app.data.local.entities.SongEntity
import com.auvra.app.data.local.entities.SpeedDialItemEntity
import com.auvra.app.data.model.ArtistMap
import com.auvra.app.data.model.DownloadLink
import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongAlbum
import com.auvra.app.data.model.SongArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackHistoryEntityTest {

    @Test
    fun testSongEntityToSongAndBack() {
        val originalSong = Song(
            id = "test_song_123",
            name = "Blinding Lights",
            duration = 200,
            album = SongAlbum(id = "album_456", name = "After Hours", url = null),
            artists = SongArtists(primary = listOf(ArtistMap(name = "The Weeknd"))),
            image = listOf(DownloadLink("500x500", "https://example.com/art.jpg"))
        )

        val entity = SongEntity.fromSong(originalSong)
        assertEquals("test_song_123", entity.id)
        assertEquals("Blinding Lights", entity.title)
        assertEquals("The Weeknd", entity.artistNames)
        assertEquals("After Hours", entity.albumName)
        assertEquals("album_456", entity.albumId)
        assertEquals(200, entity.duration)
        assertEquals("https://example.com/art.jpg", entity.thumbnailUrl)

        val mappedBack = entity.toSong()
        assertEquals(originalSong.id, mappedBack.id)
        assertEquals(originalSong.name, mappedBack.name)
        assertEquals(originalSong.primaryArtistNames, mappedBack.primaryArtistNames)
        assertEquals(originalSong.album.name, mappedBack.album.name)
        assertEquals(originalSong.duration, mappedBack.duration)

        val moduleItem = entity.toModuleItem()
        assertEquals("test_song_123", moduleItem.id)
        assertEquals("Blinding Lights", moduleItem.name)
        assertEquals("The Weeknd", moduleItem.subtitle)
        assertEquals("song", moduleItem.type)
    }

    @Test
    fun testSpeedDialItemEntityToModuleItem() {
        val entity = SpeedDialItemEntity(
            id = "speed_1",
            title = "Top Hits 2024",
            subtitle = "Pop",
            thumbnailUrl = "https://example.com/speed.jpg",
            type = "playlist",
            position = 0,
            isPinned = true
        )

        val moduleItem = entity.toModuleItem()
        assertEquals("speed_1", moduleItem.id)
        assertEquals("Top Hits 2024", moduleItem.name)
        assertEquals("Pop", moduleItem.subtitle)
        assertEquals("playlist", moduleItem.type)
        assertEquals("https://example.com/speed.jpg", moduleItem.highQualityImageUrl)
    }
}
