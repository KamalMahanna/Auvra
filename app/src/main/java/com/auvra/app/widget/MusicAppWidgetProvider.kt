package com.auvra.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.auvra.app.MainActivity
import com.auvra.app.R
import com.auvra.app.data.model.Song
import com.auvra.app.player.MusicService
import com.auvra.app.player.PlaybackState
import com.auvra.app.ui.theme.ArtworkColorCache
import com.auvra.app.ui.theme.ArtworkColorExtractor
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
        const val ACTION_PLAY_PAUSE = "com.auvra.app.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.auvra.app.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.auvra.app.ACTION_PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.auvra.app.ACTION_UPDATE_WIDGET"

        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var lastLoadedSongId: String? = null
        private var cachedCircularBitmap: Bitmap? = null
        private var cachedRawBitmap: Bitmap? = null
        private var cachedAccentColor: Int? = null
        private var lastPlaybackState: PlaybackState? = null

        fun updateWidgetColor(context: Context, songId: String?, color: Int) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, MusicAppWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds == null || widgetIds.isEmpty()) {
                    return
                }

                if (songId != null && lastPlaybackState?.currentSong?.id != songId) {
                    return
                }

                cachedAccentColor = color

                val rawBitmap = cachedRawBitmap
                if (rawBitmap != null && cachedCircularBitmap == null) {
                    cachedCircularBitmap = createCircularBitmap(rawBitmap, 160)
                }

                val state = lastPlaybackState ?: PlaybackState()
                val views = createRemoteViews(context, state, cachedCircularBitmap, color)
                appWidgetManager.updateAppWidget(widgetIds, views)
                Log.d(TAG, "Updated widget color profile for songId=$songId: color=$color")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed in updateWidgetColor: ${e.message}", e)
            }
        }

        fun updateAllWidgets(context: Context, playbackState: PlaybackState) {
            try {
                lastPlaybackState = playbackState
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, MusicAppWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds == null || widgetIds.isEmpty()) {
                    return
                }

                val currentSong = playbackState.currentSong
                val accentColor = currentSong?.let { ArtworkColorCache.get(it.id) }
                    ?: cachedAccentColor
                    ?: ArtworkColorCache.DEFAULT_ACCENT_COLOR

                val views = createRemoteViews(context, playbackState, cachedCircularBitmap, accentColor)
                appWidgetManager.updateAppWidget(widgetIds, views)

                if (currentSong != null) {
                    if (currentSong.id != lastLoadedSongId || cachedCircularBitmap == null) {
                        widgetScope.launch {
                            try {
                                val (rawBitmap, extractedColor) = loadSongArtworkAndColor(context, currentSong)
                                cachedRawBitmap = rawBitmap
                                lastLoadedSongId = currentSong.id
                                val finalColor = extractedColor ?: accentColor
                                cachedAccentColor = finalColor

                                val circular = if (rawBitmap != null) {
                                    createCircularBitmap(rawBitmap, 160)
                                } else null
                                cachedCircularBitmap = circular

                                val updatedViews = createRemoteViews(context, playbackState, circular, finalColor)
                                appWidgetManager.updateAppWidget(widgetIds, updatedViews)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update artwork in widget: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    lastLoadedSongId = null
                    cachedCircularBitmap = null
                    cachedRawBitmap = null
                    cachedAccentColor = null
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in updateAllWidgets: ${e.message}", e)
            }
        }

        private fun createRemoteViews(
            context: Context,
            playbackState: PlaybackState,
            artworkBitmap: Bitmap?,
            accentColor: Int
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

            // Update song titles
            val song = playbackState.currentSong
            if (song != null) {
                views.setTextViewText(R.id.widget_title, song.name)
                views.setTextViewText(
                    R.id.widget_artist,
                    song.primaryArtistNames.ifEmpty { "Unknown Artist" }
                )
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_artist, "Tap to play")
            }

            // Update Play/Pause icon overlay and tint with dynamic accent color
            val playPauseRes = if (playbackState.isPlaying) {
                R.drawable.ic_widget_pause
            } else {
                R.drawable.ic_widget_play
            }
            views.setImageViewResource(R.id.widget_play_pause_icon, playPauseRes)
            views.setInt(R.id.widget_play_pause_icon, "setColorFilter", accentColor)

            // Update Next button tint with dynamic accent color
            views.setInt(R.id.widget_btn_next, "setColorFilter", accentColor)

            // Update Artwork (with accent border or placeholder)
            if (artworkBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
            } else {
                val placeholder = createPlaceholderBitmap(context, 160, accentColor)
                views.setImageViewBitmap(R.id.widget_artwork, placeholder)
            }

            // Update Progress
            val progress = if (playbackState.duration > 0) {
                ((playbackState.currentPosition.toFloat() / playbackState.duration.toFloat()) * 1000)
                    .toInt()
                    .coerceIn(0, 1000)
            } else {
                0
            }
            views.setProgressBar(R.id.widget_progress, 1000, progress, false)

            // Apply accent wash to progress bar on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val progressWash = ColorUtils.setAlphaComponent(accentColor, 0x38) // ~22% opacity
                    views.setColorStateList(
                        R.id.widget_progress,
                        "setProgressTintList",
                        ColorStateList.valueOf(progressWash)
                    )
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to set progress tint on widget: ${e.message}")
                }
            }

            return views
        }

        private suspend fun loadSongArtworkAndColor(
            context: Context,
            song: Song
        ): Pair<Bitmap?, Int?> = withContext(Dispatchers.IO) {
            try {
                var cachedColor = ArtworkColorCache.get(song.id)
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
                        if (cachedColor == null) {
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            val vibrantColor = ArtworkColorExtractor.extractDominantVibrantColor(pixels)
                            cachedColor = vibrantColor.toArgb()
                            ArtworkColorCache.put(song.id, cachedColor)
                        }
                        return@withContext Pair(bitmap, cachedColor)
                    }
                }
                Pair(null, cachedColor)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artwork for widget: ${e.message}", e)
                Pair(null, null)
            }
        }

        private fun createPlaceholderBitmap(context: Context, targetSize: Int, accentColor: Int): Bitmap {
            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val radius = targetSize / 2f

            // Clean dark background circle
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF25283B.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(radius, radius, radius, bgPaint)

            // Music note icon tinted with dynamic accent color
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_widget_music_note)?.mutate()
            if (drawable != null) {
                drawable.setTint(accentColor)
                val iconSize = (targetSize * 0.48f).toInt()
                val offset = (targetSize - iconSize) / 2
                drawable.setBounds(offset, offset, offset + iconSize, offset + iconSize)
                drawable.draw(canvas)
            }

            return output
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
