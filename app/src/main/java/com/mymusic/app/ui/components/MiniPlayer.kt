@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mymusic.app.ui.screens.player.PlayerViewModel
import kotlin.math.abs

@Composable
fun MiniPlayer(
    onPlayerClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()

    val currentSong = song ?: return
    val haptics = LocalHapticFeedback.current

    var totalDragX by remember { mutableFloatStateOf(0f) }
    var totalDragY by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 80f

    val miniBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Box(
        modifier = modifier
            .width(240.dp)
            .padding(vertical = 8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
            .border(BorderStroke(1.dp, miniBorderColor), CircleShape)
            .clickable { onPlayerClick() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        val absX = abs(totalDragX)
                        val absY = abs(totalDragY)
                        if (absX > absY) {
                            if (totalDragX < -swipeThreshold) {
                                // Swiping left -> plays next song
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playNext()
                            } else if (totalDragX > swipeThreshold) {
                                // Swiping right -> plays previous song
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playPrevious()
                            }
                        } else if (totalDragY < -swipeThreshold) {
                            // Swiping up -> open full player
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayerClick()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            }
    ) {
        // Progress background overlay
        MiniPlayerProgress(
            viewModel = viewModel,
            modifier = Modifier.matchParentSize()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = currentSong.mediumQualityImageUrl,
                    contentDescription = "Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = currentSong.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.primaryArtistNames,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            CircularPlayPauseButton(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onPlayPauseClick = { viewModel.togglePlayPause() }
            )
        }
    }
}

@Composable
private fun CircularPlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlayPauseScale"
    )

    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        FilledIconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayPauseClick()
            },
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                },
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            interactionSource = interactionSource
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgress(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val rawProgress = if (playbackState.duration > 0) {
        playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
    } else 0f

    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
        label = "MiniPlayerProgress"
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
        )
    }
}

