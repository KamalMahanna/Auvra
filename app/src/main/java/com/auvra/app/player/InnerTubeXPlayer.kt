@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.auvra.app.player

import android.content.Context
import android.net.ConnectivityManager
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.Thumbnail
import com.metrolist.innertube.models.Thumbnails
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality as InnerTubeXAudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.auvra.app.utils.potoken.PoTokenGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Stream extraction engine for Auvra, incorporating BitChord's multi-tiered architecture:
 *
 * 1. **InnerTubeX Primary Tier**: Live client profile rotation (VISIONOS, ANDROID,
 *    ANDROID_MUSIC, WEB_REMIX), player cipher evaluation, and BotGuard WebPO tokens.
 * 2. **Pre-flight CDN Probe**: Tests candidate stream URLs with HTTP byte-range probes
 *    and matching client headers to guarantee playback readiness and detect HTTP 403s before ExoPlayer loads.
 * 3. **NewPipeExtractor Failsafe Tier**: Scrapes YouTube's watch page directly to derive
 *    playable streams if InnerTubeX is rate-shaped or fails to extract.
 */
object InnerTubeXPlayer {
    private const val TAG = "InnerTubeXPlayer"
    private const val STREAM_CLIENT_FAILURE_TTL_MS = 10 * 60 * 1000L
    private const val DEFAULT_STREAM_TTL_SECONDS = 5 * 60
    private const val INNERTUBEX_MAX_ATTEMPTS = 3
    private const val PROBE_READ_BYTES = 1L

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var currentBundle: ExtractionBundle? = null

    private val bundleMutex = Mutex()
    private val newPipeExtractionGate = Mutex()
    private val streamClientFailures = ConcurrentHashMap<String, FailedStreamClients>()
    private val playbackDataCache = ConcurrentHashMap<String, CachedPlaybackData>()

