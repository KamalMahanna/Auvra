package com.auvra.app

import com.auvra.app.data.model.DownloadLink
import com.auvra.app.data.model.Song
import com.auvra.app.utils.resize
import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeUtilsTest {

    @Test
    fun testGoogleUserContentResize() {
        val original = "https://lh3.googleusercontent.com/fife/ABSRlIo=w120-h120-l90-rj"
        val resized = original.resize(1080, 1080)
        assertEquals("https://lh3.googleusercontent.com/fife/ABSRlIo=w1080-h1080-p-l90-rj", resized)

        val resizedMedium = original.resize(544, 544)
        assertEquals("https://lh3.googleusercontent.com/fife/ABSRlIo=w544-h544-p-l90-rj", resizedMedium)
    }

    @Test
    fun testGgphtResize() {
        val original = "https://yt3.ggpht.com/a-/AAuE7mB=s60"
        val resized = original.resize(1080, 1080)
        assertEquals("https://yt3.ggpht.com/a-/AAuE7mB=w1080-h1080-p-l90-rj", resized)

        val singleDim = original.resize(width = 200)
        assertEquals("https://yt3.ggpht.com/a-/AAuE7mB=s200", singleDim)
    }

    @Test
    fun testYouTubeVideoThumbnailUpgrade() {
        val hqUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        val maxRes = hqUrl.resize(1080, 1080)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", maxRes)
    }

    @Test
    fun testLocalAndNonMatchingUrlsUntouched() {
        val localFilePath = "/data/user/0/com.auvra.app/files/artwork.jpg"
        assertEquals(localFilePath, localFilePath.resize(1080, 1080))

        val fileUri = "file:///storage/emulated/0/Download/song.jpg"
        assertEquals(fileUri, fileUri.resize(1080, 1080))

        val customWebUrl = "https://example.com/cover.png"
        assertEquals(customWebUrl, customWebUrl.resize(1080, 1080))
    }

    @Test
    fun testSongHighQualityImageUrlAutoResize() {
        val song = Song(
            id = "yt123",
            name = "Test Song",
            image = listOf(DownloadLink("500x500", "https://lh3.googleusercontent.com/sample=w120-h120-l90-rj"))
        )
        assertEquals("https://lh3.googleusercontent.com/sample=w1080-h1080-p-l90-rj", song.highQualityImageUrl)
        assertEquals("https://lh3.googleusercontent.com/sample=w544-h544-p-l90-rj", song.mediumQualityImageUrl)
        assertEquals("https://lh3.googleusercontent.com/sample=w200-h200-p-l90-rj", song.lowQualityImageUrl)
    }
}

