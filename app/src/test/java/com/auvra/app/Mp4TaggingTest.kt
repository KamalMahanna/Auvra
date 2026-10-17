package com.auvra.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Mp4TaggingTest {

    private fun isMp4Container(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@use false
                header[4] == 'f'.code.toByte() &&
                header[5] == 't'.code.toByte() &&
                header[6] == 'y'.code.toByte() &&
                header[7] == 'p'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun testIsMp4ContainerDetection() {
        val mp4File = File.createTempFile("test_sample", ".m4a").apply {
            deleteOnExit()
            // 00 00 00 20 'f' 't' 'y' 'p' 'M' '4' 'A' ' '
            writeBytes(byteArrayOf(0, 0, 0, 32, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()))
        }
        assertTrue(isMp4Container(mp4File))

        val webmFile = File.createTempFile("test_webm", ".m4a").apply {
            deleteOnExit()
            // EBML magic bytes: 1A 45 DF A3
            writeBytes(byteArrayOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte(), 0x9f.toByte(), 0x42.toByte(), 0x86.toByte(), 0x81.toByte(), 0x01.toByte(), 0x42.toByte(), 0xf7.toByte(), 0x81.toByte()))
        }
        assertFalse(isMp4Container(webmFile))

        val shortFile = File.createTempFile("test_short", ".m4a").apply {
            deleteOnExit()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        assertFalse(isMp4Container(shortFile))
    }
}
