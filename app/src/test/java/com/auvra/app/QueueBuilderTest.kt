package com.auvra.app

import com.auvra.app.data.model.ArtistMap
import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongArtists
import com.auvra.app.player.QueueBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueBuilderTest {

    private fun createSong(
        id: String,
        name: String,
        artistName: String,
        duration: Int = 200,
    ): Song {
        return Song(
            id = id,
            name = name,
            duration = duration,
            artists = SongArtists(
                primary = listOf(ArtistMap(name = artistName))
            )
        )
    }

    @Test
    fun `isSameRecording matches identical videoId`() {
        val a = createSong("vid1", "Song Title", "Artist A")
        val b = createSong("vid1", "Different Title", "Artist B")
        assertTrue(QueueBuilder.isSameRecording(a, b))
    }

    @Test
    fun `isSameRecording matches audio and music video cuts`() {
        // e.g. "Kesariya" vs "Kesariya (From \"Brahmastra\") | Official Video"
        val officialAudio = createSong("audio1", "Kesariya", "Arijit Singh")
        val musicVideo = createSong(
            "video1",
            "Kesariya (From \"Brahmastra\") | Official Video",
            "Arijit Singh, Pritam"
        )
        assertTrue(QueueBuilder.isSameRecording(officialAudio, musicVideo))
    }

    @Test
    fun `isSameRecording preserves distinct remixes and covers`() {
        val original = createSong("id1", "Kesariya", "Arijit Singh")
        val remix = createSong("id2", "Kesariya (Dance Remix)", "Arijit Singh")
        val cover = createSong("id3", "Kesariya (Acoustic Cover)", "Arijit Singh")

        assertFalse(QueueBuilder.isSameRecording(original, remix))
        assertFalse(QueueBuilder.isSameRecording(original, cover))
    }

    @Test
    fun `extend filters duplicates already in existing queue`() {
        val existing = listOf(
            createSong("id1", "Song One", "Artist A"),
            createSong("id2", "Song Two", "Artist B"),
        )
        val candidates = listOf(
            createSong("id1_diff", "Song One (Official Music Video)", "Artist A"), // duplicate
            createSong("id3", "Song Three", "Artist C"),
            createSong("id4", "Song Four", "Artist D"),
        )

        val extended = QueueBuilder.extend(existing, candidates, limit = 5)
        assertEquals(2, extended.size)
        assertEquals("id3", extended[0].id)
        assertEquals("id4", extended[1].id)
    }

    @Test
    fun `extend caps per-artist suggestions`() {
        val existing = listOf(
            createSong("seedId", "Seed Song", "Seed Artist")
        )
        // 5 songs by "Other Artist", should be capped at PER_ARTIST_LIMIT = 2
        val candidates = (1..5).map {
            createSong("c$it", "Other Track $it", "Other Artist")
        }

        val extended = QueueBuilder.extend(existing, candidates, limit = 10)
        assertEquals(2, extended.size)
    }

    @Test
    fun `extend allows seed artist more room but caps at seed limit`() {
        val existing = listOf(
            createSong("seedId", "Seed Track", "Seed Artist")
        )
        // 6 songs by "Seed Artist", capped at SEED_ARTIST_LIMIT = 4
        val candidates = (1..6).map {
            createSong("s$it", "Seed Album Track $it", "Seed Artist")
        }

        val extended = QueueBuilder.extend(existing, candidates, limit = 10)
        assertEquals(QueueBuilder.SEED_ARTIST_LIMIT, extended.size)
    }
}
