package com.auvra.app

import com.auvra.app.download.Mp4MetadataEditor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Mp4TaggingTest {

    @Test
    fun testIsMp4ContainerDetection() {
        val mp4File = File.createTempFile("test_sample", ".m4a").apply {
            deleteOnExit()
            // 00 00 00 20 'f' 't' 'y' 'p' 'M' '4' 'A' ' '
            writeBytes(byteArrayOf(0, 0, 0, 32, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()))
        }
        assertTrue(Mp4MetadataEditor.isMp4(mp4File))

        val webmFile = File.createTempFile("test_webm", ".m4a").apply {
            deleteOnExit()
            // EBML magic bytes: 1A 45 DF A3
            writeBytes(byteArrayOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte(), 0x9f.toByte(), 0x42.toByte(), 0x86.toByte(), 0x81.toByte(), 0x01.toByte(), 0x42.toByte(), 0xf7.toByte(), 0x81.toByte()))
        }
        assertFalse(Mp4MetadataEditor.isMp4(webmFile))

        val shortFile = File.createTempFile("test_short", ".m4a").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        assertFalse(Mp4MetadataEditor.isMp4(shortFile))
    }
}
