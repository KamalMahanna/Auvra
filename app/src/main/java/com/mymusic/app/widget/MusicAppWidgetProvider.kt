package com.mymusic.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

        fun updateAllWidgets(context: Context, playbackState: PlaybackState) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, MusicAppWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds == null || widgetIds.isEmpty()) {
                    return
                }

                val views = createRemoteViews(context, playbackState, cachedCircularBitmap)
                appWidgetManager.updateAppWidget(widgetIds, views)

                val currentSong = playbackState.currentSong
                if (currentSong != null) {
                    if (currentSong.id != lastLoadedSongId || cachedCircularBitmap == null) {
                        widgetScope.launch {
                            try {
                                val bitmap = loadSongArtwork(context, currentSong)
                                cachedCircularBitmap = bitmap
                                lastLoadedSongId = currentSong.id

                                val updatedViews = createRemoteViews(context, playbackState, bitmap)
                                appWidgetManager.updateAppWidget(widgetIds, updatedViews)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update artwork in widget: ${e.message}", e)
                            }
                        }
                    }
                } else {
                    lastLoadedSongId = null
                    cachedCircularBitmap = null
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in updateAllWidgets: ${e.message}", e)
            }
        }

        private fun createRemoteViews(
            context: Context,
            playbackState: PlaybackState,
            artworkBitmap: Bitmap?
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
            views.setOnClickPendingIntent(R.id.widget_art_container, launchPendingIntent)
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

            // Update Play/Pause icon
            val playPauseRes = if (playbackState.isPlaying) {
                R.drawable.ic_widget_pause
            } else {
                R.drawable.ic_widget_play
            }
            views.setImageViewResource(R.id.widget_btn_play_pause, playPauseRes)

            // Update Artwork
            if (artworkBitmap != null) {
                views.setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
            } else {
                views.setImageViewResource(R.id.widget_artwork, R.drawable.ic_widget_music_note)
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

            return views
        }

        private suspend fun loadSongArtwork(context: Context, song: Song): Bitmap? {
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
                            return@withContext createCircularBitmap(bitmap, 160)
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
