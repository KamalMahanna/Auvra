package com.mymusic.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.mymusic.app.ui.screens.player.PlayerViewModel
import com.mymusic.app.ui.components.SongListItem
import com.mymusic.app.ui.screens.update.AppUpdateViewModel

@Composable
fun LibraryScreen(
    isPlayerExpanded: Boolean = false,
    onPlaySong: () -> Unit,
    bottomPadding: Dp,
    viewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val songs by viewModel.downloadedSongs.collectAsState()
    val isUpdating by viewModel.isUpdating.collectAsState()
    val isCheckingUpdate by updateViewModel.isChecking.collectAsState()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val currentPlayingSongId by playerViewModel.currentSongId.collectAsState(initial = null)
    val isOnline by playerViewModel.isOnline.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val songList = remember(songs) { viewModel.getAsSongList() }
    val filteredSongList = remember(songList, searchQuery) {
        if (searchQuery.isBlank()) {
            songList
        } else {
            songList.filter { song ->
                song.name.contains(searchQuery, ignoreCase = true) ||
                song.artists.primary.any { artist -> artist.name.contains(searchQuery, ignoreCase = true) }
            }
        }
    }
    
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isTablet) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            val focusRequester = remember { FocusRequester() }
            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search downloads...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            isSearching = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close search"
                            )
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
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (!isOnline) {
                                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                                updateViewModel.checkForUpdate(force = true)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = "Check for updates",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search downloads",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (isUpdating || isCheckingUpdate) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
        
        if (filteredSongList.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No downloaded songs yet." else "No matching downloads found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(
                items = filteredSongList,
                key = { _, song -> song.id }
            ) { index, song ->
                val isPlaying = currentPlayingSongId == song.id
                SongListItem(
                    song = song,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        val originalIndex = songList.indexOfFirst { it.id == song.id }
                        playerViewModel.playSongFromList(songList, if (originalIndex != -1) originalIndex else index)
                        onPlaySong()
                    },
                    onDownloadClick = {},
                    isDownloaded = true,
                    isDownloading = false,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(onClick = {
                            songs.find { it.id == song.id }?.let { viewModel.deleteSong(it) }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

