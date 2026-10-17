package com.auvra.app

import com.auvra.app.download.Mp4MetadataEditor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Mp4MetadataEditorTest {

    @Test
    fun testWriteMetadataToNightChanges() {
        val srcFile = File("/tmp/night_changes.m4a")
        if (!srcFile.exists()) return

        val testFile = File("/tmp/test_editor_output.m4a")
        srcFile.copyTo(testFile, overwrite = true)

        val artFile = File("/tmp/test_art.jpg")
        val artBytes = if (artFile.exists()) artFile.readBytes() else null

        val success = Mp4MetadataEditor.writeMetadata(
            file = testFile,
            title = "Night Changes",
            artist = "One Direction",
            album = "Four",
            artworkBytes = artBytes
        )

        assertTrue(success)
        println("Mp4MetadataEditor wrote metadata successfully! New file size: ${testFile.length()}")
    }
}

