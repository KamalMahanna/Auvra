@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.auvra.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.auvra.app.R
import com.auvra.app.data.model.Album
import com.auvra.app.data.model.ArtistDetail
import com.auvra.app.data.model.Playlist
import com.auvra.app.data.model.ShelfLayout
import com.auvra.app.data.model.UiState
import com.auvra.app.ui.components.SongListItem
import com.auvra.app.ui.screens.home.components.HomeTitle
import com.auvra.app.ui.screens.home.components.RecentShelf
import com.auvra.app.ui.screens.home.components.Shelf
import com.auvra.app.ui.screens.home.components.feedMoreSkeleton
import com.auvra.app.ui.screens.home.components.feedSkeleton
import com.auvra.app.ui.screens.home.components.recentlyPlayedSkeleton
import com.auvra.app.ui.screens.player.PlayerViewModel

private const val RECENTS_TITLE = "Recents"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val homeState by viewModel.home.collectAsState()
    val loadingMore by viewModel.homeLoadingMore.collectAsState()
    val recentlyPlayedLoading by viewModel.homeRecentlyPlayedLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val playbackState by playerViewModel.playbackState.collectAsState()
    val isPlaying = playbackState.isPlaying

    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedArtistDetail by viewModel.selectedArtistDetail.collectAsState()

    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val albumSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val artistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState()

    // Infinite scroll continuation trigger via snapshotFlow
    if (homeState is UiState.Success) {
        LaunchedEffect(listState, loadingMore) {
            snapshotFlow {
                val layout = listState.layoutInfo
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
            }.collect { (lastVisible, total) ->
                if (!loadingMore && total > 0 && lastVisible >= total - 3) {
                    viewModel.loadMoreHome()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                item(key = "home_title") {
                    HomeTitle(title = stringResource(R.string.listen_now))
                }

                when (val state = homeState) {
                    is UiState.Loading -> {
                        if (recentlyPlayedLoading) {
                            recentlyPlayedSkeleton()
                        }
                        feedSkeleton(shelves = 3)
                    }

                    is UiState.Error -> {
                        item(key = "home_error") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.loadHome() }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (recentlyPlayedLoading) {
                            recentlyPlayedSkeleton()
                        }
                        state.data.forEachIndexed { index, shelf ->
                            item(key = shelf.title + index) {
                                if (shelf.layout == ShelfLayout.LIST ||
                                    (index == 0 && shelf.title.equals(RECENTS_TITLE, ignoreCase = true))
                                ) {
                                    RecentShelf(
                                        shelf = shelf,
                                        onItemClick = { item ->
                                            viewModel.onItemClick(item, shelf.title, onPlay = onPlaySong)
                                        },
                                        onItemLongPress = { item ->
                                            viewModel.onItemClick(item, shelf.title, onPlay = onPlaySong)
                                        },
                                        currentSongId = currentPlayingSongId,
                                        isPlaying = isPlaying,
                                    )
                                } else {
                                    Shelf(
                                        shelf = shelf,
                                        onItemClick = { item ->
                                            viewModel.onItemClick(item, shelf.title, onPlay = onPlaySong)
                                        },
                                        onItemLongPress = { item ->
                                            viewModel.onItemClick(item, shelf.title, onPlay = onPlaySong)
                                        },
                                        currentSongId = currentPlayingSongId,
                                        isPlaying = isPlaying,
                                    )
                                }
                            }
                        }
                        if (loadingMore) {
                            feedMoreSkeleton()
                        }
                    }
                }
            }
        }

        // Playlist Detail BottomSheet
        selectedPlaylist?.let { playlist ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedPlaylist() },
                sheetState = playlistSheetState,
                containerColor = Color.Transparent,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                PlaylistSheetContent(
                    playlist = playlist,
                    playerViewModel = playerViewModel,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Album Detail BottomSheet
        selectedAlbum?.let { album ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedAlbum() },
                sheetState = albumSheetState,
                containerColor = Color.Transparent,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                AlbumSheetContent(
                    album = album,
                    playerViewModel = playerViewModel,
                    onPlaySong = onPlaySong
                )
            }
        }

        // Artist Detail BottomSheet
        selectedArtistDetail?.let { artistDetail ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedArtist() },
                sheetState = artistSheetState,
                containerColor = Color.Transparent,
                dragHandle = null,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                ArtistSheetContent(
                    artistDetail = artistDetail,
                    playerViewModel = playerViewModel,
                    onPlaySong = onPlaySong
                )
            }
        }
    }
}

