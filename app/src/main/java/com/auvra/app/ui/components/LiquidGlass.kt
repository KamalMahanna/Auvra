package com.auvra.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.auvra.app.ui.components.backdrop.Backdrop
import com.auvra.app.ui.components.backdrop.backdrops.LayerBackdrop
import com.auvra.app.ui.components.backdrop.drawBackdrop
import com.auvra.app.ui.components.backdrop.effects.blur
import com.auvra.app.ui.components.backdrop.effects.colorControls
import com.auvra.app.ui.components.backdrop.effects.lens
import com.auvra.app.ui.components.backdrop.highlight.Highlight
import com.auvra.app.ui.components.backdrop.highlight.HighlightElement
import com.auvra.app.ui.components.backdrop.internal.ShapeProvider
import com.auvra.app.ui.components.backdrop.shadow.Shadow

val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

/** Apple-matched defaults from BitChord LiquidGlass */
private const val VIBRANCY = 1f
private const val BLUR_RADIUS_DP = 8f
private const val LENS_HEIGHT = 0.5f
private const val LENS_AMOUNT = 0.5f
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = 0.35f
private const val GLASS_RESOLUTION_SCALE = 0.33f

val GLASS_EDGE_WIDTH = 0.5.dp
val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.12f)

@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun glassEdgeColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }

@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    if (!LocalLiquidGlassEnabled.current || !isGlassSupported()) return this
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    return then(
        HighlightElement(
            shapeProvider = shapeProvider,
            highlight = { Highlight.Default },
        ),
    )
}

/**
 * Renders this composable as a liquid glass surface sampling [LocalAppBackdrop]:
 * vibrancy (saturation boost), hardware blur, and lens refraction with chromatic aberration,
 * directional highlight rim, soft drop shadow, and a theme-adaptive translucent surface tint.
 */
@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,
    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) {
        val fallbackTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
            Color(0xFFFAFAFA).copy(alpha = 0.85f)
        } else {
            Color(0xFF121212).copy(alpha = 0.85f)
        }
        return clip(shape)
            .background(fallbackTint)
            .border(GLASS_EDGE_WIDTH, glassEdgeColor(), shape)
    }

    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val resolutionScale = GLASS_RESOLUTION_SCALE
    val blurPx = with(density) { BLUR_RADIUS_DP.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP).dp.toPx() } * resolutionScale
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint ?: themeTint.copy(alpha = SURFACE_OPACITY)

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1f + 0.5f * VIBRANCY)
            blur(blurPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = true,
                    chromaticAberration = true,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        backdropScale = resolutionScale,
    )
}
