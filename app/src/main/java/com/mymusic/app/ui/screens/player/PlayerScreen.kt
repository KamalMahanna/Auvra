@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.screens.player

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.CircleShape
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.mymusic.app.data.model.Song
import com.mymusic.app.ui.components.SongListItem
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val song = playbackState.currentSong ?: return
    val currentIndex by viewModel.queueIndex.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    var showQueue by remember { mutableStateOf(false) }

    val queueListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val queue by viewModel.queue.collectAsState()

    val density = LocalDensity.current
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val playerGestureModifier = if (!isTablet) {
        if (!showQueue) {
            Modifier.pointerInput(currentIndex) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    },
                    onDragEnd = {
                        val swipeThresholdPx = with(density) { 30.dp.toPx() }
                        if (kotlin.math.abs(dragOffsetX) > kotlin.math.abs(dragOffsetY)) {
                            // Horizontal Swipe
                            if (dragOffsetX < -swipeThresholdPx) {
                                // Swipe Left -> Play Next
                                viewModel.playNext()
                            } else if (dragOffsetX > swipeThresholdPx) {
                                // Swipe Right -> Play Previous or Replay from start
                                if (currentIndex == 0) {
                                    viewModel.seekTo(0)
                                } else {
                                    viewModel.playPrevious()
                                }
                            }
                        } else {
                            // Vertical Swipe
                            if (dragOffsetY < -swipeThresholdPx) {
                                // Swipe Up -> Open Play Queue
                                showQueue = true
                            } else if (dragOffsetY > swipeThresholdPx) {
                                // Swipe Down -> Collapse Player
                                onCollapse()
                            }
                        }
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            }
        } else Modifier
    } else {
        // On tablet: Only support swipe down to collapse, and swipe left/right to skip songs
        Modifier.pointerInput(currentIndex) {
            detectDragGestures(
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetX += dragAmount.x
                    dragOffsetY += dragAmount.y
                },
                onDragEnd = {
                    val swipeThresholdPx = with(density) { 30.dp.toPx() }
                    if (kotlin.math.abs(dragOffsetX) > kotlin.math.abs(dragOffsetY)) {
                        if (dragOffsetX < -swipeThresholdPx) {
                            viewModel.playNext()
                        } else if (dragOffsetX > swipeThresholdPx) {
                            if (currentIndex == 0) {
                                viewModel.seekTo(0)
                            } else {
                                viewModel.playPrevious()
                            }
                        }
                    } else {
                        if (dragOffsetY > swipeThresholdPx) {
                            onCollapse()
                        }
                    }
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                },
                onDragCancel = {
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {} // Consume click events to prevent touch propagation to search/nav UI underneath
            )
            .then(playerGestureModifier)
    ) {
        // Fullscreen Blurred Media Art Background
        AnimatedContent(
            targetState = song.highQualityImageUrl,
            transitionSpec = {
                fadeIn(animationSpec = tween(700)) togetherWith fadeOut(animationSpec = tween(700))
            },
            label = "BlurredMediaArtBackground",
            modifier = Modifier.fillMaxSize()
        ) { imageUrl ->
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

        // Ambient Dark Scrim Overlay for Contrast & Artwork Colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isTablet) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left pane: Now playing content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabletNowPlayingContent(
                            song = song,
                            playbackState = playbackState,
                            currentIndex = currentIndex,
                            viewModel = viewModel,
                            onQueueClick = {
                                coroutineScope.launch {
                                    if (currentIndex in queue.indices) {
                                        queueListState.animateScrollToItem(currentIndex)
                                    }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    VerticalDivider(
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.width(32.dp))

                    // Right pane: QueueView content
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Play Queue",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        QueueView(
                            viewModel = viewModel,
                            onSwipeDown = {},
                            listState = queueListState
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = showQueue,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + 
                             fadeIn(animationSpec = tween(300)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } + 
                                              fadeOut(animationSpec = tween(300)))
                        } else {
                            (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } + 
                             fadeIn(animationSpec = tween(300)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } + 
                                              fadeOut(animationSpec = tween(300)))
                        }
                    },
                    label = "QueueTransition",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) { isQueueVisible ->
                    if (isQueueVisible) {
                        // Queue View
                        QueueView(
                            viewModel = viewModel,
                            onSwipeDown = { showQueue = false },
                            onBackClick = { showQueue = false }
                        )
                    } else {
                        // Main Player View
                        var prevIndex by remember { mutableIntStateOf(currentIndex) }
                        val isNext = currentIndex >= prevIndex

                        LaunchedEffect(currentIndex) {
                            prevIndex = currentIndex
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Only Artwork & Info transitions horizontally
                            AnimatedContent(
                                targetState = song,
                                transitionSpec = {
                                    if (isNext) {
                                        (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width } + 
                                         fadeIn(animationSpec = tween(250)))
                                            .togetherWith(
                                                slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width } + 
                                                fadeOut(animationSpec = tween(250))
                                            )
                                    } else {
                                        (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width } + 
                                         fadeIn(animationSpec = tween(250)))
                                            .togetherWith(
                                                slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width } + 
                                                fadeOut(animationSpec = tween(250))
                                            )
                                    }
                                },
                                label = "SongChangeTransition",
                                modifier = Modifier.weight(1.0f)
                            ) { currentSong ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Spacer(modifier = Modifier.weight(1f))

                                    // Large Artwork Card with Floating Shadow
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .shadow(
                                                elevation = 32.dp,
                                                shape = RoundedCornerShape(24.dp),
                                                clip = false,
                                                spotColor = Color.Black.copy(alpha = 0.75f),
                                                ambientColor = Color.Black.copy(alpha = 0.5f)
                                            )
                                            .clip(RoundedCornerShape(24.dp))
                                    ) {
                                        AsyncImage(
                                            model = currentSong.highQualityImageUrl,
                                            contentDescription = "Artwork",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Song Title & Artist Info
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = currentSong.name,
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        spacing = MarqueeSpacing(48.dp)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = currentSong.primaryArtistNames,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Progress Slider (Transitions with title)
                                    val progress = if (playbackState.duration > 0) {
                                        playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
                                    } else 0f

                                    var sliderPosition by remember { mutableStateOf<Float?>(null) }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    val sliderColors = SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        thumbColor = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Current position time
                                        val displayPositionMillis = if (sliderPosition != null) {
                                            (sliderPosition!! * playbackState.duration).toLong()
                                        } else {
                                            playbackState.currentPosition
                                        }
                                        Text(
                                            text = formatMillis(displayPositionMillis),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Slider(
                                            value = sliderPosition ?: progress,
                                            onValueChange = { sliderPosition = it },
                                            onValueChangeFinished = {
                                                sliderPosition?.let { pos ->
                                                    viewModel.seekTo((pos * playbackState.duration).toLong())
                                                }
                                                sliderPosition = null
                                            },
                                            colors = sliderColors,
                                            interactionSource = interactionSource,
                                            track = { sliderState ->
                                                val density = LocalDensity.current
                                                val strokeWidthPx = with(density) { 6.dp.toPx() }
                                                val trackStrokeWidthPx = with(density) { 6.dp.toPx() }
                                                LinearWavyProgressIndicator(
                                                    progress = { sliderState.coercedValueAsFraction },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(18.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    amplitude = { if (playbackState.isPlaying) 0.6f else 0.0f },
                                                    wavelength = 40.dp,
                                                    waveSpeed = 16.dp,
                                                    stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                                                    trackStroke = Stroke(width = trackStrokeWidthPx, cap = StrokeCap.Round)
                                                )
                                            },
                                            thumb = {},
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Total duration time
                                        Text(
                                            text = formatMillis(playbackState.duration),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(0.05f))

                            // Controls Row (Static)
                            PlaybackControls(
                                isPlaying = playbackState.isPlaying,
                                isBuffering = playbackState.isBuffering,
                                onPreviousClick = { viewModel.playPrevious() },
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onNextClick = { viewModel.playNext() }
                            )

                            Spacer(modifier = Modifier.weight(0.05f))

                            BottomControlsRow(
                                song = song,
                                viewModel = viewModel,
                                isShuffleEnabled = isShuffleEnabled,
                                onShuffleClick = { viewModel.toggleShuffle() },
                                onQueueClick = { showQueue = true }
                            )

                            Spacer(modifier = Modifier.weight(0.1f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueView(
    viewModel: PlayerViewModel,
    onSwipeDown: () -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.queueIndex.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState(initial = emptyList())
    val downloadStates by viewModel.downloadStates.collectAsState()

    LaunchedEffect(currentIndex, queue) {
        if (queue.isNotEmpty() && currentIndex in queue.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    // Detect if we are scrolled to the top
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 30.dp.toPx() }

    // Intercept scroll-down drags when list is scrolled to the top
    val nestedScrollConnection = remember(isAtTop) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && isAtTop) {
                    dragOffsetY += available.y
                    if (dragOffsetY > swipeThresholdPx) {
                        onSwipeDown()
                        dragOffsetY = 0f
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y < 0) {
                    dragOffsetY = 0f
                }
                return Offset.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        if (onBackClick != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backInteractionSource = remember { MutableInteractionSource() }
                val isBackPressed by backInteractionSource.collectIsPressedAsState()
                val backScale by animateFloatAsState(
                    targetValue = if (isBackPressed) 0.85f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "BackScale"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = backScale
                            scaleY = backScale
                        }
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = backInteractionSource,
                            indication = null,
                            onClick = onBackClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Play Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { dragOffsetY = 0f },
                            onDragEnd = { dragOffsetY = 0f },
                            onDragCancel = { dragOffsetY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 0) {
                                    dragOffsetY += dragAmount
                                    if (dragOffsetY > swipeThresholdPx) {
                                        onSwipeDown()
                                        dragOffsetY = 0f
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Queue is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${song.id}_$index" }
                ) { index, song ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.removeFromQueue(index)
                                true
                            } else false
                        }
                    )

                    val isDownloading = downloadStates[song.id]?.isDownloading == true
                    val isDownloaded = remember(downloadedSongs, downloadStates[song.id]?.isComplete, song.id) { viewModel.isSongDownloaded(song) }
                    val isPlaying = index == currentIndex

                    val itemShape = if (isPlaying) CircleShape else RoundedCornerShape(12.dp)

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = false,
                        backgroundContent = {
                            val color = when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clip(itemShape)
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        SongListItem(
                            song = song,
                            onClick = { viewModel.playQueueIndex(index) },
                            onDownloadClick = { viewModel.downloadSong(song) },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPlaying = isPlaying,
                            downloadProgress = downloadStates[song.id]?.progress
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletNowPlayingContent(
    song: Song,
    playbackState: com.mymusic.app.player.PlaybackState,
    currentIndex: Int,
    viewModel: PlayerViewModel,
    onQueueClick: () -> Unit
) {
    var prevIndex by remember { mutableIntStateOf(currentIndex) }
    val isNext = currentIndex >= prevIndex

    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()

    LaunchedEffect(currentIndex) {
        prevIndex = currentIndex
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.weight(0.1f))

        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.75f),
                    ambientColor = Color.Black.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            AsyncImage(
                model = song.highQualityImageUrl,
                contentDescription = "Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.weight(0.1f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(48.dp)
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.primaryArtistNames,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val progress = if (playbackState.duration > 0) {
            playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()
        } else 0f

        var sliderPosition by remember { mutableStateOf<Float?>(null) }
        val interactionSource = remember { MutableInteractionSource() }
        val sliderColors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            thumbColor = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current position time
            val displayPositionMillis = if (sliderPosition != null) {
                (sliderPosition!! * playbackState.duration).toLong()
            } else {
                playbackState.currentPosition
            }
            Text(
                text = formatMillis(displayPositionMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Slider(
                value = sliderPosition ?: progress,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    sliderPosition?.let { pos ->
                        viewModel.seekTo((pos * playbackState.duration).toLong())
                    }
                    sliderPosition = null
                },
                colors = sliderColors,
                interactionSource = interactionSource,
                track = { sliderState ->
                    val density = LocalDensity.current
                    val strokeWidthPx = with(density) { 6.dp.toPx() }
                    val trackStrokeWidthPx = with(density) { 6.dp.toPx() }
                    LinearWavyProgressIndicator(
                        progress = { sliderState.coercedValueAsFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        amplitude = { if (playbackState.isPlaying) 0.6f else 0.0f },
                        wavelength = 40.dp,
                        waveSpeed = 16.dp,
                        stroke = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                        trackStroke = Stroke(width = trackStrokeWidthPx, cap = StrokeCap.Round)
                    )
                },
                thumb = {},
                modifier = Modifier.weight(1f)
            )

            // Total duration time
            Text(
                text = formatMillis(playbackState.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        PlaybackControls(
            isPlaying = playbackState.isPlaying,
            isBuffering = playbackState.isBuffering,
            onPreviousClick = { viewModel.playPrevious() },
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onNextClick = { viewModel.playNext() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        BottomControlsRow(
            song = song,
            viewModel = viewModel,
            isShuffleEnabled = isShuffleEnabled,
            onShuffleClick = { viewModel.toggleShuffle() },
            onQueueClick = onQueueClick
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    val prevInteractionSource = remember { MutableInteractionSource() }
    val isPrevPressed by prevInteractionSource.collectIsPressedAsState()
    val prevScale by animateFloatAsState(
        targetValue = if (isPrevPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PrevScale"
    )

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlayScale"
    )

    val nextInteractionSource = remember { MutableInteractionSource() }
    val isNextPressed by nextInteractionSource.collectIsPressedAsState()
    val nextScale by animateFloatAsState(
        targetValue = if (isNextPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NextScale"
    )

    val glassBorderColor = Color.White.copy(alpha = 0.20f)
    val buttonGlassBg = Color.White.copy(alpha = 0.12f)
    val playGlassBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val playBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Glassmorphic Previous Button
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 46.dp)
                .graphicsLayer {
                    scaleX = prevScale
                    scaleY = prevScale
                }
                .clip(CircleShape)
                .background(buttonGlassBg)
                .border(BorderStroke(1.dp, glassBorderColor), CircleShape)
                .clickable(
                    interactionSource = prevInteractionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPreviousClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // Glassmorphic Play/Pause Button
        IconButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayPauseClick()
            },
            modifier = Modifier
                .size(74.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .clip(CircleShape)
                .background(playGlassBg)
                .border(BorderStroke(1.5.dp, playBorderColor), CircleShape),
            interactionSource = playInteractionSource
        ) {
            if (isBuffering) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Glassmorphic Next Button
        Box(
            modifier = Modifier
                .size(width = 58.dp, height = 46.dp)
                .graphicsLayer {
                    scaleX = nextScale
                    scaleY = nextScale
                }
                .clip(CircleShape)
                .background(buttonGlassBg)
                .border(BorderStroke(1.dp, glassBorderColor), CircleShape)
                .clickable(
                    interactionSource = nextInteractionSource,
                    indication = LocalIndication.current,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNextClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun BottomControlsRow(
    song: Song,
    viewModel: PlayerViewModel,
    isShuffleEnabled: Boolean,
    onShuffleClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadedSongs by viewModel.downloadedSongs.collectAsState(initial = emptyList())
    val downloadStates by viewModel.downloadStates.collectAsState()
    val state = downloadStates[song.id]
    val isDownloading = state?.isDownloading == true
    val isDownloaded = remember(downloadedSongs, state?.isComplete, song.id) { viewModel.isSongDownloaded(song) }

    val downloadInteractionSource = remember { MutableInteractionSource() }
    val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
    val downloadScale by animateFloatAsState(
        targetValue = if (isDownloadPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "DownloadScale"
    )

    val shuffleInteractionSource = remember { MutableInteractionSource() }
    val isShufflePressed by shuffleInteractionSource.collectIsPressedAsState()
    val shuffleScale by animateFloatAsState(
        targetValue = if (isShufflePressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ShuffleScale"
    )

    val queueInteractionSource = remember { MutableInteractionSource() }
    val isQueuePressed by queueInteractionSource.collectIsPressedAsState()
    val queueScale by animateFloatAsState(
        targetValue = if (isQueuePressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "QueueScale"
    )

    Row(
        modifier = modifier
            .wrapContentSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val leftShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        val middleShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 6.dp, bottomEnd = 6.dp)
        val rightShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)
        val glassBorderColor = Color.White.copy(alpha = 0.15f)
        val glassBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

        // Left Button: Download
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = downloadScale
                    scaleY = downloadScale
                }
                .height(48.dp)
                .width(76.dp)
                .clip(leftShape)
                .background(glassBgColor)
                .border(BorderStroke(1.dp, glassBorderColor), leftShape)
                .clickable(
                    interactionSource = downloadInteractionSource,
                    indication = null
                ) {
                    viewModel.downloadSong(song)
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading) {
                CircularWavyProgressIndicator(
                    progress = { state?.progress ?: 0f },
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    contentDescription = "Download",
                    modifier = Modifier.size(22.dp),
                    tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Middle Button: Shuffle
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = shuffleScale
                    scaleY = shuffleScale
                }
                .height(48.dp)
                .width(76.dp)
                .clip(middleShape)
                .background(glassBgColor)
                .border(BorderStroke(1.dp, glassBorderColor), middleShape)
                .clickable(
                    interactionSource = shuffleInteractionSource,
                    indication = null
                ) {
                    onShuffleClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                modifier = Modifier.size(22.dp),
                tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Right Button: Play Queue
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = queueScale
                    scaleY = queueScale
                }
                .height(48.dp)
                .width(76.dp)
                .clip(rightShape)
                .background(glassBgColor)
                .border(BorderStroke(1.dp, glassBorderColor), rightShape)
                .clickable(
                    interactionSource = queueInteractionSource,
                    indication = null
                ) {
                    onQueueClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Play Queue",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
