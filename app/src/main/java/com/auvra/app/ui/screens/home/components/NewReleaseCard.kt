package com.auvra.app.ui.screens.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auvra.app.data.model.Album

@Composable
fun NewReleaseCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = 160.dp
) {
    val artistName = album.artists?.primary?.joinToString(", ") { it.name }
        ?: album.artists?.all?.joinToString(", ") { it.name }.orEmpty()

    Column(
        modifier = modifier
            .width(cardSize)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(cardSize)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = album.highQualityImageUrl ?: album.mediumQualityImageUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = album.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val subtitle = buildString {
            if (artistName.isNotBlank()) append(artistName)
            if (album.year != null) {
                if (isNotEmpty()) append(" • ")
                append(album.year)
            }
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
