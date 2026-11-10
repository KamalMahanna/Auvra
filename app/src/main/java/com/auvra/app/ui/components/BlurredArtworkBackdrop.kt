package com.auvra.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Performance-optimized background blur inspired by BitChord:
 * Instead of paying a full-screen GPU RenderEffect / Modifier.blur on a 1080p+ layer
 * on every single frame, we decode a small 128px thumbnail, perform a 3-pass box blur
 * on Dispatchers.Default, and cache the result. The GPU then renders this pre-blurred
 * bitmap with hardware bilinear sampling at display refresh rate with virtually 0% overhead.
 */
private const val FULL_BLUR_SOURCE_PX = 128
private const val FULL_BLUR_PASSES = 3

private val blurCache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 8
}

@Composable
fun rememberBlurredArtwork(imageUrl: String?): Bitmap? {
    val context = LocalContext.current
    var blurredBitmap by remember(imageUrl) { mutableStateOf(imageUrl?.let { blurCache[it] }) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            blurredBitmap = null
            return@LaunchedEffect
        }
        val cached = blurCache[imageUrl]
        if (cached != null) {
            blurredBitmap = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(FULL_BLUR_SOURCE_PX)
                .allowHardware(false) // Required for pixel access
                .build()

            val result = SingletonImageLoader.get(context).execute(request)
            val bitmap = (result as? SuccessResult)?.image?.toBitmap()

            if (bitmap != null) {
                val blurred = withContext(Dispatchers.Default) {
                    bitmap.boxBlurred(FULL_BLUR_PASSES)
                }
                blurCache[imageUrl] = blurred
                withContext(Dispatchers.Main) {
                    blurredBitmap = blurred
                }
            }
        }
    }
    return blurredBitmap
}

@Composable
fun FullArtworkBlurBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    fallbackColor: Color = Color(0xFF121216),
) {
    val blurredBitmap = rememberBlurredArtwork(imageUrl)

    Box(modifier = modifier.fillMaxSize().background(fallbackColor)) {
        Crossfade(
            targetState = blurredBitmap,
            animationSpec = tween(500, easing = FastOutSlowInEasing),
            label = "blurredArtworkBackdropFade"
        ) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Ambient dark scrim overlay for high-contrast legibility of controls and lyrics
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )
    }
}

/**
 * Three small box passes approximate a broad Gaussian blur in O(N) linear time.
 */
private fun Bitmap.boxBlurred(passes: Int): Bitmap {
    val w = width
    val h = height
    if (w < 2 || h < 2) return this

    var source = IntArray(w * h)
    getPixels(source, 0, w, 0, 0, w, h)
    var target = IntArray(source.size)
    val radius = (minOf(w, h) / 10).coerceAtLeast(2)

    repeat(passes) {
        boxBlurPass(source, target, w, h, radius, horizontal = true)
        boxBlurPass(target, source, w, h, radius, horizontal = false)
    }

    return Bitmap.createBitmap(source, w, h, config ?: Bitmap.Config.ARGB_8888)
}

private fun boxBlurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1

    repeat(minor) { fixed ->
        var red = 0
        var green = 0
        var blue = 0

        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position]
            else source[position * width + fixed]
        }

        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }

        repeat(major) { moving ->
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = (0xFF shl 24) or
                    (((red + window / 2) / window) shl 16) or
                    (((green + window / 2) / window) shl 8) or
                    ((blue + window / 2) / window)

            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
