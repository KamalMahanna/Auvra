package com.mymusic.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun OfflineBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    // Show "Back online" briefly when recovering from offline
    var wasOffline by remember { mutableStateOf(false) }
    var showBackOnline by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOffline = true
            showBackOnline = false
        } else if (wasOffline) {
            showBackOnline = true
            delay(2500)
            showBackOnline = false
            wasOffline = false
        }
    }

    val isBannerVisible = !isOnline || showBackOnline

    AnimatedVisibility(
        visible = isBannerVisible,
        enter = fadeIn(animationSpec = tween(300)) +
                slideInVertically(animationSpec = tween(350, easing = FastOutSlowInEasing)) { -it },
        exit = fadeOut(animationSpec = tween(250)) +
                slideOutVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) { -it },
        modifier = modifier
    ) {
        val backgroundColor = if (isOnline) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
        }

        val borderColor = if (isOnline) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        } else {
            Color(0xFFEF4444).copy(alpha = 0.4f)
        }

        val iconTint = if (isOnline) {
            MaterialTheme.colorScheme.primary
        } else {
            Color(0xFFEF4444)
        }

        val text = if (isOnline) "Back online" else "No internet connection"

        Surface(
            shape = CircleShape,
            color = backgroundColor,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 8.dp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isOnline) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun OfflineEmptyState(
    title: String = "No internet connection",
    description: String = "Connect to Wi-Fi or mobile data to explore and stream music, or listen to your downloaded songs.",
    onRetry: (() -> Unit)? = null,
    onGoToLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Frosted icon container
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onRetry != null) {
                FilledTonalButton(
                    onClick = onRetry,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }

            if (onGoToLibrary != null) {
                OutlinedButton(
                    onClick = onGoToLibrary,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Downloads")
                }
            }
        }
    }
}

