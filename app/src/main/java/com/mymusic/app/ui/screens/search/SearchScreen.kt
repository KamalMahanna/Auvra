@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.screens.search

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.text.style.TextOverflow
import com.mymusic.app.ui.components.OfflineEmptyState

import com.mymusic.app.ui.components.SongListItem
import com.mymusic.app.ui.screens.player.PlayerViewModel
import com.mymusic.app.ui.screens.home.PlaylistSheetContent
import com.mymusic.app.ui.screens.home.AlbumSheetContent
import com.mymusic.app.ui.screens.home.ArtistSheetContent

enum class SearchCategory {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    isPlayerExpanded: Boolean,
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadedSongs by playerViewModel.downloadedSongs.collectAsState(initial = emptyList())
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    var selectedCategory by remember { mutableStateOf(SearchCategory.SONGS) }

    val gridState = key(selectedCategory, uiState.query) { rememberLazyGridState() }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isBlank()) {
            selectedCategory = SearchCategory.SONGS
        }
    }

    val availableCategories = remember(uiState.songs, uiState.albums, uiState.artists, uiState.playlists) {
        buildList {
            if (uiState.songs.isNotEmpty()) add(SearchCategory.SONGS)
            if (uiState.albums.isNotEmpty()) add(SearchCategory.ALBUMS)
            if (uiState.artists.isNotEmpty()) add(SearchCategory.ARTISTS)
            if (uiState.playlists.isNotEmpty()) add(SearchCategory.PLAYLISTS)
        }
    }

    LaunchedEffect(availableCategories, uiState.isLoading) {
        if (!uiState.isLoading && availableCategories.isNotEmpty() && selectedCategory !in availableCategories) {
            selectedCategory = availableCategories.first()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search it") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f)
            )
        )

        if (uiState.query.isNotBlank() && !uiState.isLoading && uiState.error == null && availableCategories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                availableCategories.forEach { category ->
                    item {
                        val isSelected = selectedCategory == category
                        val label = when (category) {
                            SearchCategory.SONGS -> "Songs"
                            SearchCategory.ALBUMS -> "Albums"
                            SearchCategory.ARTISTS -> "Artists"
                            SearchCategory.PLAYLISTS -> "Playlists"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else if (uiState.error != null) {
            val isNetworkErr = uiState.error?.contains("internet", ignoreCase = true) == true
            OfflineEmptyState(
                title = if (isNetworkErr) "No internet connection" else "Search failed",
                description = if (isNetworkErr) {
                    "Connect to Wi-Fi or mobile data to search songs, albums, artists, and playlists."
                } else {
                    uiState.error ?: "Unable to complete search. Please try again."
                },
                onRetry = { viewModel.retry() }
            )
        } else {
            key(selectedCategory) {
                LazyVerticalGrid(
                columns = GridCells.Fixed(if (isTablet) 4 else 2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
            ) {
                // 1. Songs Section
                if (selectedCategory == SearchCategory.SONGS && uiState.songs.isNotEmpty()) {
                    itemsIndexed(
                        items = uiState.songs,
                        key = { _, song -> song.id },
                        span = { _, _ -> GridItemSpan(2) } // 2 columns on tablet, 1 column on mobile
                    ) { index, song ->
                        val isDownloading = downloadStates[song.id]?.isDownloading == true
                        val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { playerViewModel.isSongDownloaded(song) }
                        val isPlaying = currentPlayingSongId == song.id
                        
                        val onClick = remember(song) {
                            {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                playerViewModel.playSongWithRecommendations(song)
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

                // 2. Albums Section
                if (selectedCategory == SearchCategory.ALBUMS && uiState.albums.isNotEmpty()) {
                    items(
                        items = uiState.albums,
                        key = { album -> album.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { album ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectAlbum(album.id) }
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = album.mediumQualityImageUrl,
                                contentDescription = album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 3. Artists Section
                if (selectedCategory == SearchCategory.ARTISTS && uiState.artists.isNotEmpty()) {
                    items(
                        items = uiState.artists,
                        key = { artist -> artist.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { artist ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectArtist(artist.id) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                model = artist.mediumQualityImageUrl,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 4. Playlists Section
                if (selectedCategory == SearchCategory.PLAYLISTS && uiState.playlists.isNotEmpty()) {
                    items(
                        items = uiState.playlists,
                        key = { playlist -> playlist.id },
                        span = { _ -> GridItemSpan(1) } // 4 columns on tablet, 2 columns on mobile
                    ) { playlist ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable { viewModel.selectPlaylist(playlist.id) }
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = playlist.mediumQualityImageUrl,
                                contentDescription = playlist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // General empty state
                if (availableCategories.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No results found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

    if (uiState.isArtistDetailLoading || uiState.isPlaylistDetailLoading || uiState.isAlbumDetailLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f))
                .clickable(enabled = false) {}, // Block clicks underneath
            contentAlignment = Alignment.Center
        ) {
            CircularWavyProgressIndicator()
        }
    }

    if (uiState.selectedArtistDetail != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedArtist() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
            dragHandle = null
        ) {
            ArtistSheetContent(
                artistDetail = uiState.selectedArtistDetail!!,
                playerViewModel = playerViewModel,
                onPlaySong = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    viewModel.clearSelectedArtist()
                    onPlaySong()
                }
            )
        }
    }

    if (uiState.selectedPlaylist != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearSelectedPlaylist() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
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
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
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
}
