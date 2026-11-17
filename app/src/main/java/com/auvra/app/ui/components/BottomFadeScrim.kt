package com.auvra.app.ui.components

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FADE_HEIGHT = 150.dp
private val FADE_HEIGHT_WITH_MINI_PLAYER = 224.dp
private const val STOPS = 16

private val GESTURE_BAR_FLOOR = 15.dp
internal val floatingBarInsets: WindowInsets
    @Composable get() = WindowInsets.navigationBars.union(WindowInsets(bottom = GESTURE_BAR_FLOOR))

/**
 * Floor gradient matching BitChord: smoothly fades out content scrolling
 * behind the floating navigation bar and miniplayer using a 16-stop EaseInCubic curve.
 */
@Composable
fun BottomFadeScrim(
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
    pageColor: Color = MaterialTheme.colorScheme.background,
) {
    val inset = floatingBarInsets.asPaddingValues().calculateBottomPadding()
    val height by animateDpAsState(
        targetValue = inset + if (withMiniPlayer) FADE_HEIGHT_WITH_MINI_PLAYER else FADE_HEIGHT,
        animationSpec = tween(220),
        label = "bottomFadeScrimHeight",
    )

    val brush = remember(pageColor) {
        Brush.verticalGradient(
            colorStops = Array(STOPS) { i ->
                val t = i / (STOPS - 1f)
                t to pageColor.copy(alpha = EaseInCubic.transform(t))
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}
