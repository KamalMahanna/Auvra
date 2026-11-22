package com.music.bitchord

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.LikeStatus
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class LikedMusicSyncTest {

    @After
    fun tearDown() {
        LikeState.clear()
    }

    @Test
    fun `an existing like survives a menu that says nothing`() {
        LikeState.clear()
        LikeState.set("abc123", LikeStatus.LIKE)

        InnertubeParser.parseSongMenu(
            Json.parseToJsonElement(
                """{"contents":[{"playlistPanelVideoRenderer":{"videoId":"abc123"}}]}""",
            ),
            "abc123",
        )?.let { LikeState.rememberStated("abc123", it.likeStatus) }

        // The menu carried no rating (null), so the LIKE stays put.
        assertEquals(LikeStatus.LIKE, LikeState.overrides.value["abc123"])
    }

    @Test
    fun `a stated indifferent rating does not downgrade an existing like`() {
        LikeState.clear()
        LikeState.set("abc123", LikeStatus.LIKE)

        LikeState.rememberStated("abc123", LikeStatus.INDIFFERENT)

        assertEquals(LikeStatus.LIKE, LikeState.overrides.value["abc123"])
    }
}
