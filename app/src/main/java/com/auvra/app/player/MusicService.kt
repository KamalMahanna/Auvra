@file:OptIn(UnstableApi::class)

package com.auvra.app.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.auvra.app.MainActivity
import com.auvra.app.R
import com.auvra.app.widget.MusicAppWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Inject
    lateinit var musicPlayerManager: MusicPlayerManager

    @Inject
    lateinit var queueManager: QueueManager

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        Log.d(TAG, "onCreate() service started")
        super.onCreate()
        sharedPreferences = getPlaybackPreferences(this)
        createNotificationChannel()
        promoteToForegroundImmediate()

        val defaultNotificationProvider = DefaultMediaNotificationProvider(
            this,
            { NOTIFICATION_ID },
            CHANNEL_ID,
            R.string.app_name
        ).apply {
            setSmallIcon(android.R.drawable.ic_media_play)
        }
        setMediaNotificationProvider(defaultNotificationProvider)

        val player = musicPlayerManager.getPlayer()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                Log.d(TAG, "onPlaybackResumption: System UI requested playback resumption")
                val songs = queueManager.queue.value
                val currentIndex = queueManager.currentIndex.value
                val savedPos = sharedPreferences.getLong("KEY_SEEK_POSITION", 0L)

                if (songs.isEmpty()) {
                    Log.w(TAG, "onPlaybackResumption: no songs in queue to resume")
                    return Futures.immediateFailedFuture(
                        UnsupportedOperationException("No songs to resume")
                    )
                }

                val mediaItems = songs.map { song ->
                    musicPlayerManager.buildMediaItemForResumption(song)
                }
                val startIndex = if (currentIndex in songs.indices) currentIndex else 0

                Log.d(TAG, "onPlaybackResumption: restoring ${mediaItems.size} items, startIndex=$startIndex, startPos=$savedPos")
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, savedPos)
                )
            }
        })
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(CoilBitmapLoader(this, scope))
            .build()

        mediaSession?.let { addSession(it) }
        Log.d(TAG, "MediaLibrarySession successfully built and registered")

        // Connect internal MediaController so Media3 automatically updates system notification controls
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controllerFuture?.get()
                Log.d(TAG, "MediaController initialized for MediaSession notification updates")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MediaController: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            MusicAppWidgetProvider.ACTION_PLAY_PAUSE -> {
                Log.d(TAG, "onStartCommand: ACTION_PLAY_PAUSE")
                musicPlayerManager.togglePlayPause()
            }
            MusicAppWidgetProvider.ACTION_NEXT -> {
                Log.d(TAG, "onStartCommand: ACTION_NEXT")
                scope.launch { musicPlayerManager.playNext() }
            }
            MusicAppWidgetProvider.ACTION_PREVIOUS -> {
                Log.d(TAG, "onStartCommand: ACTION_PREVIOUS")
                musicPlayerManager.playPrevious()
            }
        }
        return START_STICKY
    }

    private fun promoteToForegroundImmediate() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val fallbackNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auvra")
            .setContentText("Playing Music")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    fallbackNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, fallbackNotification)
            }
            Log.d(TAG, "Promoted service to foreground successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground safely: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        Log.d(TAG, "onGetSession: controller package=${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved() called. App swiped away from recent tasks.")
        val player = mediaSession?.player
        if (player != null && (player.playWhenReady || player.isPlaying) && player.mediaItemCount > 0) {
            // User swiped the app but music is playing — keep the service alive
            // so playback continues uninterrupted in the background.
            Log.d(TAG, "Active playback detected, keeping service alive")
        } else {
            // Nothing playing — safe to tear down the service entirely.
            Log.d(TAG, "No active playback, stopping service")
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() service stopping")
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaSession?.run {
            Log.d(TAG, "Releasing MediaLibrarySession")
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel: $CHANNEL_ID")
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auvra Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MusicService"
        private const val CHANNEL_ID = "auvra_playback"
        private const val NOTIFICATION_ID = 1001
    }
}

