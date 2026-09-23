package com.auvra.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.auvra.app.data.model.Song
import com.auvra.app.player.MusicPlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class DynamicColorPalette(
    val primary: Color = GlassPrimary,
    val onPrimary: Color = Color.White,
    val primaryContainer: Color = GlassPrimaryContainer,
    val onPrimaryContainer: Color = Color(0xFFE0E7FF),
    val secondary: Color = GlassSecondary,
    val onSecondary: Color = Color.White,
    val tertiary: Color = GlassTertiary,
    val onTertiary: Color = Color.White
)

val DefaultDynamicPalette = DynamicColorPalette()

@Singleton
class DynamicThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicPlayerManager: MusicPlayerManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val paletteCache = LruCache<String, DynamicColorPalette>(50)
    private var extractionJob: Job? = null

    private val _dynamicPalette = MutableStateFlow(DefaultDynamicPalette)
    val dynamicPalette: StateFlow<DynamicColorPalette> = _dynamicPalette.asStateFlow()

    init {
        scope.launch {
            musicPlayerManager.playbackState
                .map { it.currentSong }
                .distinctUntilChanged { old, new ->
                    old?.id == new?.id && old?.mediumQualityImageUrl == new?.mediumQualityImageUrl
                }
                .collect { song ->
                    updatePaletteForSong(song)
                }
        }
    }

    private fun updatePaletteForSong(song: Song?) {
        if (song == null) {
            extractionJob?.cancel()
            _dynamicPalette.value = DefaultDynamicPalette
            return
        }

        val cacheKey = song.id.ifEmpty { song.name + song.primaryArtistNames }
        val cached = paletteCache.get(cacheKey)
        if (cached != null) {
            _dynamicPalette.value = cached
            return
        }

        extractionJob?.cancel()
        extractionJob = scope.launch(Dispatchers.IO) {
            val imageUrl = song.mediumQualityImageUrl
                ?: song.highQualityImageUrl
                ?: song.lowQualityImageUrl

            if (!imageUrl.isNullOrBlank()) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(128, 128)
                        .allowHardware(false)
                        .build()

                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = result.image.toBitmap()
                        val extracted = ArtworkColorExtractor.extractDynamicPalette(bitmap)
                        paletteCache.put(cacheKey, extracted)
                        _dynamicPalette.value = extracted
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract palette for song ${song.name}: ${e.message}", e)
                }
            }

            // Fallback if extraction failed or image url was blank
            _dynamicPalette.value = DefaultDynamicPalette
        }
    }

    companion object {
        private const val TAG = "DynamicThemeManager"
    }
}

object ArtworkColorExtractor {
    val DEFAULT_ACCENT_COLOR = GlassPrimary.toArgb()

    fun extractAccentColor(bitmap: Bitmap): Int {
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(24)
                .clearFilters()
                .generate()

            val primaryInt = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: DEFAULT_ACCENT_COLOR

            tuneColorForDarkTheme(primaryInt)
        } catch (e: Exception) {
            DEFAULT_ACCENT_COLOR
        }
    }

    fun tuneColorForDarkTheme(colorInt: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorInt, hsl)

        // Boost saturation slightly if it's too washed out
        if (hsl[1] < 0.40f && hsl[1] > 0.05f) {
            hsl[1] = 0.55f
        }

        // Clamp lightness in the ideal range for dark mode UI
        hsl[2] = hsl[2].coerceIn(0.55f, 0.78f)

        return ColorUtils.HSLToColor(hsl)
    }

    fun extractDynamicPalette(bitmap: Bitmap): DynamicColorPalette {
        return try {
            val palette = Palette.from(bitmap)
                .maximumColorCount(24)
                .clearFilters()
                .generate()

            // Select most expressive accent swatch
            val primaryInt = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: DEFAULT_ACCENT_COLOR

            val adjustedPrimary = tuneColorForDarkTheme(primaryInt)
            val primaryCompose = Color(adjustedPrimary)

            // High contrast text/icon for primary surfaces
            val luminance = ColorUtils.calculateLuminance(adjustedPrimary)
            val onPrimary = if (luminance > 0.65) Color(0xFF0F172A) else Color.White

            // Container: tinted darker variation with subtle saturation
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(adjustedPrimary, hsl)
            val containerHsl = floatArrayOf(
                hsl[0],
                (hsl[1] * 0.85f).coerceIn(0.40f, 1f),
                0.22f
            )
            val primaryContainerCompose = Color(ColorUtils.HSLToColor(containerHsl))
            val onPrimaryContainer = Color(0xFFF1F5F9)

            // Secondary: complimentary or light vibrant swatch
            val secondaryInt = palette.lightVibrantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: run {
                    val secHsl = floatArrayOf((hsl[0] + 35f) % 360f, hsl[1], hsl[2])
                    ColorUtils.HSLToColor(secHsl)
                }
            val adjustedSecondary = tuneColorForDarkTheme(secondaryInt)
            val secondaryCompose = Color(adjustedSecondary)

            // Tertiary: light muted swatch or hue shifted
            val tertiaryInt = palette.lightMutedSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: run {
                    val tertHsl = floatArrayOf((hsl[0] + 70f) % 360f, hsl[1], hsl[2])
                    ColorUtils.HSLToColor(tertHsl)
                }
            val adjustedTertiary = tuneColorForDarkTheme(tertiaryInt)
            val tertiaryCompose = Color(adjustedTertiary)

            DynamicColorPalette(
                primary = primaryCompose,
                onPrimary = onPrimary,
                primaryContainer = primaryContainerCompose,
                onPrimaryContainer = onPrimaryContainer,
                secondary = secondaryCompose,
                onSecondary = Color.White,
                tertiary = tertiaryCompose,
                onTertiary = Color.White
            )
        } catch (e: Exception) {
            DefaultDynamicPalette
        }
    }
}

