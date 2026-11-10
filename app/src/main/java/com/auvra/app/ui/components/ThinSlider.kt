package com.auvra.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Music style scrubber: a sleek hairline capsule with no thumb knob.
 * The bar expands responsively under the user's finger during dragging/scrubbing
 * and settles back smoothly with spring physics upon release.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    idleHeight: Dp = 6.dp,
    activeHeight: Dp = 11.dp,
    activeColor: Color = Color.White.copy(alpha = 0.95f),
    inactiveColor: Color = Color.White.copy(alpha = 0.25f),
) {
    var dragging by remember { mutableStateOf(false) }

    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thinSliderHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(activeHeight + 20.dp) // Generous invisible touch target
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val radius = CornerRadius(size.height / 2f)

            // Inactive background capsule
            drawRoundRect(
                color = inactiveColor,
                size = size,
                cornerRadius = radius
            )

            // Active played progress capsule
            val fraction = value.coerceIn(0f, 1f)
            val filledWidth = (size.width * fraction)
                .coerceAtLeast(if (fraction > 0f) size.height else 0f)
                .coerceAtMost(size.width)

            if (filledWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(filledWidth, size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}