    /** Probe HTTP client with fast timeouts to avoid blocking player loads. */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Reusable OkHttpClient for NewPipeExtractor fetches. */
    private val newPipeHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** NewPipe OkHttp Downloader with optimized empty next endpoint handling. */
    private class NewPipeOkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        companion object {
            private const val NEXT_ENDPOINT = "/youtubei/v1/next"
            private const val EMPTY_NEXT_RESPONSE =
                """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""
            private const val DEFAULT_UA =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        }

        override fun execute(request: NewPipeRequest): NewPipeResponse {
            if (NEXT_ENDPOINT in request.url()) {
                return NewPipeResponse(200, "OK", emptyMap(), EMPTY_NEXT_RESPONSE, request.url())
            }

            val builder = Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())

            var hasUserAgent = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) {
                    hasUserAgent = true
                }
                when {
                    values.size > 1 -> {
                        builder.removeHeader(name)
                        values.forEach { builder.addHeader(name, it) }
                    }
                    values.size == 1 -> builder.header(name, values[0])
                }
            }
            if (!hasUserAgent) {
                builder.header("User-Agent", DEFAULT_UA)
            }

            val response = client.newCall(builder.build()).execute()
            return NewPipeResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }

    private val initNewPipe by lazy {
        NewPipe.init(NewPipeOkHttpDownloader(newPipeHttpClient))
    }

    private data class CachedPlaybackData(
        val data: PlaybackData,
        val expiresAtMs: Long
    )

    @Synchronized
    fun initialize(context: Context) {
        if (applicationContext == null) applicationContext = context.applicationContext
    }

    suspend fun prewarm() = bundle().extractor.prewarm()

    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.AUTO,
        connectivityManager: ConnectivityManager,
        contentHints: ContentHints = ContentHints(),
        allowBoundedRange: Boolean = true,
    ): Result<PlaybackData> {
        val cacheKey = "${videoId}_${audioQuality.name}_$allowBoundedRange"
        val cached = playbackDataCache[cacheKey]
        if (cached != null && System.currentTimeMillis() < cached.expiresAtMs) {
            return Result.success(cached.data)
        }

        return try {
            // 1. Try InnerTubeX with client rotation and pre-flight probing
            var lastInnerTubeError: Throwable? = null
            var innerTubePlaybackData: PlaybackData? = null

            val isRequireM4a = audioQuality == AudioQuality.LOW
            val targetAudioQuality = audioQuality.toInnerTubeX(connectivityManager)

            for (attempt in 0 until INNERTUBEX_MAX_ATTEMPTS) {
                val excludedClients = failedStreamClients(videoId)
                val hints = contentHints.copy(
                    isUploaded = contentHints.isUploaded == true ||
                        playlistId == "MLPT" ||
                        playlistId?.contains("MLPT") == true,
                ).withStreamCapabilities(
                    allowHls = false,
                    allowSabr = false,
                    allowBoundedRange = allowBoundedRange,
                )

                val extractedStream = try {
                    bundle().extractor.extract(
                        videoId = videoId,
                        hints = hints,
                        excludedClients = excludedClients,
                        audioQuality = targetAudioQuality,
                        clientPlaybackNonce = generateClientPlaybackNonce(),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastInnerTubeError = e
                    Timber.tag(TAG).w("InnerTubeX attempt $attempt failed for $videoId: ${e.message}")
                    null
                }

                if (extractedStream != null && extractedStream.sabrBootstrap == null) {
                    val clientHeaders = extractedStream.headers.ifEmpty {
                        PlayerClient.forStreamUrl(extractedStream.audioUrl).mediaHeaders()
                    }
                    val probeVerdict = probeStreamUrl(extractedStream.audioUrl, clientHeaders)
                    if (probeVerdict) {
                        Timber.tag(TAG).d("Resolved $videoId via InnerTubeX ${extractedStream.clientName} (probe OK)")
                        innerTubePlaybackData = extractedStream.toPlaybackData(clientHeaders)
                        break
                    } else {
                        Timber.tag(TAG).w("InnerTubeX ${extractedStream.clientName} minted unusable URL for $videoId (probe failed)")
                        markStreamClientFailed(videoId, extractedStream.clientName)
                    }
                }
            }

            if (innerTubePlaybackData != null) {
                val ttlMs = (innerTubePlaybackData.streamExpiresInSeconds.toLong() * 1000L).coerceIn(30_000L, 5 * 60 * 1000L)
                playbackDataCache[cacheKey] = CachedPlaybackData(innerTubePlaybackData, System.currentTimeMillis() + ttlMs)
                return Result.success(innerTubePlaybackData)
            }

            // 2. Failsafe Tier: Fall back to NewPipeExtractor
            Timber.tag(TAG).i("InnerTubeX found no usable stream for $videoId; falling back to NewPipeExtractor")
            val fallbackData = extractViaNewPipe(videoId, isRequireM4a)
            if (fallbackData != null) {
                Timber.tag(TAG).d("Resolved $videoId via NewPipeExtractor fallback")
                val ttlMs = (fallbackData.streamExpiresInSeconds.toLong() * 1000L).coerceIn(30_000L, 5 * 60 * 1000L)
                playbackDataCache[cacheKey] = CachedPlaybackData(fallbackData, System.currentTimeMillis() + ttlMs)
                return Result.success(fallbackData)
            }

            Result.failure(lastInnerTubeError ?: IOException("All extraction tiers failed for $videoId"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: StreamResolveException) {
            val cause = error.cause
            Result.failure(
                if (error.reason == StreamResolveException.Reason.NETWORK && cause != null) {
                    cause
                } else {
                    error
                },
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    /**
     * Resolves the stream specifically for offline audio file download.
     * Selects AAC itag 140 (audio/mp4) for embedding ID3/MP4 metadata and cover art.
     */
    suspend fun playerResponseForDownload(
        videoId: String,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = playerResponseForPlayback(
        videoId = videoId,
        audioQuality = AudioQuality.LOW,
        connectivityManager = connectivityManager,
        allowBoundedRange = false,
    )

    /**
     * Pre-flight probe testing whether googlevideo CDN returns 200/206 audio bytes.
     */
    private fun probeStreamUrl(url: String, headers: Map<String, String>): Boolean {
        return try {
            val window = PlayerClient.rangeBytesFor(url)
            val rangeHeader = if (window != Long.MAX_VALUE) "bytes=0-${window - 1}" else "bytes=0-1"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", rangeHeader)

            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

            probeClient.newCall(requestBuilder.build()).execute().use { response ->
                val contentType = response.header("Content-Type")
                val isSuccessStatus = response.code in 200..299 || response.code == 416
                val isAudio = contentType?.startsWith("audio/") == true
                val hasBodyBytes = response.body?.source()?.request(PROBE_READ_BYTES) == true

                isSuccessStatus && isAudio && hasBodyBytes
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w("Probe request threw exception for $url: ${e.message}")
            false
        }
    }

    /**
     * Failsafe extraction using NewPipeExtractor when InnerTubeX cannot resolve a playable stream.
     */
    private suspend fun extractViaNewPipe(videoId: String, requireM4a: Boolean): PlaybackData? {
        initNewPipe
        return newPipeExtractionGate.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                    extractor.fetchPage()

                    val audioStreams = extractor.audioStreams
                        .filter { !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }

                    val pickedStream: AudioStream = if (requireM4a) {
                        audioStreams.firstOrNull { it.format == MediaFormat.M4A }
                            ?: audioStreams.maxByOrNull { it.averageBitrate }
                    } else {
                        audioStreams.firstOrNull { it.format == MediaFormat.WEBMA_OPUS }
                            ?: audioStreams.maxByOrNull { it.averageBitrate }
                    } ?: return@withContext null

                    val streamUrl = pickedStream.content
                    val client = PlayerClient.forStreamUrl(streamUrl)
                    val headers = client.mediaHeaders()

                    val fullMimeType = pickedStream.format?.mimeType ?: "audio/webm"
                    val bitrate = pickedStream.averageBitrate * 1000

                    PlaybackData(
                        audioConfig = null,
                        videoDetails = PlayerResponse.VideoDetails(
                            videoId = videoId,
                            title = extractor.name.orEmpty(),
                            author = extractor.uploaderName.orEmpty(),
                            channelId = extractor.uploaderUrl.orEmpty(),
                            lengthSeconds = extractor.length.toString(),
                            musicVideoType = null,
                            viewCount = extractor.viewCount.takeIf { it >= 0 }?.toString(),
                            thumbnail = Thumbnails(emptyList()),
                        ),
                        playbackTracking = null,
                        format = PlayerResponse.StreamingData.Format(
                            itag = pickedStream.itag,
                            url = streamUrl,
                            mimeType = fullMimeType,
                            bitrate = bitrate,
                            width = null,
                            height = null,
                            contentLength = null,
                            quality = "",
                            fps = null,
                            qualityLabel = null,
                            averageBitrate = bitrate,
                            audioQuality = null,
                            approxDurationMs = (extractor.length * 1000L).toString(),
                            audioSampleRate = null,
                            audioChannels = null,
                            loudnessDb = null,
                            lastModified = null,
                            signatureCipher = null,
                            cipher = null,
                            audioTrack = null,
                        ),
                        streamUrl = streamUrl,
                        streamExpiresInSeconds = DEFAULT_STREAM_TTL_SECONDS,
                        streamClient = client.clientName,
                        streamHeaders = headers,
                        requireBoundedRange = true,
                        rangeChunkSizeBytes = PlayerClient.rangeBytesFor(streamUrl),
                        useRangeChunks = true,
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).e("NewPipe fallback extraction failed for $videoId: ${e.message}")
                    null
                }
            }
        }
    }

    internal fun markStreamClientFailed(
        videoId: String,
        clientName: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        playbackDataCache.keys.removeIf { it.startsWith("${videoId}_") }
        streamClientFailures.compute(videoId) { _, failures ->
            FailedStreamClients(failures?.clientNames.orEmpty() + clientName, nowMs)
        }
    }

    fun clearStreamClientFailures() {
        streamClientFailures.clear()
        playbackDataCache.clear()
    }

    suspend fun refreshAfterStreamRejection(): Boolean = bundle().cipherService.refreshAfterStreamRejection()

    internal fun failedStreamClients(
        videoId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Set<String> {
        val failures = streamClientFailures[videoId] ?: return emptySet()
        if ((nowMs - failures.failedAtMs) !in 0 until STREAM_CLIENT_FAILURE_TTL_MS) {
            streamClientFailures.remove(videoId, failures)
            return emptySet()
        }
        return failures.clientNames
    }

    private suspend fun bundle(): ExtractionBundle {
        val transport = YouTube.extractionTransport()
        currentBundle?.takeIf { it.transportGeneration == transport.generation }?.let { return it }
        return bundleMutex.withLock {
            val latestTransport = YouTube.extractionTransport()
            currentBundle?.takeIf { it.transportGeneration == latestTransport.generation }?.let { return@withLock it }
            try {
                currentBundle?.cipherService?.dispose()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(
                    com.metrolist.innertubex.InnerTubeLogEvent(
                        level = InnerTubeLogLevel.WARN,
                        tag = TAG,
                        message = "old cipher disposal failed",
                        details = mapOf("exceptionType" to (error::class.simpleName ?: "unknown")),
                    ),
                )
            }

            val remoteStore = RemotePlayerConfigStore(latestTransport.httpClient, configRepository, logger)
            val cipherService = YouTubeCipherService(latestTransport.httpClient, remoteStore, logger)
            val extractor =
                InnerTubeExtractor(
                    configParser =
                        YtConfigParserImpl(
                            latestTransport.httpClient,
                            latestTransport.innerTube,
                            remoteStore,
                            logger,
                        ).withEmbeddedConfigFallback(),
                    cipherService = cipherService,
                    innerTube = latestTransport.innerTube,
                    tokenProvider = tokenProvider,
                    logger = logger,
                )

            ExtractionBundle(
                transportGeneration = latestTransport.generation,
                cipherService = cipherService,
                extractor = extractor,
            ).also { currentBundle = it }
        }
    }

    private val configRepository: PlayerConfigRepository by lazy {
        AndroidPlayerConfigRepository(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val poTokenGenerator: PoTokenGenerator by lazy {
        PoTokenGenerator(requireNotNull(applicationContext) { "InnerTubeXPlayer is not initialized" })
    }

    private val tokenProvider =
        object : TokenProvider {
            override val capabilities: TokenProviderCapabilities
                get() =
                    TokenProviderCapabilities(
                        providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
                        usesWebView = true,
                    )

            override suspend fun getPoToken(
                videoId: String,
                visitorData: String,
                cookie: String?,
            ): PoTokenResult? =
                poTokenGenerator.getWebClientPoToken(videoId, visitorData)?.let { token ->
                    PoTokenResult(
                        playerRequestToken = token.playerRequestPoToken,
                        streamingDataToken = token.streamingDataPoToken,
                        visitorData = visitorData,
                    )
                }

            override suspend fun close() {
                poTokenGenerator.close()
            }
        }

    private val logger =
        InnerTubeLogger { event ->
            val details = event.details.entries.joinToString(prefix = " [", postfix = "]") { "${it.key}=${it.value}" }
            val message = event.message + details.takeUnless { event.details.isEmpty() }.orEmpty()
            when (event.level) {
                InnerTubeLogLevel.DEBUG -> Timber.tag(event.tag).d(message)
                InnerTubeLogLevel.INFO -> Timber.tag(event.tag).i(message)
                InnerTubeLogLevel.WARN -> Timber.tag(event.tag).w(message)
                InnerTubeLogLevel.ERROR -> Timber.tag(event.tag).e(message)
            }
        }

    class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String,
        val streamHeaders: Map<String, String>,
        val requireBoundedRange: Boolean,
        val rangeChunkSizeBytes: Long,
        val useRangeChunks: Boolean,
    )

    private data class ExtractionBundle(
        val transportGeneration: Long,
        val cipherService: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )

    private data class FailedStreamClients(
        val clientNames: Set<String>,
        val failedAtMs: Long,
    )

    private class AndroidPlayerConfigRepository(context: Context) : PlayerConfigRepository {
        private val preferences = context.getSharedPreferences("innertubex_player_config", Context.MODE_PRIVATE)

        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = preferences.getString("json", "").orEmpty()
            set(value) = preferences.edit().putString("json", value).apply()
        override var cachedAtMs: Long
            get() = preferences.getLong("cached_at_ms", 0L)
            set(value) = preferences.edit().putLong("cached_at_ms", value).apply()
        override var cachedSourceUrl: String
            get() = preferences.getString("source_url", "").orEmpty()
            set(value) = preferences.edit().putString("source_url", value).apply()
        override var cachedEtag: String
            get() = preferences.getString("etag", "").orEmpty()
            set(value) = preferences.edit().putString("etag", value).apply()

        private companion object {
            const val PLAYER_CONFIG_URL =
                "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        }
    }

    internal fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
        object : YtConfigParser by this {
            override suspend fun fetchConfig(
                videoId: String,
                useLoginCookies: Boolean,
            ) =
                try {
                    this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
                } catch (_: IllegalStateException) {
                    this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
                }
        }

    private fun AudioQuality.toInnerTubeX(connectivityManager: ConnectivityManager): InnerTubeXAudioQuality =
        when (this) {
            AudioQuality.HIGH -> InnerTubeXAudioQuality.HIGH
            AudioQuality.LOW -> InnerTubeXAudioQuality.LOW
            AudioQuality.AUTO ->
                if (connectivityManager.isActiveNetworkMetered) {
                    InnerTubeXAudioQuality.LOW
                } else {
                    InnerTubeXAudioQuality.AUTO
                }
        }

    private fun ExtractedStream.toPlaybackData(resolvedHeaders: Map<String, String> = headers): PlaybackData {
        val metadata = mediaMetadata
        val tracking = playbackTracking
        val fullMimeType =
            if (codecs.isNullOrBlank()) {
                mimeType.orEmpty()
            } else {
                "${mimeType.orEmpty()}; codecs=\"$codecs\""
            }
        return PlaybackData(
            audioConfig =
                if (loudnessDb != null || perceptualLoudnessDb != null) {
                    PlayerResponse.PlayerConfig.AudioConfig(loudnessDb, perceptualLoudnessDb)
                } else {
                    null
                },
            videoDetails =
                metadata?.let {
                    PlayerResponse.VideoDetails(
                        videoId = videoId,
                        title = it.title,
                        author = it.author,
                        channelId = it.channelId.orEmpty(),
                        lengthSeconds = it.durationSeconds?.toString().orEmpty(),
                        musicVideoType = it.musicVideoType,
                        viewCount = it.viewCount,
                        thumbnail =
                            Thumbnails(
                                it.thumbnails.map { thumbnail ->
                                    Thumbnail(thumbnail.url, thumbnail.width, thumbnail.height)
                                },
                            ),
                    )
                },
            playbackTracking =
                tracking?.let {
                    PlayerResponse.PlaybackTracking(
                        videostatsPlaybackUrl =
                            it.playbackUrl?.let(PlayerResponse.PlaybackTracking::VideostatsPlaybackUrl),
                        videostatsWatchtimeUrl =
                            it.watchtimeUrl?.let(PlayerResponse.PlaybackTracking::VideostatsWatchtimeUrl),
                        atrUrl = null,
                    )
                },
            format =
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = audioUrl,
                    mimeType = fullMimeType,
                    bitrate = bitrate ?: 0,
                    width = null,
                    height = null,
                    contentLength = contentLengthBytes,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = bitrate,
                    audioQuality = null,
                    approxDurationMs = metadata?.durationSeconds?.times(1000)?.toString(),
                    audioSampleRate = sampleRate,
                    audioChannels = null,
                    loudnessDb = loudnessDb,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                ),
            streamUrl = audioUrl,
            streamExpiresInSeconds =
                expiresAt
                    ?.let { ((it.toEpochMilliseconds() - System.currentTimeMillis()) / 1000L).toInt() }
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_STREAM_TTL_SECONDS,
            streamClient = clientName,
            streamHeaders = resolvedHeaders,
            requireBoundedRange = this.requireBoundedRange,
            rangeChunkSizeBytes = this.rangeChunkSizeBytes,
            useRangeChunks = this.useRangeChunks,
        )
    }
}
