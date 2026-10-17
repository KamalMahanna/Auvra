package com.auvra.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.auvra.app.R
import com.auvra.app.data.model.CommunityPlaylistItem
import com.auvra.app.data.model.Song

@Composable
fun CommunityPlaylistCard(
    item: CommunityPlaylistItem,
    onClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .width(320.dp)
            .height(420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(28.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Section: 2x2 Collage + Title & Author
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2x2 Collage (100x100 dp)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    val previewThumbs = item.previewSongs.mapNotNull { it.mediumQualityImageUrl }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = previewThumbs.getOrNull(0) ?: item.playlist.mediumQualityImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                            AsyncImage(
                                model = previewThumbs.getOrNull(1) ?: item.playlist.mediumQualityImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                        }
                        Row(modifier = Modifier.weight(1f)) {
                            AsyncImage(
                                model = previewThumbs.getOrNull(2) ?: item.playlist.mediumQualityImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                            AsyncImage(
                                model = previewThumbs.getOrNull(3) ?: item.playlist.mediumQualityImageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                        }
                    }
                }

                // Playlist Name & Description/Author
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.playlist.description ?: "${item.playlist.songCount ?: item.previewSongs.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Middle Section: 3 Preview Songs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.previewSongs.take(3).forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSongClick(song) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = song.mediumQualityImageUrl,
                            contentDescription = song.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.primaryArtistNames,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Bottom Action Row: Play
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayAllClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_widget_play),
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

