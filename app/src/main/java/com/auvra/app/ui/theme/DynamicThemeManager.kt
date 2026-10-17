package com.auvra.app.ui.theme

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.auvra.app.data.model.Song
import com.auvra.app.player.MusicPlayerManager
import com.auvra.app.widget.MusicAppWidgetProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicThemeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicPlayerManager: MusicPlayerManager,
    private val artworkColorExtractor: ArtworkColorExtractor
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val colorCache = LruCache<String, Color>(100)
    private var latestSongId: String? = null

    private val _currentAccentColor = MutableStateFlow(GlassPrimary)
    val currentAccentColor: StateFlow<Color> = _currentAccentColor.asStateFlow()

    init {
        Log.w(TAG, "DynamicThemeManager initialized successfully")
        val initialSong = musicPlayerManager.playbackState.value.currentSong
        if (initialSong != null) {
            updateAccentForSong(initialSong)
        }

        scope.launch {
            musicPlayerManager.playbackState
                .map { it.currentSong }
                .distinctUntilChangedBy { it?.id }
                .collect { song ->
                    Log.w(TAG, "Observed song transition: '${song?.name}' (id=${song?.id})")
                    updateAccentForSong(song)
                }
        }
    }

    private fun updateAccentForSong(song: Song?) {
        if (song == null) {
            latestSongId = null
            _currentAccentColor.value = GlassPrimary
            MusicAppWidgetProvider.updateWidgetColor(context, null, GlassPrimary.toArgb())
            return
        }

        latestSongId = song.id

        // Check memory cache first for 0ms instant transition
        val cached = colorCache.get(song.id)
        if (cached != null) {
            _currentAccentColor.value = cached
            MusicAppWidgetProvider.updateWidgetColor(context, song.id, cached.toArgb())
            Log.w(TAG, "Accent color cache hit for '${song.name}': $cached")
            return
        }

        // Asynchronously extract color without blocking the main thread
        scope.launch(Dispatchers.IO) {
            val extractedColor = artworkColorExtractor.extractAccentColor(song)
            val argb = extractedColor.toArgb()
            colorCache.put(song.id, extractedColor)
            ArtworkColorCache.put(song.id, argb)

            // Only update if this song is still the active one
            if (latestSongId == song.id) {
                _currentAccentColor.value = extractedColor
                MusicAppWidgetProvider.updateWidgetColor(context, song.id, argb)
                Log.w(TAG, "Applied dynamic accent color for '${song.name}': $extractedColor")
            }
        }
    }

    companion object {
        private const val TAG = "DynamicThemeManager"
    }
}
