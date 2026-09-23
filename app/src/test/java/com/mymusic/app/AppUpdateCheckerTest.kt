package com.mymusic.app

import com.mymusic.app.utils.AppUpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    @Test
    fun testCompareVersions() {
        // Tag format in GitHub releases: vYY.MM.DD.RUN_NUMBER
        assertEquals(1, AppUpdateChecker.compareVersions("v26.07.10.21", "1.0.0"))
        assertEquals(1, AppUpdateChecker.compareVersions("26.07.10.21", "26.07.10.20"))
        assertEquals(-1, AppUpdateChecker.compareVersions("26.07.10.19", "26.07.10.20"))
        assertEquals(0, AppUpdateChecker.compareVersions("v1.0.0", "1.0.0"))
        assertEquals(0, AppUpdateChecker.compareVersions("v26.07.10.21", "26.07.10.21"))

        // Semantic versioning
        assertEquals(1, AppUpdateChecker.compareVersions("1.0.1", "1.0.0"))
        assertEquals(1, AppUpdateChecker.compareVersions("1.1.0", "1.0.9"))
        assertEquals(1, AppUpdateChecker.compareVersions("2.0.0", "1.99.99"))
        assertEquals(-1, AppUpdateChecker.compareVersions("1.0.0", "1.0.1"))

        // Pre-release or suffix handling
        assertEquals(0, AppUpdateChecker.compareVersions("1.0.0-beta", "1.0.0"))
        assertEquals(1, AppUpdateChecker.compareVersions("1.0.1-rc1", "1.0.0"))
    }

    @Test
    fun testIsUpdateAvailable() {
        assertTrue(AppUpdateChecker.isUpdateAvailable("1.0.0", "v26.07.10.21"))
        assertTrue(AppUpdateChecker.isUpdateAvailable("26.07.10.20", "26.07.10.21"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("26.07.10.21", "26.07.10.21"))
        assertFalse(AppUpdateChecker.isUpdateAvailable("26.07.10.22", "26.07.10.21"))
    }

    @Test
    fun testCleanReleaseNotesFiltersBoilerplate() {
        val boilerplate = """
            Automated release generated on push to main branch.
            
            **Version Name:** `26.09.23.62`
            **Version Code:** `62`
        """.trimIndent()

        val release = com.mymusic.app.data.model.AppRelease(
            tagName = "v26.09.23.62",
            versionName = "26.09.23.62",
            releaseNotes = boilerplate,
            downloadUrl = "https://example.com",
            publishedAt = "2026-09-23",
            apkSize = 3812580L
        )

        assertEquals("• Performance improvements and bug fixes.", release.cleanReleaseNotes())
    }

    @Test
    fun testCleanReleaseNotesFormatsMarkdownAndBullets() {
        val markdownNotes = """
            ### What's Changed
            • Add Material 3 progress bar below downloads header during update/sync
            - Eliminate audio artifact and residual sound on song transitions
            * **Important**: `Fix playback resumption`
            
            **Full Changelog**: https://github.com/example/repo
        """.trimIndent()

        val release = com.mymusic.app.data.model.AppRelease(
            tagName = "v26.09.23.63",
            versionName = "26.09.23.63",
            releaseNotes = markdownNotes,
            downloadUrl = "https://example.com",
            publishedAt = "2026-09-23",
            apkSize = 3812580L
        )

        val expected = """
            • Add Material 3 progress bar below downloads header during update/sync
            • Eliminate audio artifact and residual sound on song transitions
            • Important: Fix playback resumption
        """.trimIndent()

        assertEquals(expected, release.cleanReleaseNotes())
    }
}

