package com.auvra.app

import com.auvra.app.data.repository.*
import com.metrolist.innertube.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MusicRepositoryMappingTest {

    @Test
    fun testSongItemMapping() {
        val songItem = SongItem(
            id = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artists = listOf(Artist(name = "Rick Astley", id = "artist_id")),
            album = Album(name = "Whenever You Need Somebody", id = "album_id"),
            duration = 213,
            thumbnail = "https://example.com/thumb.jpg"
        )

        val auvraSong = songItem.toAuvraSong()
        assertEquals("dQw4w9WgXcQ", auvraSong.id)
        assertEquals("Never Gonna Give You Up", auvraSong.name)
        assertEquals("Rick Astley", auvraSong.primaryArtistNames)
        assertEquals("Whenever You Need Somebody", auvraSong.album.name)
        assertEquals(213, auvraSong.duration)
        assertEquals("3:33", auvraSong.durationFormatted)
        assertEquals("https://example.com/thumb.jpg", auvraSong.highQualityImageUrl)
    }

    @Test
    fun testAlbumItemMapping() {
        val albumItem = AlbumItem(
            browseId = "MPREb_album1",
            playlistId = "OLAK5uy_album1",
            title = "Greatest Hits",
            artists = listOf(Artist(name = "Artist Name", id = "artist_id")),
            year = 2024,
            thumbnail = "https://example.com/album.jpg"
        )

        val auvraAlbum = albumItem.toAuvraAlbum()
        assertEquals("MPREb_album1", auvraAlbum.id)
        assertEquals("Greatest Hits", auvraAlbum.name)
        assertEquals(2024, auvraAlbum.year)
        assertEquals("https://example.com/album.jpg", auvraAlbum.highQualityImageUrl)
    }

    @Test
    fun testPlaylistItemMapping() {
        val playlistItem = PlaylistItem(
            id = "PL12345",
            title = "Top Hits",
            author = Artist(name = "Curator", id = null),
            songCountText = "50 tracks",
            thumbnail = "https://example.com/playlist.jpg",
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )

        val auvraPlaylist = playlistItem.toAuvraPlaylist()
        assertEquals("PL12345", auvraPlaylist.id)
        assertEquals("Top Hits", auvraPlaylist.name)
        assertEquals("https://example.com/playlist.jpg", auvraPlaylist.highQualityImageUrl)
    }

    @Test
    fun testYTItemToModuleItemMapping() {
        val songItem = SongItem(
            id = "song1",
            title = "Song Title",
            artists = listOf(Artist(name = "Singer", id = null)),
            thumbnail = "https://example.com/song.jpg"
        )
        val moduleItem = songItem.toAuvraModuleItem()
        assertNotNull(moduleItem)
        assertEquals("song1", moduleItem!!.id)
        assertEquals("Song Title", moduleItem.name)
        assertEquals("Singer", moduleItem.subtitle)
        assertEquals("song", moduleItem.type)
    }

    @Test
    fun testModuleSectionIsSongsOnly() {
        val songItem1 = com.auvra.app.data.model.ModuleItem(id = "s1", name = "Song 1", type = "song", duration = 185)
        val songItem2 = com.auvra.app.data.model.ModuleItem(id = "s2", name = "Song 2", type = "song", duration = 240)
        val albumItem = com.auvra.app.data.model.ModuleItem(id = "a1", name = "Album 1", type = "album")

        val songSection = com.auvra.app.data.model.ModuleSection(
            title = "Quick picks",
            data = listOf(songItem1, songItem2)
        )
        val mixedSection = com.auvra.app.data.model.ModuleSection(
            title = "Recommended albums",
            data = listOf(songItem1, albumItem)
        )

        assertEquals(true, songSection.isSongsOnly)
        assertEquals(false, mixedSection.isSongsOnly)
        assertEquals("3:05", songItem1.durationFormatted)
        assertEquals("4:00", songItem2.durationFormatted)
    }
}

