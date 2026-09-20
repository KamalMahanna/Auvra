package com.mymusic.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.mymusic.app.MainActivity
import com.mymusic.app.R
import com.mymusic.app.data.model.Song
import com.mymusic.app.player.MusicService
import com.mymusic.app.player.PlaybackState
import com.mymusic.app.ui.theme.ArtworkColorExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class MusicAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        try {
            updateAllWidgets(context, PlaybackState())
        } catch (e: Exception) {
            Log.e(TAG, "Failed in onUpdate: ${e.message}", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        Log.d(TAG, "onReceive action: $action")

        when (action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                sendActionToService(context, action)
            }
        }
    }

    private fun sendActionToService(context: Context, action: String) {
        try {
            val serviceIntent = Intent(context, MusicService::class.java).apply {
                this.action = action
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send action $action to MusicService: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "MusicAppWidget"
        const val ACTION_PLAY_PAUSE = "com.mymusic.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.mymusic.app.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.mymusic.app.ACTION_PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.mymusic.app.ACTION_UPDATE_WIDGET"

        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var lastLoadedSongId: String? = null
        private var cachedCircularBitmap: Bitmap? = null
        private var cachedAccentColor: Int = ArtworkColorExtractor.DEFAULT_ACCENT_COLOR

        fun updateAllWidgets(context: Context, playbackState: PlaybackState) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, MusicAppWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds == null || widgetIds.isEmpty()) {
                    return
                }

                val currentSong = playbackState.currentSong
                val activeAccent = if (currentSong != null) cachedAccentColor else ArtworkColorExtractor.DEFAULT_ACCENT_COLOR

                val views = createRemoteViews(context, playbackState, cachedCircularBitmap, activeAccent)
                appWidgetManager.updateAppWidget(widgetIds, views)

                if (currentSong != null) {
                    if (currentSong.id != lastLoadedSongId || cachedCircularBitmap == null) {
                        widgetScope.launch {
                            try {
                                val result = loadSongArtwork(context, currentSong)
                                val bitmap = result?.first
                                val accent = result?.second ?: ArtworkColorExtractor.DEFAULT_ACCENT_COLOR

                                cachedCircularBitmap = bitmap
                                cachedAccentColor = accent
                                lastLoadedSongId = currentSong.id

                                val updatedViews = createRemoteViews(context, playbackState, bitmap, accent)
                                appWidgetManager.updateAppWidget(widgetIds, updatedViews)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update artwork in widget: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    lastLoadedSongId = null
                    cachedCircularBitmap = null
                    cachedAccentColor = ArtworkColorExtractor.DEFAULT_ACCENT_COLOR
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in updateAllWidgets: ${e.message}", e)
            }
        }

        private fun createRemoteViews(
            context: Context,
            playbackState: PlaybackState,
            artworkBitmap: Bitmap?,
            accentColor: Int = ArtworkColorExtractor.DEFAULT_ACCENT_COLOR
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_mini_player)

            // Setup Launch App PendingIntent on body click
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_text_container, launchPendingIntent)

            // Setup Next Track PendingIntent
            val nextIntent = Intent(context, MusicAppWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            // Next button tint
            val song = playbackState.currentSong
            val nextTint = if (song != null) accentColor else 0xFFE5E7EB.toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_btn_next,
                    "setImageTintList",
                    ColorStateList.valueOf(nextTint)
                )
            } else {
                views.setInt(R.id.widget_btn_next, "setColorFilter", nextTint)
            }

            // Setup Play/Pause PendingIntent
            val playPauseIntent = Intent(context, MusicAppWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // Update song titles and artist accent color
            if (song != null) {
                views.setTextViewText(R.id.widget_title, song.name)
                views.setTextViewText(
                    R.id.widget_artist,
                    song.primaryArtistNames.ifEmpty { "Unknown Artist" }
                )
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(accentColor, hsl)
                hsl[1] = (hsl[1] * 0.60f).coerceIn(0.35f, 0.75f)
                hsl[2] = 0.78f
                views.setTextColor(R.id.widget_artist, ColorUtils.HSLToColor(hsl))
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, "Tap to play")
                views.setTextColor(R.id.widget_artist, 0xFF9CA3AF.toInt())
            }

            // Update Play/Pause icon overlay
            val playPauseRes = if (playbackState.isPlaying) {
                R.drawable.ic_widget_pause
            } else {
                R.drawable.ic_widget_play
            }
            views.setImageViewResource(R.id.widget_play_pause_icon, playPauseRes)

            // Update Artwork with accent stroke or tinted fallback
            if (artworkBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
                views.setInt(R.id.widget_artwork, "setColorFilter", 0)
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
                views.setInt(R.id.widget_artwork, "setColorFilter", accentColor)
            }

            // Update Progress with dynamic accent tint
            val progress = if (playbackState.duration > 0) {
                ((playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()) * 1000)
                    .toInt()
                    .coerceIn(0, 1000)
            } else {
                0
            }
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val progressTint = if (song != null) {
                    ColorUtils.setAlphaComponent(accentColor, 0x4D)
                } else {
                    0x26818CF8.toInt()
                }
                views.setColorStateList(
                    R.id.widget_progress,
                    "setProgressTintList",
                    ColorStateList.valueOf(progressTint)
                )

                val ambientTint = if (song != null) {
                    ColorUtils.setAlphaComponent(accentColor, 0x1E)
                } else {
                    android.graphics.Color.TRANSPARENT
                }
                views.setColorStateList(
                    R.id.widget_ambient_tint,
                    "setBackgroundTintList",
                    ColorStateList.valueOf(ambientTint)
                )
            }

            return views
        }

        private suspend fun loadSongArtwork(context: Context, song: Song): Pair<Bitmap, Int>? {
            return withContext(Dispatchers.IO) {
                try {
                    val imageUrl = song.mediumQualityImageUrl ?: song.highQualityImageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .size(160, 160)
                            .allowHardware(false)
                            .build()

                        val result = context.imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = result.image.toBitmap()
                            val accent = ArtworkColorExtractor.extractAccentColor(bitmap)
                            val circularBitmap = createCircularBitmap(bitmap, 160)
                            return@withContext Pair(circularBitmap, accent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading artwork for widget: ${e.message}", e)
                }
                null
            }
        }

        private fun createCircularBitmap(src: Bitmap, targetSize: Int): Bitmap {
            val scaled = if (src.width != targetSize || src.height != targetSize) {
                val size = min(src.width, src.height)
                val x = (src.width - size) / 2
                val y = (src.height - size) / 2
                val squared = Bitmap.createBitmap(src, x, y, size, size)
                Bitmap.createScaledBitmap(squared, targetSize, targetSize, true)
            } else {
                src
            }

            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val radius = targetSize / 2f
            canvas.drawCircle(radius, radius, radius, paint)
            return output
        }
    }
}
