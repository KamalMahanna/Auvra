package com.auvra.app.di

import android.content.Context
import androidx.room.Room
import com.auvra.app.data.api.SaavnApi
import com.auvra.app.data.local.AppDatabase
import com.auvra.app.data.local.DownloadedSongDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheSize = 50 * 1024 * 1024L // 50 MB
        val cache = Cache(context.cacheDir.resolve("http_cache"), cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    val response = chain.proceed(request)
                    if (response.isSuccessful) {
                        response
                    } else {
                        val cachedResponse = tryGetCachedResponse(chain)
                        cachedResponse ?: response
                    }
                } catch (e: Exception) {
                    val cachedResponse = tryGetCachedResponse(chain)
                    cachedResponse ?: throw e
                }
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val callParam = request.url.queryParameter("__call") ?: ""
                
                val (duration, unit) = when {
                    callParam.contains("search") -> 5 to TimeUnit.MINUTES
                    callParam.contains("getBrowseModules") -> 1 to TimeUnit.HOURS
                    else -> 12 to TimeUnit.HOURS
                }
                
                val cacheControl = CacheControl.Builder()
                    .maxAge(duration, unit)
                    .build()
                
                response.newBuilder()
                    .header("Cache-Control", cacheControl.toString())
                    .removeHeader("Pragma")
                    .removeHeader("Vary") // Bypass Vary constraint to cache successfully
                    .build()
            }
            .build()
    }

    private fun tryGetCachedResponse(chain: okhttp3.Interceptor.Chain): okhttp3.Response? {
        val cacheRequest = chain.request().newBuilder()
            .cacheControl(CacheControl.FORCE_CACHE)
            .build()
        return try {
            val cachedResponse = chain.proceed(cacheRequest)
            if (cachedResponse.code != 504) {
                cachedResponse
            } else {
                cachedResponse.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Provides
    @Singleton
    fun provideSaavnApi(okHttpClient: OkHttpClient, moshi: Moshi): SaavnApi = 
        com.auvra.app.data.api.SaavnApiImpl(okHttpClient, moshi)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "auvra_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideDownloadedSongDao(database: AppDatabase): DownloadedSongDao {
        return database.downloadedSongDao()
    }
}
