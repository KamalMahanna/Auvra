package com.auvra.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import com.auvra.app.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

object ArtworkColorCache {
    val DEFAULT_ACCENT_COLOR: Int = 0xFF818CF8.toInt()
    private val cache = android.util.LruCache<String, Int>(100)

    fun get(songId: String): Int? = cache.get(songId)
    fun put(songId: String, color: Int) {
        cache.put(songId, color)
    }
}

@Singleton
class ArtworkColorExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun extractAccentColor(song: Song?): Color = withContext(Dispatchers.IO) {
        if (song == null) {
            return@withContext GlassPrimary
        }

        ArtworkColorCache.get(song.id)?.let { cachedArgb ->
            return@withContext Color(cachedArgb)
        }

        val imageUrl = song.highQualityImageUrl
            ?: song.mediumQualityImageUrl
            ?: song.lowQualityImageUrl
            ?: song.image.firstOrNull()?.url

        Log.w(TAG, "extractAccentColor for '${song.name}': imageUrl='$imageUrl'")

        try {
            // 1. Remote or local image path via Coil
            if (!imageUrl.isNullOrBlank()) {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(48, 48)
                    .allowHardware(false)
                    .build()

                val result = SingletonImageLoader.get(context).execute(request)
                val bitmap: Bitmap? = when (val image = result.image) {
                    is BitmapImage -> image.bitmap
                    null -> {
                        if (result is ErrorResult) {
                            Log.w(TAG, "Coil ImageRequest error for '${song.name}': ${result.throwable.message}")
                        }
                        null
                    }
                    else -> {
                        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        image.draw(canvas)
                        bmp
                    }
                }

                if (bitmap != null) {
                    val color = extractFromBitmap(bitmap)
                    ArtworkColorCache.put(song.id, color.toArgb())
                    Log.w(TAG, "Extracted vibrant color for '${song.name}': $color")
                    return@withContext color
                }
            }

            // 2. Embedded cover art in local audio files
            if (song.url.isNotBlank() && (song.url.endsWith(".mp3", true) || song.url.endsWith(".m4a", true) || song.url.startsWith("/"))) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(song.url)
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = 8
                        }
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size, options)
                        if (bitmap != null) {
                            val color = extractFromBitmap(bitmap)
                            ArtworkColorCache.put(song.id, color.toArgb())
                            Log.w(TAG, "Extracted embedded art color for '${song.name}': $color")
                            return@withContext color
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read embedded picture: ${e.message}")
                } finally {
                    retriever.release()
                }
            }

            Log.w(TAG, "Fallback to GlassPrimary for '${song.name}'")
            ArtworkColorCache.put(song.id, GlassPrimary.toArgb())
            GlassPrimary
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract accent color: ${e.message}", e)
            ArtworkColorCache.put(song.id, GlassPrimary.toArgb())
            GlassPrimary
        }
    }

    private suspend fun extractFromBitmap(bitmap: Bitmap): Color = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        extractDominantVibrantColor(pixels)
    }

    companion object {
        private const val TAG = "ArtworkColorExtractor"

        fun extractDominantVibrantColor(pixels: IntArray): Color {
            val binCount = 36
            val binScores = FloatArray(binCount)
            val binHues = FloatArray(binCount)
            val binSaturations = FloatArray(binCount)
            val binValues = FloatArray(binCount)
            val binCounts = IntArray(binCount)

            val hsv = FloatArray(3)

            for (pixel in pixels) {
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF

                rgbToHsv(r, g, b, hsv)
                val h = hsv[0] // 0..360
                val s = hsv[1] // 0..1
                val v = hsv[2] // 0..1

                // Filter out near-black, blown-out whites, and low-saturation grays
                if (v < 0.15f || (v > 0.90f && s < 0.20f) || s < 0.20f) {
                    continue
                }

                val binIndex = ((h / 360f) * binCount).toInt().coerceIn(0, binCount - 1)
                // Weight by saturation and optimal luminance for dark glass themes
                val score = s * (1.0f - abs(v - 0.70f))

                binScores[binIndex] += score
                binHues[binIndex] += h
                binSaturations[binIndex] += s
                binValues[binIndex] += v
                binCounts[binIndex]++
            }

            var bestBin = -1
            var highestScore = 0f
            for (i in 0 until binCount) {
                if (binScores[i] > highestScore) {
                    highestScore = binScores[i]
                    bestBin = i
                }
            }

            if (bestBin == -1 || binCounts[bestBin] == 0) {
                return GlassPrimary
            }

            val count = binCounts[bestBin].toFloat()
            val avgH = binHues[bestBin] / count
            // Boost saturation for vivid vibrancy against dark glass
            val tunedS = (binSaturations[bestBin] / count).coerceIn(0.60f, 0.95f)
            // Tune value for high contrast readability
            val tunedV = (binValues[bestBin] / count).coerceIn(0.70f, 0.90f)

            return hsvToColor(avgH, tunedS, tunedV)
        }

        fun rgbToHsv(r: Int, g: Int, b: Int, hsv: FloatArray) {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f
            val max = maxOf(rf, gf, bf)
            val min = minOf(rf, gf, bf)
            val delta = max - min

            val v = max
            val s = if (max == 0f) 0f else delta / max

            val h = when {
                delta == 0f -> 0f
                max == rf -> ((gf - bf) / delta).mod(6f) * 60f
                max == gf -> (((bf - rf) / delta) + 2f) * 60f
                else -> (((rf - gf) / delta) + 4f) * 60f
            }

            hsv[0] = if (h < 0f) h + 360f else h
            hsv[1] = s
            hsv[2] = v
        }

        fun hsvToColor(h: Float, s: Float, v: Float): Color {
            val c = v * s
            val x = c * (1f - abs((h / 60f).mod(2f) - 1f))
            val m = v - c
            val (rf, gf, bf) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            return Color(
                red = (rf + m).coerceIn(0f, 1f),
                green = (gf + m).coerceIn(0f, 1f),
                blue = (bf + m).coerceIn(0f, 1f)
            )
        }
    }
}
