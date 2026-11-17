package com.auvra.app.ui.navigation

import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.auvra.app.ui.components.BottomFadeScrim
import com.auvra.app.ui.components.GLASS_EDGE_WIDTH
import com.auvra.app.ui.components.LocalAppBackdrop
import com.auvra.app.ui.components.LocalLiquidGlassEnabled
import com.auvra.app.ui.components.MiniPlayer
import com.auvra.app.ui.components.OfflineBanner
import com.auvra.app.ui.components.UpdateDialog
import com.auvra.app.ui.components.backdrop.backdrops.layerBackdrop
import com.auvra.app.ui.components.backdrop.backdrops.rememberLayerBackdrop
import com.auvra.app.ui.components.glassContentColor
import com.auvra.app.ui.components.glassEdgeColor
import com.auvra.app.ui.components.liquidGlass
import com.auvra.app.ui.icons.NavIcons
import com.auvra.app.ui.screens.home.HomeScreen
import com.auvra.app.ui.screens.library.LibraryScreen
import com.auvra.app.ui.screens.player.PlayerScreen
import com.auvra.app.ui.screens.player.PlayerViewModel
import com.auvra.app.ui.screens.search.SearchScreen
import com.auvra.app.ui.screens.update.AppUpdateViewModel

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", NavIcons.Home)
    object Search : Screen("search", "Search", NavIcons.Search)
    object Library : Screen("library", "Library", NavIcons.Library)
}

val items = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

@Composable
fun AuvraNavGraph(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isMiniPlayerVisible = currentSong != null && !isPlayerExpanded

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val windowBackground = MaterialTheme.colorScheme.background
    val paintBackdrop: ContentDrawScope.() -> Unit = remember(windowBackground) {
        {
            drawRect(windowBackground)
            drawContent()
        }
    }
    val appBackdrop = rememberLayerBackdrop(onDraw = paintBackdrop)

    val screenBottomPadding = if (isTablet) 80.dp else if (isMiniPlayerVisible) 160.dp else 96.dp

    CompositionLocalProvider(
        LocalLiquidGlassEnabled provides true,
        LocalAppBackdrop provides appBackdrop,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Fullscreen NavHost recording into appBackdrop for true hardware liquid glass
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(appBackdrop),
                enterTransition = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 12 }
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { -it / 12 }
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { -it / 12 }
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    slideOutHorizontally(animationSpec = tween(180, easing = FastOutSlowInEasing)) { it / 12 }
                }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onPlaySong = { isPlayerExpanded = true },
                        bottomPadding = screenBottomPadding
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        isPlayerExpanded = isPlayerExpanded,
                        onPlaySong = { isPlayerExpanded = true },
                        bottomPadding = screenBottomPadding
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        isPlayerExpanded = isPlayerExpanded,
                        onPlaySong = { isPlayerExpanded = true },
                        bottomPadding = screenBottomPadding
                    )
                }
            }

            // 2. Floor gradient easing out content scrolling behind the floating bars
            BottomFadeScrim(
                withMiniPlayer = isMiniPlayerVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 3. Floating Bars Column: MiniPlayer + CustomBottomNavigation
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isTablet) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 19.dp)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomBottomNavigationContent(navController = navController)
                        if (isMiniPlayerVisible) {
                            Spacer(modifier = Modifier.width(16.dp))
                            MiniPlayer(onPlayerClick = { isPlayerExpanded = true })
                        }
                    }
                } else {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isMiniPlayerVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 200)) + 
                                slideInVertically(
                                    initialOffsetY = { it / 2 },
                                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 150)) + 
                               slideOutVertically(
                                   targetOffsetY = { it / 2 },
                                   animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                )
                    ) {
                        MiniPlayer(onPlayerClick = { isPlayerExpanded = true })
                    }

                    CustomBottomNavigation(navController = navController)
                }
            }

            // Full Player Screen (Slides up from the bottom, covering the whole screen including the navbar)
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)) + 
                        slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 250)) + 
                       slideOutVertically(
                           targetOffsetY = { it },
                           animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                       )
            ) {
                PlayerScreen(
                    onCollapse = { isPlayerExpanded = false }
                )
            }

            // New Version Update Dialog (shown on app launch when an update is available)
            val availableUpdate by updateViewModel.availableUpdate.collectAsState()
            availableUpdate?.let { release ->
                val context = LocalContext.current
                UpdateDialog(
                    release = release,
                    currentVersion = updateViewModel.installedVersion,
                    onDownload = {
                        updateViewModel.dismissUpdate(release)
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, release.downloadUrl.toUri()).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("NavGraph", "Failed to launch download URL: ${e.message}", e)
                        }
                    },
                    onDismiss = {
                        updateViewModel.dismissForNow()
                    }
                )
            }

            // Floating Offline Status Banner (at the top, over all screens and sheets)
            val isOnline by playerViewModel.isOnline.collectAsState()
            OfflineBanner(
                isOnline = isOnline,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun CustomBottomNavigation(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 19.dp)
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomBottomNavigationContent(
            navController = navController
        )
    }
}

@Composable
fun CustomBottomNavigationContent(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val pillShape = remember { RoundedCornerShape(percent = 50) }

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(pillShape)
            .liquidGlass(shape = pillShape)
            .border(GLASS_EDGE_WIDTH, glassEdgeColor(), pillShape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route
            NavBarItem(
                screen = screen,
                isSelected = isSelected,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun NavBarItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Bouncy press effect (shrinks to 0.88f and bounces back)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NavBarItemScale"
    )

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        glassContentColor().copy(alpha = 0.65f)
    }

    Row(
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null // Disables default ripple so our custom press animation stands out cleanly
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .then(
                if (isSelected) {
                    Modifier.padding(horizontal = 14.dp)
                } else {
                    Modifier.width(48.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = screen.icon,
            contentDescription = screen.title,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(animationSpec = tween(150, delayMillis = 80)) + 
                    slideInHorizontally(animationSpec = spring(stiffness = Spring.StiffnessMedium)) { -it / 2 },
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = screen.title,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
