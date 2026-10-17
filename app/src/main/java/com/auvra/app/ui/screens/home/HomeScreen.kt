@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.auvra.app.ui.screens.home

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.auvra.app.R
import com.auvra.app.data.model.ModuleItem
import com.auvra.app.ui.components.SongListItem
import com.auvra.app.ui.screens.home.components.CommunityPlaylistCard
import com.auvra.app.ui.screens.home.components.HomeSongItem
import com.auvra.app.ui.screens.home.components.NavigationTitle
import com.auvra.app.ui.screens.home.components.NewReleaseCard
import com.auvra.app.ui.screens.home.components.RandomizeGridItem
import com.auvra.app.ui.screens.home.components.SpeedDialGridItem
import com.auvra.app.ui.screens.player.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val cardSize = if (isTablet) 160.dp else 125.dp

    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val playbackState by playerViewModel.playbackState.collectAsState()
    val isPlaying = playbackState.isPlaying

    // Hoist sheet states so they survive recompositions and avoid animation jank on open
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val albumSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val artistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.isLoading && uiState.speedDialItems.isEmpty() && uiState.trendingSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            } else if (uiState.error != null && uiState.speedDialItems.isEmpty() && uiState.trendingSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Error: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadHomeFeed(forceRefresh = true) }) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {
                    // 1. SPEED DIAL (Metrolist 3x3 Paged Grid with Randomize Dice button)
                    if (uiState.speedDialItems.isNotEmpty()) {
                        item(key = "home_speed_dial_title") {
                            NavigationTitle(
                                title = stringResource(R.string.speed_dial)
                            )
                        }

                        item(key = "home_speed_dial_grid") {
                            val targetItemSize = 160.dp
                            val availableWidth = configuration.screenWidthDp.dp - 32.dp
                            val columns = (availableWidth / targetItemSize).toInt().coerceAtLeast(3)
                            val rows = if (columns >= 6) 1 else if (columns >= 4) 2 else 3
                            val itemsPerPage = columns * rows
                            val itemWidth = availableWidth / columns

                            val pagerState = rememberPagerState(
                                pageCount = { (uiState.speedDialItems.size + itemsPerPage - 1) / itemsPerPage }
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                HorizontalPager(
                                    state = pagerState,
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    pageSpacing = 16.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(itemWidth * rows)
                                ) { page ->
                                    val pageStartIndex = page * itemsPerPage
                                    val pageItems = uiState.speedDialItems.drop(pageStartIndex).take(itemsPerPage)

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        for (row in 0 until rows) {
                                            Row(modifier = Modifier.fillMaxWidth()) {
                                                for (col in 0 until columns) {
                                                    val itemIndex = row * columns + col
                                                    val isRandomizeSlot = (page == 0 && itemIndex == itemsPerPage - 1)

                                                    if (isRandomizeSlot) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(itemWidth)
                                                                .height(itemWidth)
                                                                .padding(4.dp)
                                                        ) {
                                                            RandomizeGridItem(
                                                                isLoading = uiState.isRandomizing,
                                                                onClick = {
                                                                    viewModel.getRandomItem(onPlay = onPlaySong)
                                                                }
                                                            )
                                                        }
                                                    } else if (itemIndex < pageItems.size) {
                                                        val item = pageItems[itemIndex]
                                                        Box(
                                                            modifier = Modifier
                                                                .width(itemWidth)
                                                                .height(itemWidth)
                                                                .padding(4.dp)
                                                        ) {
                                                            SpeedDialGridItem(
                                                                item = item,
                                                                isActive = item.id == currentPlayingSongId,
                                                                isPlaying = isPlaying && item.id == currentPlayingSongId,
                                                                onClick = {
                                                                    if (item.type.lowercase().trim() == "song") {
                                                                        onPlaySong()
                                                                    }
                                                                    viewModel.playModuleItem(item)
                                                                },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.width(itemWidth))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (pagerState.pageCount > 1) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        repeat(pagerState.pageCount) { iteration ->
                                            val color = if (pagerState.currentPage == iteration) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .padding(3.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .size(6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(36.dp))
                            }
                        }
                    }

                    // 2. TRENDING SONGS FOR YOU (4-row horizontal scrolling grid with Play All)
                    if (uiState.trendingSongs.isNotEmpty()) {
                        item(key = "home_trending_songs_title") {
                            NavigationTitle(
                                title = stringResource(R.string.trending_songs_for_you),
                                onPlayAllClick = {
                                    viewModel.playAllSongs(uiState.trendingSongs)
                                    onPlaySong()
                                }
                            )
                        }

                        item(key = "home_trending_songs_grid") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val rows = minOf(4, uiState.trendingSongs.size).coerceAtLeast(1)
                                val gridHeight = 64.dp * rows
                                LazyHorizontalGrid(
                                    rows = GridCells.Fixed(rows),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(gridHeight),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        items = uiState.trendingSongs,
                                        key = { song -> "trending_${song.id}" }
                                    ) { song ->
                                        val moduleItem = ModuleItem(
                                            id = song.id,
                                            name = song.name,
                                            subtitle = song.primaryArtistNames,
                                            type = "song",
                                            image = song.image,
                                            url = song.url
                                        )
                                        HomeSongItem(
                                            item = moduleItem,
                                            isActive = song.id == currentPlayingSongId,
                                            isPlaying = isPlaying && song.id == currentPlayingSongId,
                                            onClick = {
                                                viewModel.playSong(song)
                                                onPlaySong()
                                            },
                                            modifier = Modifier.width(if (isTablet) 360.dp else 290.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(36.dp))
                            }
                        }
                    }

                    // 3. TRENDING COMMUNITY PLAYLIST (320x420 cards with 2x2 collage, preview songs, and actions)
                    if (uiState.communityPlaylists.isNotEmpty()) {
                        item(key = "home_community_title") {
                            NavigationTitle(
                                title = stringResource(R.string.trending_community_playlists)
                            )
                        }

                        item(key = "home_community_row") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = uiState.communityPlaylists,
                                        key = { item -> "comm_playlist_${item.playlist.id}" }
                                    ) { item ->
                                        CommunityPlaylistCard(
                                            item = item,
                                            onClick = {
                                                viewModel.playModuleItem(
                                                    ModuleItem(
                                                        id = item.playlist.id,
                                                        name = item.playlist.name,
                                                        type = "playlist",
                                                        image = item.playlist.image
                                                    )
                                                )
                                            },
                                            onSongClick = { song ->
                                                viewModel.playSong(song)
                                                onPlaySong()
                                            },
                                            onPlayAllClick = {
                                                if (item.previewSongs.isNotEmpty()) {
                                                    viewModel.playSongFromList(item.previewSongs, 0)
                                                    onPlaySong()
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(36.dp))
                            }
                        }
                    }

                    // 4. NEW RELEASES (Horizontal carousel of new release albums)
                    if (uiState.newReleases.isNotEmpty()) {
                        item(key = "home_new_releases_title") {
                            NavigationTitle(
                                title = stringResource(R.string.new_releases)
                            )
                        }

                        item(key = "home_new_releases_row") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(
                                        items = uiState.newReleases,
                                        key = { album -> "new_rel_${album.id}" }
                                    ) { album ->
                                        NewReleaseCard(
                                            album = album,
                                            onClick = {
                                                viewModel.openAlbum(album)
                                            },
                                            cardSize = if (isTablet) 180.dp else 160.dp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(36.dp))
                            }
                        }
                    }
                }
            }
        }



        if (uiState.selectedPlaylist != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedPlaylist() },
                sheetState = playlistSheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                PlaylistSheetContent(
                    playlist = uiState.selectedPlaylist!!,
                    playerViewModel = playerViewModel,
                    onPlaySong = {
                        viewModel.clearSelectedPlaylist()
                        onPlaySong()
                    }
                )
            }
        }

        if (uiState.selectedAlbum != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedAlbum() },
                sheetState = albumSheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                AlbumSheetContent(
                    album = uiState.selectedAlbum!!,
                    playerViewModel = playerViewModel,
                    onPlaySong = {
                        viewModel.clearSelectedAlbum()
                        onPlaySong()
                    }
                )
            }
        }

        if (uiState.selectedArtistDetail != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedArtist() },
                sheetState = artistSheetState,
                containerColor = Color.Transparent,
                scrimColor = Color.Black.copy(alpha = 0.6f),
                dragHandle = null
            ) {
                ArtistSheetContent(
                    artistDetail = uiState.selectedArtistDetail!!,
                    playerViewModel = playerViewModel,
                    onPlaySong = {
                        viewModel.clearSelectedArtist()
                        onPlaySong()
                    }
                )
            }
        }
    }
}

