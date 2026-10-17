@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.auvra.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
fun AuvraTheme(
    darkTheme: Boolean = true,
    targetAccentColor: Color = GlassPrimary,
    content: @Composable () -> Unit
) {
    val animatedPrimary by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "dynamicAccentColorAnimation"
    )

    val primaryContainer = remember(animatedPrimary) {
        val hsv = FloatArray(3)
        ArtworkColorExtractor.rgbToHsv(
            (animatedPrimary.red * 255).toInt(),
            (animatedPrimary.green * 255).toInt(),
            (animatedPrimary.blue * 255).toInt(),
            hsv
        )
        val tunedS = (hsv[1] * 0.85f).coerceIn(0.40f, 0.90f)
        val tunedV = 0.35f // Deep tonal container
        ArtworkColorExtractor.hsvToColor(hsv[0], tunedS, tunedV)
    }

    val secondary = remember(animatedPrimary) {
        shiftHue(animatedPrimary, 25f)
    }

    val tertiary = remember(animatedPrimary) {
        shiftHue(animatedPrimary, -25f)
    }

    val colorScheme = remember(animatedPrimary, primaryContainer, secondary, tertiary) {
        GlassColorScheme.copy(
            primary = animatedPrimary,
            primaryContainer = primaryContainer,
            secondary = secondary,
            tertiary = tertiary
        )
    }

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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    ArtworkColorExtractor.rgbToHsv(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv
    )
    val newHue = (hsv[0] + degrees).mod(360f)
    return ArtworkColorExtractor.hsvToColor(newHue, hsv[1], hsv[2])
}

