@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mymusic.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDynamicThemePalette = staticCompositionLocalOf { DefaultDynamicPalette }

val GlassColorScheme = darkColorScheme(
    primary = GlassPrimary,
    onPrimary = Color.White,
    primaryContainer = GlassPrimaryContainer,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = GlassSecondary,
    onSecondary = Color.White,
    tertiary = GlassTertiary,
    onTertiary = Color.White,
    background = GlassBackground,
    onBackground = GlassOnBackground,
    surface = GlassSurface,
    onSurface = GlassOnSurface,
    surfaceVariant = GlassSurfaceVariant,
    onSurfaceVariant = GlassOnSurfaceVariant
)

@Composable
fun MyMusicTheme(
    dynamicPalette: DynamicColorPalette = DefaultDynamicPalette,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val animatedPrimary by animateColorAsState(
        targetValue = dynamicPalette.primary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "AnimatedThemePrimary"
    )
    val animatedPrimaryContainer by animateColorAsState(
        targetValue = dynamicPalette.primaryContainer,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "AnimatedThemePrimaryContainer"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = dynamicPalette.secondary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "AnimatedThemeSecondary"
    )
    val animatedTertiary by animateColorAsState(
        targetValue = dynamicPalette.tertiary,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "AnimatedThemeTertiary"
    )

    val colorScheme = GlassColorScheme.copy(
        primary = animatedPrimary,
        onPrimary = dynamicPalette.onPrimary,
        primaryContainer = animatedPrimaryContainer,
        onPrimaryContainer = dynamicPalette.onPrimaryContainer,
        secondary = animatedSecondary,
        tertiary = animatedTertiary
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalDynamicThemePalette provides dynamicPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
