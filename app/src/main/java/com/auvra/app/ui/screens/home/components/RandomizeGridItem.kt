package com.auvra.app.ui.screens.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
fun RandomizeGridItem(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When isLoading is true, multiplier goes to 0 (moving dots to center)
    // When isLoading is false, multiplier goes to 1 (moving dots to corners)
    val dotOffsetMultiplier by animateFloatAsState(
        targetValue = if (isLoading) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "dotOffset"
    )

    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "loadingAlpha"
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Die Dots (5-dot pattern)
        val dotColor = MaterialTheme.colorScheme.onSecondaryContainer
        val dotSize = 12.dp
        val padding = 22.dp

        // Top Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        (-padding * dotOffsetMultiplier).roundToPx(),
                        (-padding * dotOffsetMultiplier).roundToPx()
                    )
                }
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Top Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        (padding * dotOffsetMultiplier).roundToPx(),
                        (-padding * dotOffsetMultiplier).roundToPx()
                    )
                }
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Left
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        (-padding * dotOffsetMultiplier).roundToPx(),
                        (padding * dotOffsetMultiplier).roundToPx()
                    )
                }
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )
        // Bottom Right
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset {
                    IntOffset(
                        (padding * dotOffsetMultiplier).roundToPx(),
                        (padding * dotOffsetMultiplier).roundToPx()
                    )
                }
                .size(dotSize)
                .clip(CircleShape)
                .background(dotColor)
        )

        // Loading Indicator overlay
        Box(
            modifier = Modifier
                .alpha(loadingAlpha)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                strokeWidth = 3.dp
            )
        }
    }
}

