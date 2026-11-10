package com.auvra.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * BitChord Default Player Scrim:
 * "Enough of a scrim to keep white text off a bright sleeve, and no more."
 * Only 6% at the top down to 30% at the bottom.
 * Never darkens or veils the vibrant artwork colors.
 */
val PlayerMeshScrim = Brush.verticalGradient(
    colors = listOf(
        Color.Black.copy(alpha = 0.06f),
        Color.Black.copy(alpha = 0.30f),
    )
)

/** BitChord 3-stop scrim used for lyrics/queue subviews where higher contrast is needed */
val FullArtworkScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1.00f to Color.Black.copy(alpha = 0.64f),
)

val FallbackBackdrop = Color(0xFF121212)

const val HERO_FADE_FRACTION = 0.42f
const val FADE_CURVE_STOPS = 16

/**
 * Smootherstep Hermite curve: `6t⁵ − 15t⁴ + 10t³`.
 * Zero slope and curvature at both ends eliminates any harsh line where the dissolve starts or finishes.
 */
fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * BitChord's signature seamless artwork bottom dissolve:
 * Erases the bottom [HERO_FADE_FRACTION] (42%) along a [smootherStep] Hermite curve into the background blur,
 * completely eliminating any card borders or hard edges.
 */
fun Modifier.artworkBottomDissolve(
    fraction: Float = HERO_FADE_FRACTION,
): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = Array(FADE_CURVE_STOPS + 1) { i ->
                    val t = i / FADE_CURVE_STOPS.toFloat()
                    t to Color.Black.copy(alpha = 1f - smootherStep(t))
                },
                startY = size.height * (1f - fraction),
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

private const val MESH_PX = 120
private const val MESH_GRID = 6
private const val MESH_TEX = 32
private const val MESH_BLUR_RADIUS = 1
private const val MESH_BLUR_PASSES = 3
private const val MESH_FADE_MS = 600
private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f

private const val FULL_BLUR_SOURCE_PX = 128
private const val FULL_BLUR_PASSES = 3

private val meshCache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 16
}

private val blurCache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 8
}

/**
 * Reads and computes BitChord's [ArtworkMesh]:
 * The sleeve is averaged into a [MESH_GRID] (6x6) square of means; row 0 (against the seam)
 * is kept in place, and everything below it is flipped and rotated sideways.
 * Resampled with Hermite smoothstep to 32x32, softened with 3 passes of 1px box blur,
 * and rendered via GPU bilinear texture sampling.
 */
@Composable
fun rememberArtworkMesh(imageUrl: String?): Bitmap? {
    val context = LocalContext.current
    var meshBitmap by remember(imageUrl) { mutableStateOf(imageUrl?.let { meshCache[it] }) }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) {
            meshBitmap = null
            return@LaunchedEffect
        }
        val cached = meshCache[imageUrl]
        if (cached != null) {
            meshBitmap = cached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .size(MESH_PX)
                .allowHardware(false)
                .build()

            val result = SingletonImageLoader.get(context).execute(request)
            val bitmap = (result as? SuccessResult)?.image?.toBitmap()

            if (bitmap != null) {
                val processed = withContext(Dispatchers.Default) {
                    meshOf(bitmap, seed = imageUrl.hashCode())
                }
                if (processed != null) {
                    meshCache[imageUrl] = processed
                    withContext(Dispatchers.Main) {
                        meshBitmap = processed
                    }
                }
            }
        }
    }
    return meshBitmap
}

/**
 * BitChord's primary player backdrop:
 * Anchors the artwork's own colours to where the artwork card stops ([seam]),
 * holding the bottom edge above it and stretching the resampled mesh gradient below it.
 * Overlaid with BitChord's ultra-light 6% -> 30% scrim.
 */
@Composable
fun ArtworkMeshBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    seam: () -> Dp = { 0.dp },
    fallbackColor: Color = FallbackBackdrop,
) {
    val mesh = rememberArtworkMesh(imageUrl)
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<Bitmap?>(null) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(mesh) {
        val next = mesh ?: return@LaunchedEffect
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect

        if (current == null) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackColor),
    ) {
        val seamY = seam().toPx().coerceIn(0f, size.height)
        shown?.let { drawMesh(it, seamY, alpha = 1f) }
        incoming?.let { drawMesh(it, seamY, alpha = fade.value) }

        // BitChord 6% -> 30% light scrim (keeps pure white text readable on bright covers)
        drawRect(brush = PlayerMeshScrim)
    }
}

private fun DrawScope.drawMesh(mesh: Bitmap, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val image = mesh.asImageBitmap()
    val width = size.width.roundToInt()

    // Above the seam: the sleeve's bottom edge, held across the top
    if (seamY > 0.5f) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, seamY.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }

    // From the seam down: the mesh itself, stretched across the controls
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(0, seamY.roundToInt()),
        dstSize = IntSize(width, (size.height - seamY).roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Averages [source] into a 6x6 grid of means, flipped top to bottom so Row 0 is the seam,
 * rotated below the seam, resampled with smoothstep, and box blurred.
 */
private fun meshOf(source: Bitmap, seed: Int): Bitmap? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null

    val cols = MESH_GRID.coerceAtMost(width)
    val rows = MESH_GRID.coerceAtMost(height)
    val rowStep = (height / 128).coerceAtLeast(1)
    val colStep = (width / 128).coerceAtLeast(1)

    val cells = rows * cols
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)

    val line = IntArray(width)
    var y = 0
    while (y < height) {
        source.getPixels(line, 0, width, 0, y, width, 1)
        val rowBase = ((height - 1 - y) * rows / height) * cols
        var x = 0
        while (x < width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += ((pixel shr 16) and 0xFF).toLong()
            green[cell] += ((pixel shr 8) and 0xFF).toLong()
            blue[cell] += (pixel and 0xFF).toLong()
            count[cell]++
            x += colStep
        }
        y += rowStep
    }

    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb(
            (red[cell] / n).toInt(),
            (green[cell] / n).toInt(),
            (blue[cell] / n).toInt()
        ).lifted()
    }

    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
    val scratch = IntArray(texels.size)
    repeat(MESH_BLUR_PASSES) {
        boxBlurPass(texels, scratch, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = true)
        boxBlurPass(scratch, texels, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = false)
    }

    return Bitmap.createBitmap(texels, MESH_TEX, MESH_TEX, Bitmap.Config.ARGB_8888)
}

private fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

private fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)

private fun Int.lifted(): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this, hsl)
    hsl[1] = (hsl[1] * MESH_VIBRANCE).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceAtLeast(MESH_FLOOR)
    return ColorUtils.HSLToColor(hsl)
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
                .allowHardware(false)
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
    fallbackColor: Color = FallbackBackdrop,
    scrim: Brush = FullArtworkScrim,
) {
    val blurredBitmap = rememberBlurredArtwork(imageUrl)

    Box(modifier = modifier.fillMaxSize().background(fallbackColor)) {
        Crossfade(
            targetState = blurredBitmap,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
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

        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = scrim)
        }
    }
}

private fun Bitmap.boxBlurred(passes: Int): Bitmap {
    val w = width
    val h = height
    if (w < 2 || h < 2) return this

    var source = IntArray(w * h)
    getPixels(source, 0, w, 0, 0, w, h)
    var target = IntArray(source.size)
    val radius = (minOf(w, h) / 12).coerceAtLeast(2)

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