/**
 * Extracted playlist sheet content into a separate composable so that
 * playbackState/downloadState collection is scoped here — not the main HomeScreen.
 * This prevents the entire home LazyColumn from recomposing every 500ms.
 */
@Composable
internal fun PlaylistSheetContent(
    playlist: com.auvra.app.data.model.Playlist,
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
                // available.y < 0 is unconsumed upward scroll (swiping up at bottom of list).
                // Consume it and apply elastic bounce offset to song list tiles,
                // while preventing ModalBottomSheet drag handler from pushing outer sheet upward.
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
        // Extended Background Container: Blurred Image + Glassmorphism Scrim Gradient
        // Measures 1000dp taller than the sheet layout so that the blurred artwork and gradient overlay
        // extend seamlessly past the bottom edge when the sheet is stretched or bounced during fast scrolling.
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
            // Blurred Playlist Image Background
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

            // Glassmorphism Dark Gradient Scrim Overlay
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

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag Handle Pill
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
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(songs, index) {
                            {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            }
                        }
                        val onDownloadClick = remember(song) {
                            { playerViewModel.downloadSong(song) }
                        }

                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onDownloadClick = onDownloadClick,
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

/**
 * Extracted album sheet content — same rationale as PlaylistSheetContent.
 */
@Composable
internal fun AlbumSheetContent(
    album: com.auvra.app.data.model.Album,
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
        // Extended Background Container: Blurred Image + Glassmorphism Scrim Gradient
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
            // Blurred Album Image Background
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

            // Glassmorphism Dark Gradient Scrim Overlay
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

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag Handle Pill
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
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(songs, index) {
                            {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            }
                        }
                        val onDownloadClick = remember(song) {
                            { playerViewModel.downloadSong(song) }
                        }

                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onDownloadClick = onDownloadClick,
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

/**
 * Extracted artist sheet content — same rationale as PlaylistSheetContent and AlbumSheetContent.
 */
@Composable
internal fun ArtistSheetContent(
    artistDetail: com.auvra.app.data.model.ArtistDetail,
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
        // Extended Background Container: Blurred Image + Glassmorphism Scrim Gradient
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
            // Blurred Artist Image Background
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

            // Glassmorphism Dark Gradient Scrim Overlay
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

        // Foreground Content
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Drag Handle Pill
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
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id

                        val onClick = remember(songs, index) {
                            {
                                playerViewModel.playSongFromList(songs, index)
                                onPlaySong()
                            }
                        }
                        val onDownloadClick = remember(song) {
                            { playerViewModel.downloadSong(song) }
                        }

                        SongListItem(
                            song = song,
                            onClick = onClick,
                            onDownloadClick = onDownloadClick,
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

internal fun formatCount(count: Int): String {
    return if (count >= 1_000_000) {
        "%.1fM".format(count / 1_000_000f)
    } else if (count >= 1_000) {
        "%.1fK".format(count / 1_000f)
    } else {
        count.toString()
    }
}
