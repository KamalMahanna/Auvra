package com.auvra.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

val LINE_FADE = 36.dp
const val MARQUEE_DP_PER_SEC = 26f
val MARQUEE_GAP = 48.dp
const val MARQUEE_REST_MS = 5_000L
const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

private val NoFade = { 0f }
private val FullFade = { 1f }

/**
 * Fades what is drawn here out toward its start and end edges, each over
 * [width] along [smootherStep] — the way Apple / BitChord lets a line too long for its
 * space run out, rather than ending it in an ellipsis or cutting it square.
 */
fun Modifier.fadingEdges(
    width: Dp = LINE_FADE,
    start: () -> Float = NoFade,
    end: () -> Float = NoFade,
): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fade = width.toPx().coerceAtMost(size.width / 2f)
            val stops = Array(FADE_CURVE_STOPS + 1) { i ->
                val t = i / FADE_CURVE_STOPS.toFloat()
                t to Color.Black.copy(alpha = 1f - smootherStep(t))
            }
            val leftBrush = Brush.horizontalGradient(*stops, startX = 0f, endX = fade)
            val rightBrush = Brush.horizontalGradient(*stops, startX = size.width, endX = size.width - fade)
            val rtl = layoutDirection == LayoutDirection.Rtl
            onDrawWithContent {
                drawContent()
                val left = (if (rtl) end() else start()).coerceIn(0f, 1f)
                if (left > 0f) {
                    drawRect(leftBrush, Offset.Zero, Size(fade, size.height), alpha = left, blendMode = BlendMode.DstOut)
                }
                val right = (if (rtl) start() else end()).coerceIn(0f, 1f)
                if (right > 0f) {
                    drawRect(
                        rightBrush,
                        Offset(size.width - fade, 0f),
                        Size(fade, size.height),
                        alpha = right,
                        blendMode = BlendMode.DstOut,
                    )
                }
            }
        }

/**
 * A marquee line's fades, where it [overflows] its box: out at its end always,
 * and at its start while it is away from its resting place — in over the first
 * fade's width of a pass of [travelPx] and back out over the last, by when the
 * copy chasing it has arrived where it started. [offset] is read as it draws.
 */
private fun Modifier.marqueeFade(overflows: Boolean, travelPx: Int, offset: () -> Float): Modifier =
    if (!overflows) {
        this
    } else {
        composed {
            val fadePx = with(LocalDensity.current) { LINE_FADE.toPx() }
            fadingEdges(
                LINE_FADE,
                start = {
                    val moved = -offset()
                    minOf(moved, travelPx - moved) / fadePx
                },
                end = { 1f },
            )
        }
    }

/**
 * Single-line text marquee with BitChord / Apple Music smootherstep edge dissolve.
 * When text fits within its bounds, it displays normally with 0% fade and no animation overhead.
 * When overflowing, it displays with a smooth gradient fade on the trailing edge,
 * pauses at the resting position for [MARQUEE_REST_MS], then scrolls smoothly with
 * a Hermite dissolve on both edges as it travels.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 0L,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onOverflowChange: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        BoxWithConstraints(Modifier.weight(1f, fill = false).clipToBounds()) {
            val maxWidthPx = constraints.maxWidth
            val layout = remember(text, style, maxWidthPx) {
                textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
            }
            val overflowing = layout.size.width > maxWidthPx
            val crawls = enabled && overflowing
            LaunchedEffect(crawls) { onOverflowChange(crawls) }

            val travelPx = if (crawls) {
                layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            } else {
                0
            }

            val offsetX = remember { Animatable(0f) }
            LaunchedEffect(text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(400)
                delay(startDelayMillis)
                while (true) {
                    offsetX.animateTo(-travelPx.toFloat(), tween(scrollMs, easing = LinearEasing))
                    offsetX.snapTo(0f)
                    delay(MARQUEE_REST_MS)
                }
            }

            Row(
                modifier = Modifier
                    .marqueeFade(overflowing, travelPx) { offsetX.value }
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                if (crawls) {
                    Spacer(Modifier.width(MARQUEE_GAP))
                    Text(
                        text = text,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        }
    }
}
