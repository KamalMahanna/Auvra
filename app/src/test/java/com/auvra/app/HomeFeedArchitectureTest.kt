package com.auvra.app

import com.auvra.app.data.innertube.InnertubeHomeParser
import com.auvra.app.data.model.HomeShelf
import com.auvra.app.data.model.ShelfItem
import com.auvra.app.data.model.ShelfLayout
import com.auvra.app.data.model.artworkAt
import com.auvra.app.data.model.shelfLayoutFor
import com.auvra.app.data.model.withoutRepeatsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedArchitectureTest {

    @Test
    fun testWithoutRepeatsOfDeduplicatesSharedShelves() {
        val shelf1 = HomeShelf(
            title = "Albums & singles",
            items = listOf(
                ShelfItem(title = "Album 1", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE1"),
                ShelfItem(title = "Album 2", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE2"),
                ShelfItem(title = "Album 3", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE3"),
            )
        )

        // Repeats 2 out of 3 items (66.6% >= 60%)
        val shelf2Reordered = HomeShelf(
            title = "New albums & singles",
            items = listOf(
                ShelfItem(title = "Album 2", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE2"),
                ShelfItem(title = "Album 1", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE1"),
                ShelfItem(title = "Album 4", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE4"),
            )
        )

        val shelf3Distinct = HomeShelf(
            title = "Community Playlists",
            items = listOf(
                ShelfItem(title = "Playlist 1", subtitle = "User", thumbnailUrl = null, videoId = null, browseId = "VL1"),
                ShelfItem(title = "Playlist 2", subtitle = "User", thumbnailUrl = null, videoId = null, browseId = "VL2"),
            )
        )

        val existing = listOf(shelf1)
        val incoming = listOf(shelf2Reordered, shelf3Distinct)
        val deduplicated = incoming.withoutRepeatsOf(existing)

        assertEquals(1, deduplicated.size)
        assertEquals("Community Playlists", deduplicated.first().title)
    }

    @Test
    fun testSongShelvesWithoutBrowseIdAreNotFilteredByRepeats() {
        val recents = HomeShelf(
            title = "Recents",
            items = listOf(
                ShelfItem(title = "Song A", subtitle = "Artist A", thumbnailUrl = null, videoId = "vidA", browseId = null),
                ShelfItem(title = "Song B", subtitle = "Artist B", thumbnailUrl = null, videoId = "vidB", browseId = null),
            ),
            layout = ShelfLayout.LIST
        )

        val listenAgain = HomeShelf(
            title = "Listen again",
            items = listOf(
                ShelfItem(title = "Song A", subtitle = "Artist A", thumbnailUrl = null, videoId = "vidA", browseId = null),
                ShelfItem(title = "Song B", subtitle = "Artist B", thumbnailUrl = null, videoId = "vidB", browseId = null),
            ),
            layout = ShelfLayout.LIST
        )

        val result = listOf(listenAgain).withoutRepeatsOf(listOf(recents))
        assertEquals(1, result.size)
        assertEquals("Listen again", result.first().title)
    }

    @Test
    fun testShelfLayoutForDecision() {
        val trackItem = ShelfItem(title = "Track", subtitle = "Artist", thumbnailUrl = null, videoId = "vid1", browseId = null)
        val videoItem = ShelfItem(title = "Video", subtitle = "Artist", thumbnailUrl = null, videoId = "vid2", browseId = null, isVideo = true)
        val albumItem = ShelfItem(title = "Album", subtitle = "Artist", thumbnailUrl = null, videoId = null, browseId = "MPRE1")

        // 1. Listen again is always LIST
        assertEquals(ShelfLayout.LIST, shelfLayoutFor("Listen again", listOf(albumItem), fromList = false))

        // 2. fromList with tracks is LIST
        assertEquals(ShelfLayout.LIST, shelfLayoutFor("Quick picks", listOf(trackItem), fromList = true))

        // 3. Majority videos is VIDEO
        assertEquals(ShelfLayout.VIDEO, shelfLayoutFor("Top music videos", listOf(videoItem, videoItem, trackItem), fromList = false))

        // 4. Default albums / mixes is CARDS
        assertEquals(ShelfLayout.CARDS, shelfLayoutFor("Featured albums", listOf(albumItem, trackItem), fromList = false))
    }

    @Test
    fun testArtworkAtSizing() {
        val standardUrl = "https://lh3.googleusercontent.com/abc=s120-c"
        val upgraded = standardUrl.artworkAt(480)
        assertTrue(upgraded!!.contains("=s480"))

        val ggphtUrl = "https://yt3.ggpht.com/xyz=s68-c-k-c0x00ffffff-no-rj"
        val upgradedGgpht = ggphtUrl.artworkAt(240)
        assertTrue(upgradedGgpht!!.contains("=s240"))
    }

    @Test
    fun testArtistFromSubtitle() {
        val sub1 = "Song • The Weeknd • 3:20"
        assertEquals("The Weeknd", InnertubeHomeParser.artistFromSubtitle(sub1))

        val sub2 = "Single • Billie Eilish • 120M plays"
        assertEquals("Billie Eilish", InnertubeHomeParser.artistFromSubtitle(sub2))

        val sub3 = "Adele"
        assertEquals("Adele", InnertubeHomeParser.artistFromSubtitle(sub3))
    }
}
