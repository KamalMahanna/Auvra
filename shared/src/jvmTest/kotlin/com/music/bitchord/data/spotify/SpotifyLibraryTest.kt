package com.music.bitchord.data.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyLibraryTest {
    @Test
    fun playlistIdsComeFromLinksAndUris() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        assertEquals(id, SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/$id"))
        assertEquals(id, SpotifyImporter.extractPlaylistId("  https://open.spotify.com/playlist/$id?si=abc123  "))
        assertEquals(id, SpotifyImporter.extractPlaylistId("https://open.spotify.com/intl-id/playlist/$id"))
        assertEquals(id, SpotifyImporter.extractPlaylistId("spotify:playlist:$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/album/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://notspotify.com/playlist/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("ftp://open.spotify.com/playlist/$id"))
        assertNull(SpotifyImporter.extractPlaylistId("https://open.spotify.com/playlist/short"))
        assertNull(SpotifyImporter.extractPlaylistId(""))
    }
}
