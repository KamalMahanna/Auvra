package com.auvra.app

import androidx.compose.ui.graphics.Color
import com.auvra.app.ui.theme.ArtworkColorExtractor
import com.auvra.app.ui.theme.GlassPrimary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkColorExtractorTest {

    private fun makeArgb(r: Int, g: Int, b: Int, a: Int = 255): Int {
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun testVibrantRedImageExtractsRedAccent() {
        // Create an array of pixels dominated by vivid crimson/red (220, 20, 60)
        val redPixel = makeArgb(220, 20, 60)
        val darkPixel = makeArgb(15, 15, 20)
        val pixels = IntArray(200) { if (it < 120) redPixel else darkPixel }

        val extracted = ArtworkColorExtractor.extractDominantVibrantColor(pixels)
        assertNotEquals(GlassPrimary, extracted)

        // Verify hue is in the red range (0..30 or 330..360)
        val hsv = FloatArray(3)
        ArtworkColorExtractor.rgbToHsv(
            (extracted.red * 255).toInt(),
            (extracted.green * 255).toInt(),
            (extracted.blue * 255).toInt(),
            hsv
        )
        assertTrue("Hue should be red (${hsv[0]})", hsv[0] <= 30f || hsv[0] >= 330f)
        assertTrue("Saturation should be high for vibrancy (${hsv[1]})", hsv[1] >= 0.60f)
        assertTrue("Luminance should be tuned for dark background readability (${hsv[2]})", hsv[2] >= 0.70f)
    }

    @Test
    fun testVibrantCyanImageExtractsCyanAccent() {
        // Cyan/teal pixels (0, 200, 220)
        val cyanPixel = makeArgb(0, 200, 220)
        val darkPixel = makeArgb(10, 10, 15)
        val pixels = IntArray(200) { if (it < 130) cyanPixel else darkPixel }

        val extracted = ArtworkColorExtractor.extractDominantVibrantColor(pixels)
        assertNotEquals(GlassPrimary, extracted)

        val hsv = FloatArray(3)
        ArtworkColorExtractor.rgbToHsv(
            (extracted.red * 255).toInt(),
            (extracted.green * 255).toInt(),
            (extracted.blue * 255).toInt(),
            hsv
        )
        // Cyan hue is ~180..200
        assertTrue("Hue should be cyan (${hsv[0]})", hsv[0] in 170f..210f)
    }

    @Test
    fun testPureMonochromeFallbackToGlassPrimary() {
        // Grayscale / black & white pixels with zero saturation
        val grayPixel1 = makeArgb(30, 30, 30)
        val grayPixel2 = makeArgb(180, 180, 180)
        val pixels = IntArray(100) { if (it % 2 == 0) grayPixel1 else grayPixel2 }

        val extracted = ArtworkColorExtractor.extractDominantVibrantColor(pixels)
        // Since saturation < 0.20 for all pixels, it cleanly falls back to GlassPrimary
        assertEquals(GlassPrimary, extracted)
    }
}
