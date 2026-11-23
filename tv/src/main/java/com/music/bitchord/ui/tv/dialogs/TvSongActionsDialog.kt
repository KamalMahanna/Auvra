package com.music.bitchord.ui.tv.dialogs

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.SleepTimer
import com.music.bitchord.playback.beginRadioQueue
import com.music.bitchord.playback.commitRadioQueue
import com.music.bitchord.playback.playSongs
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.ui.MainViewModel
import com.music.bitchord.ui.tv.components.TvArtwork
import com.music.bitchord.ui.tv.components.TvDialog
import com.music.bitchord.ui.tv.components.TvListRow
import com.music.bitchord.ui.tv.components.tvInitialFocus
import kotlinx.coroutines.launch

/**
 * A song's actions from anywhere outside the player: works out whether it's
 * liked and where its album and artist pages are, then shows the menu.
 */
@Composable
fun TvSongMenu(
    song: Song,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onNavigateToDetail: (browseId: String, title: String, subtitle: String, thumbnailUrl: String?, type: BrowseType) -> Unit,
    onDismiss: () -> Unit,
) {
    val overrides by LikeState.overrides.collectAsState()
    val isLiked = overrides[song.videoId] == LikeStatus.LIKE
    TvSongActionMenuDialog(
        song = song,
        isLiked = isLiked,
        viewModel = viewModel,
        mediaController = mediaController,
        onToggleLike = { viewModel.toggleLike(song.videoId) },
        onOpenAlbum = song.albumId?.let { id ->
            { _: String -> onNavigateToDetail(id, song.albumName.orEmpty(), song.artist, song.thumbnailUrl, BrowseType.ALBUM) }
        },
        onOpenArtist = song.artistId?.let { id ->
            { _: String -> onNavigateToDetail(id, song.artist, "", null, BrowseType.ARTIST) }
        },
        onDismiss = onDismiss,
    )
}

/**
 * The tvOS action sheet for a track: the song itself at the top, then a short
 * list of plain actions — one line each, a glyph on the right, the white
 * platter on whichever has focus.
 */
@Composable
fun TvSongActionMenuDialog(
    song: Song,
    isLiked: Boolean,
    viewModel: MainViewModel,
    mediaController: MediaController?,
    onToggleLike: () -> Unit,
    onOpenAlbum: ((String) -> Unit)? = null,
    onOpenArtist: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf(SongSheet.Actions) }
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    val sleepAfterTrack by SleepTimer.afterTrack.collectAsState()
    val nerdStats by AppSettings.showNerdStats.collectAsState()

    when (sheet) {
        SongSheet.Sleep -> {
            TvSleepTimerDialog(onDismiss = onDismiss)
            return
        }
        SongSheet.Actions -> Unit
    }

    TvDialog(title = song.title, message = song.artist.ifBlank { null }, onDismissRequest = onDismiss) {
        TvArtwork(
            url = song.thumbnailUrl,
            px = 320,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(77.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 288.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item {
                TvListRow(
                    title = "Play Next",
                    trailingIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = Modifier.tvInitialFocus(),
                    onClick = {
                        mediaController?.let { it.addMediaItem(it.currentMediaItemIndex + 1, song.toMediaItem()) }
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = "Play Last",
                    trailingIcon = Icons.Rounded.PlaylistPlay,
                    onClick = {
                        mediaController?.addMediaItem(song.toMediaItem())
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = "Start Station",
                    trailingIcon = Icons.Rounded.Radio,
                    onClick = {
                        val controller = mediaController
                        if (controller != null) scope.launch { controller.startStation(song) }
                        onDismiss()
                    },
                )
            }
            item {
                TvListRow(
                    title = if (isLiked) "Unlove" else "Love",
                    trailingIcon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    onClick = {
                        onToggleLike()
                        onDismiss()
                    },
                )
            }
            if (onOpenAlbum != null) {
                item {
                    TvListRow(
                        title = "Go to Album",
                        trailingIcon = Icons.Rounded.Album,
                        onClick = {
                            onDismiss()
                            onOpenAlbum(song.albumId.orEmpty())
                        },
                    )
                }
            }
            if (onOpenArtist != null) {
                item {
                    TvListRow(
                        title = "Go to Artist",
                        trailingIcon = Icons.Rounded.Person,
                        onClick = {
                            onDismiss()
                            onOpenArtist(song.artistId.orEmpty())
                        },
                    )
                }
            }
            item {
                TvListRow(
                    title = "Sleep Timer",
                    value = if (sleepDeadline != null || sleepAfterTrack) "On" else "Off",
                    trailingIcon = Icons.Rounded.Timer,
                    onClick = { sheet = SongSheet.Sleep },
                )
            }
            item {
                TvListRow(
                    title = "Stats for Nerds",
                    value = if (nerdStats) "On" else "Off",
                    trailingIcon = Icons.Rounded.Info,
                    onClick = { AppSettings.setShowNerdStats(!nerdStats) },
                )
            }
        }
    }
}

private enum class SongSheet { Actions, Sleep }

/**
 * A station seeded from [song], as the phone builds one: YouTube's radio for
 * it, with the song itself first. If it's already the one playing, it keeps
 * playing and only the rest of the queue is replaced.
 */
private suspend fun MediaController.startStation(song: Song) {
    val related = YtMusicRepository.radio(song.videoId).getOrNull()
        ?.filterNot { it.videoId == song.videoId }
        ?: return
    beginRadioQueue()
    val index = currentMediaItemIndex
    if (index >= 0 && currentMediaItem?.mediaId == song.videoId) {
        if (index + 1 < mediaItemCount) removeMediaItems(index + 1, mediaItemCount)
        if (index > 0) removeMediaItems(0, index)
        addMediaItems(1, related.map { it.toMediaItem() })
    } else {
        playSongs(listOf(song) + related, 0)
    }
    commitRadioQueue()
}

/** Stop playback after a while, or at the end of this song. */
@Composable
fun TvSleepTimerDialog(onDismiss: () -> Unit) {
    val deadline by SleepTimer.deadline.collectAsState()
    val afterTrack by SleepTimer.afterTrack.collectAsState()
    val active = deadline != null || afterTrack

    TvDialog(title = "Sleep Timer", message = "Stop playing music after", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(15, 30, 45, 60).forEachIndexed { index, minutes ->
                TvListRow(
                    title = "$minutes Minutes",
                    modifier = if (index == 0) Modifier.tvInitialFocus() else Modifier,
                    onClick = {
                        SleepTimer.start(minutes)
                        onDismiss()
                    },
                )
            }
            TvListRow(
                title = "End of Current Song",
                trailingIcon = if (afterTrack) Icons.Rounded.Check else null,
                onClick = {
                    SleepTimer.startAfterTrack()
                    onDismiss()
                },
            )
            if (active) {
                TvListRow(
                    title = "Turn Off Timer",
                    destructive = true,
                    onClick = {
                        SleepTimer.cancel()
                        onDismiss()
                    },
                )
            }
        }
    }
}