@Composable
internal fun PlaylistSheetContent(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    var bottomOverscrollOffset by remember { mutableFloatStateOf(0f) }
    val animatedOverscrollOffset by animateFloatAsState(
        targetValue = bottomOverscrollOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlaylistSongListOverscroll"
    )

    val preventUpwardSheetBounce = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (bottomOverscrollOffset < 0f && available.y > 0) {
                    val newOffset = (bottomOverscrollOffset + available.y).coerceAtMost(0f)
                    val consumedY = newOffset - bottomOverscrollOffset
                    bottomOverscrollOffset = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0) {
                    bottomOverscrollOffset = (bottomOverscrollOffset + available.y * 0.25f).coerceAtLeast(-50f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                bottomOverscrollOffset = 0f
                return if (available.y < 0) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val extraHeight = 1000.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.maxHeight + extraHeight,
                            maxHeight = constraints.maxHeight + extraHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            playlist.highQualityImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(radius = 70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = playlist.mediumQualityImageUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = playlist.description ?: "${playlist.songCount ?: 0} Songs"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val songs = playlist.songs
            if (songs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs found.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 2 else 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(preventUpwardSheetBounce)
                        .graphicsLayer {
                            translationY = animatedOverscrollOffset
                        },
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) {
                            playerViewModel.isSongDownloaded(song)
                        }
                        val isCurrent = currentPlayingSongId == song.id

                        SongListItem(
                            song = song,
                            onClick = {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            },
                            onDownloadClick = { playerViewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isCurrent,
                            downloadProgress = downloadStates[song.id]?.progress,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumSheetContent(
    album: Album,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    var bottomOverscrollOffset by remember { mutableFloatStateOf(0f) }
    val animatedOverscrollOffset by animateFloatAsState(
        targetValue = bottomOverscrollOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "AlbumSongListOverscroll"
    )

    val preventUpwardSheetBounce = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (bottomOverscrollOffset < 0f && available.y > 0) {
                    val newOffset = (bottomOverscrollOffset + available.y).coerceAtMost(0f)
                    val consumedY = newOffset - bottomOverscrollOffset
                    bottomOverscrollOffset = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0) {
                    bottomOverscrollOffset = (bottomOverscrollOffset + available.y * 0.25f).coerceAtLeast(-50f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                bottomOverscrollOffset = 0f
                return if (available.y < 0) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val extraHeight = 1000.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.maxHeight + extraHeight,
                            maxHeight = constraints.maxHeight + extraHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            album.highQualityImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(radius = 70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = album.mediumQualityImageUrl,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = album.description ?: "${album.songCount ?: 0} Songs"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val songs = album.songs
            if (songs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs found.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 2 else 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(preventUpwardSheetBounce)
                        .graphicsLayer {
                            translationY = animatedOverscrollOffset
                        },
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) {
                            playerViewModel.isSongDownloaded(song)
                        }
                        val isPlaying = currentPlayingSongId == song.id

                        SongListItem(
                            song = song,
                            onClick = {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            },
                            onDownloadClick = { playerViewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            downloadProgress = downloadStates[song.id]?.progress,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArtistSheetContent(
    artistDetail: ArtistDetail,
    playerViewModel: PlayerViewModel,
    onPlaySong: () -> Unit
) {
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    var bottomOverscrollOffset by remember { mutableFloatStateOf(0f) }
    val animatedOverscrollOffset by animateFloatAsState(
        targetValue = bottomOverscrollOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ArtistSongListOverscroll"
    )

    val preventUpwardSheetBounce = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (bottomOverscrollOffset < 0f && available.y > 0) {
                    val newOffset = (bottomOverscrollOffset + available.y).coerceAtMost(0f)
                    val consumedY = newOffset - bottomOverscrollOffset
                    bottomOverscrollOffset = newOffset
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0) {
                    bottomOverscrollOffset = (bottomOverscrollOffset + available.y * 0.25f).coerceAtLeast(-50f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                bottomOverscrollOffset = 0f
                return if (available.y < 0) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .layout { measurable, constraints ->
                    val extraHeight = 1000.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minHeight = constraints.maxHeight + extraHeight,
                            maxHeight = constraints.maxHeight + extraHeight
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, 0)
                    }
                }
                .clip(sheetShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            artistDetail.highQualityImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.35f
                            scaleY = 1.35f
                        }
                        .blur(radius = 70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            CircleShape
                        )
                ) {
                    AsyncImage(
                        model = artistDetail.mediumQualityImageUrl,
                        contentDescription = artistDetail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = artistDetail.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val followerCount = artistDetail.followerCount ?: artistDetail.fanCount
                    val subtitle = if (followerCount != null) "${formatCount(followerCount)} Followers" else "Artist"
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val songs = artistDetail.topSongs
            if (songs == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator()
                }
            } else if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs found.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTablet) 2 else 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(preventUpwardSheetBounce)
                        .graphicsLayer {
                            translationY = animatedOverscrollOffset
                        },
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.id },
                        contentType = { _, _ -> "song" }
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) {
                            playerViewModel.isSongDownloaded(song)
                        }
                        val isPlaying = currentPlayingSongId == song.id

                        SongListItem(
                            song = song,
                            onClick = {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            },
                            onDownloadClick = { playerViewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            downloadProgress = downloadStates[song.id]?.progress,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String {
    return if (count >= 1_000_000) {
        "%.1fM".format(count / 1_000_000f)
    } else if (count >= 1_000) {
        "%.1fK".format(count / 1_000f)
    } else {
        count.toString()
    }
}
