package com.auvra.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class AuvraApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            timber.log.Timber.plant(timber.log.Timber.DebugTree())
        }
        com.metrolist.innertube.YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(
            gl = "US",
            hl = "en"
        )
        com.auvra.app.player.InnerTubeXPlayer.initialize(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1000)
            runCatching {
                com.auvra.app.player.InnerTubeXPlayer.prewarm()
                timber.log.Timber.d("InnerTubeXPlayer prewarm completed")
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(okHttpClient))
            }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 1024L) // 1GB limit
                    .build()
            }
            .build()
    }
}
