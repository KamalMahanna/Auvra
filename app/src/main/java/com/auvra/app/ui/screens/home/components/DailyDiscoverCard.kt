package com.auvra.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auvra.app.R
import com.auvra.app.data.model.DailyDiscoverItem

@Composable
fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages = remember {
        listOf(
            R.string.daily_discover_sounds_like,
            R.string.daily_discover_because_you_listen_to,
            R.string.daily_discover_similar_to,
            R.string.daily_discover_based_on,
            R.string.daily_discover_for_fans_of
        )
    }
    val messageRes = remember(item.song.id) {
        messages[kotlin.math.abs(item.song.id.hashCode()) % messages.size]
    }

    Card(
        modifier = modifier
            .width(320.dp)
            .height(320.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed artwork
            AsyncImage(
                model = item.song.highQualityImageUrl ?: item.song.mediumQualityImageUrl,
                contentDescription = item.song.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top placeholder / spacer
                Box(modifier = Modifier)

                // Bottom: Recommendation title, artists, seed reasoning
                Column {
                    Text(
                        text = item.song.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.song.primaryArtistNames,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.seedTitle.isNotBlank()) {
                        Text(
                            text = stringResource(
                                messageRes,
                                if (item.seedArtist.isNotBlank()) "${item.seedTitle} • ${item.seedArtist}" else item.seedTitle
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

