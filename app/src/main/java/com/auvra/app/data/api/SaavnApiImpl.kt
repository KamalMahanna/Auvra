package com.auvra.app.data.api

import android.util.Base64
import com.auvra.app.data.model.*
import com.auvra.app.utils.SongDeduplicator
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaavnApiImpl @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : SaavnApi {

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    )

    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)

    private val channelStations = mapOf(
        "27" to StationInfo("Pyaar Ka Safar", "hindi", "Romance"),
        "16" to StationInfo("Chill Karo", "hindi", "Chill"),
        "17" to StationInfo("Party Karlo", "hindi", "Party"),
        "29" to StationInfo("Workout Karo", "hindi", "Workout"),
        "46" to StationInfo("90s Nostalgia", "hindi", "90s Nostalgia"),
        "69" to StationInfo("Desi Hip Hop", "hindi", "Desi Hip Hop"),
        "28" to StationInfo("Bollywood Retro 70s-80s", "hindi", "Retro"),
        "30" to StationInfo("Bhakti Rachna", "hindi", "Devotional"),
        "32" to StationInfo("Sufiyana Safar", "hindi", "Sufi"),
        "76" to StationInfo("Unlimited Khushiyan", "hindi", "Happy"),
        "40" to StationInfo("Remix Dhamaal", "hindi", "Dance"),
        "15" to StationInfo("Hindi Superhits", "hindi", "Pop"),
        "134" to StationInfo("Hindi Superhits", "hindi", "2010s"),
        "143" to StationInfo("Bollywood Retro 70s-80s", "hindi", "2000s"),
        "187" to StationInfo("Hindi Superhits", "hindi", "Best of 2022"),
        "180" to StationInfo("Hindi Superhits", "hindi", "Best of 2021"),
        "164" to StationInfo("Hindi Superhits", "hindi", "Best of 2020"),
        "126" to StationInfo("Hindi Superhits", "hindi", "Best of 2019"),
        "123" to StationInfo("Chill Karo", "hindi", "Travel"),
        "72" to StationInfo("Party Karlo", "hindi", "Wedding"),
        "73" to StationInfo("Baal Geet", "hindi", "Kids"),
        "139" to StationInfo("Hindi Superhits", "hindi", "Superstars"),
        "49" to StationInfo("Hindustani Sangeet", "hindi", "Hindustani Classical"),
        "47" to StationInfo("Hindustani Sangeet", "hindi", "Carnatic"),
        "149" to StationInfo("Hindi Superhits", "hindi", "Throwback Top 20"),
        "21" to StationInfo("Ghazal", "hindi", "Ghazal")
    )

    data class StationInfo(val name: String, val language: String, val title: String)

    private suspend fun useFetch(
        endpoint: String,
        params: Map<String, String>,
        context: String = "web6dot0"
    ): String {
        val urlBuilder = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
        urlBuilder.addQueryParameter("__call", endpoint)
        urlBuilder.addQueryParameter("_format", "json")
        urlBuilder.addQueryParameter("_marker", "0")
        urlBuilder.addQueryParameter("api_version", "4")
        urlBuilder.addQueryParameter("ctx", context)
        
        params.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }

        val requestUrl = urlBuilder.build()
        val randomUserAgent = userAgents.random()

        var lastException: Exception? = null
        for (attempt in 1..5) {
            try {
                val request = Request.Builder()
                    .url(requestUrl)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", randomUserAgent)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 404) {
                            throw Exception("HTTP 404 Not Found")
                        } else {
                            throw Exception("HTTP Error Status: ${response.code}")
                        }
                    }
                    val bodyString = response.body?.string() ?: throw Exception("Empty body")
                    return bodyString
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt < 5) {
                    delay(1000)
                }
            }
        }
        throw lastException ?: Exception("Fetch failed after 5 attempts")
    }

    private fun parseJsonToMap(json: String): Map<String, Any?>? {
        return try {
            moshi.adapter<Map<String, Any?>>(mapType).fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun createDownloadLinks(encryptedUrl: String?): List<DownloadLink> {
        if (encryptedUrl.isNullOrEmpty()) return emptyList()
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec("38346591".toByteArray(), "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decryptedBytes = cipher.doFinal(Base64.decode(encryptedUrl, Base64.DEFAULT))
            val decryptedLink = String(decryptedBytes, Charsets.UTF_8).trim()
            
            val qualities = listOf(
                Pair("_12", "12kbps"),
                Pair("_48", "48kbps"),
                Pair("_96", "96kbps"),
                Pair("_160", "160kbps"),
                Pair("_320", "320kbps")
            )
            qualities.map { (id, bitrate) ->
                DownloadLink(
                    quality = bitrate,
                    url = decryptedLink.replace("_96", id)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun createImageLinks(url: String?): List<DownloadLink> {
        if (url.isNullOrEmpty()) return emptyList()
        val qualities = listOf("50x50", "150x150", "500x500")
        val qualityRegex = Regex("150x150|50x50")
        val protocolRegex = Regex("^http://")
        return qualities.map { quality ->
            val newUrl = url.replace(qualityRegex, quality).replace(protocolRegex, "https://")
            DownloadLink(quality = quality, url = newUrl)
        }
    }

    private fun parseSong(map: Map<String, Any?>?): Song? {
        if (map == null) return null
        val id = map["id"] as? String ?: return null
        val name = map["title"] as? String ?: map["song"] as? String ?: ""
        val type = map["type"] as? String ?: "song"
        val year = map["year"] as? String
        val language = map["language"] as? String ?: ""
        val permaUrl = map["perma_url"] as? String ?: ""
        val image = map["image"] as? String ?: ""
        val playCount = (map["play_count"] as? String)?.toIntOrNull() ?: (map["play_count"] as? Number)?.toInt()

        val moreInfo = map["more_info"] as? Map<String, Any?>
        val releaseDate = moreInfo?.get("release_date") as? String
        val duration = (moreInfo?.get("duration") as? String)?.toIntOrNull() ?: (moreInfo?.get("duration") as? Number)?.toInt()
        val label = moreInfo?.get("label") as? String
        val explicitContent = map["explicit_content"] == "1"
        val hasLyrics = moreInfo?.get("has_lyrics") == "true"
        val lyricsId = moreInfo?.get("lyrics_id") as? String
        val copyright = moreInfo?.get("copyright_text") as? String

        val albumId = moreInfo?.get("album_id") as? String
        val albumName = moreInfo?.get("album") as? String
        val albumUrl = moreInfo?.get("album_url") as? String
        val album = SongAlbum(id = albumId, name = albumName, url = albumUrl)

        val artistMap = moreInfo?.get("artistMap") as? Map<String, Any?>
        val primaryArtists = parseArtistMaps(artistMap?.get("primary_artists") as? List<Map<String, Any?>>)
        val featuredArtists = parseArtistMaps(artistMap?.get("featured_artists") as? List<Map<String, Any?>>)
        val allArtists = parseArtistMaps(artistMap?.get("artists") as? List<Map<String, Any?>>)
        val artists = SongArtists(primary = primaryArtists, featured = featuredArtists, all = allArtists)

        val encryptedMediaUrl = moreInfo?.get("encrypted_media_url") as? String

        return Song(
            id = id,
            name = name,
            type = type,
            year = year,
            releaseDate = releaseDate,
            duration = duration,
            label = label,
            explicitContent = explicitContent,
            playCount = playCount,
            language = language,
            hasLyrics = hasLyrics,
            lyricsId = lyricsId,
            url = permaUrl,
            copyright = copyright,
            album = album,
            artists = artists,
            image = createImageLinks(image),
            downloadUrl = createDownloadLinks(encryptedMediaUrl)
        )
    }

    private fun parseArtistMap(map: Map<String, Any?>?): ArtistMap? {
        if (map == null) return null
        val id = map["id"] as? String ?: ""
        val name = map["name"] as? String ?: ""
        val role = map["role"] as? String ?: ""
        val type = map["type"] as? String ?: "artist"
        val image = map["image"] as? String ?: ""
        val url = map["perma_url"] as? String ?: ""
        return ArtistMap(
            id = id,
            name = name,
            role = role,
            type = type,
            image = createImageLinks(image),
            url = url
        )
    }

    private fun parseArtistMaps(list: List<Map<String, Any?>>?): List<ArtistMap> {
        return list?.mapNotNull { parseArtistMap(it) } ?: emptyList()
    }

    private fun parseSearchArtist(map: Map<String, Any?>?): SearchArtist? {
        if (map == null) return null
        val id = map["id"] as? String ?: map["artistId"] as? String ?: ""
        val name = map["name"] as? String ?: ""
        val role = map["role"] as? String ?: ""
        val type = map["type"] as? String ?: "artist"
        val image = map["image"] as? String ?: map["image_url"] as? String ?: ""
        val url = map["perma_url"] as? String ?: ""
        return SearchArtist(
            id = id,
            name = name,
            role = role,
            type = type,
            image = createImageLinks(image),
            url = url
        )
    }

    private fun parseAlbum(map: Map<String, Any?>?): Album? {
        if (map == null) return null
        val id = map["id"] as? String ?: map["albumid"] as? String ?: ""
        val name = map["title"] as? String ?: map["name"] as? String ?: ""
        val description = map["header_desc"] as? String
        val type = map["type"] as? String ?: "album"
        val year = (map["year"] as? String)?.toIntOrNull() ?: (map["year"] as? Number)?.toInt()
        val playCount = (map["play_count"] as? String)?.toIntOrNull() ?: (map["play_count"] as? Number)?.toInt()
        val language = map["language"] as? String ?: ""
        val explicitContent = map["explicit_content"] == "1"
        val permaUrl = map["perma_url"] as? String ?: ""

        val moreInfo = map["more_info"] as? Map<String, Any?>
        val songCount = (moreInfo?.get("song_count") as? String)?.toIntOrNull() ?: (moreInfo?.get("song_count") as? Number)?.toInt()

        val artistMap = moreInfo?.get("artistMap") as? Map<String, Any?>
        val primaryArtists = parseArtistMaps(artistMap?.get("primary_artists") as? List<Map<String, Any?>>)
        val featuredArtists = parseArtistMaps(artistMap?.get("featured_artists") as? List<Map<String, Any?>>)
        val allArtists = parseArtistMaps(artistMap?.get("artists") as? List<Map<String, Any?>>)
        val artists = SongArtists(primary = primaryArtists, featured = featuredArtists, all = allArtists)

        val image = map["image"] as? String ?: ""
        val songsList = (map["list"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()

        return Album(
            id = id,
            name = name,
            description = description,
            year = year,
            type = type,
            playCount = playCount,
            language = language,
            explicitContent = explicitContent,
            songCount = songCount,
            url = permaUrl,
            artists = artists,
            image = createImageLinks(image),
            songs = songsList?.mapNotNull { parseSong(it) }
        )
    }

    private fun parsePlaylist(map: Map<String, Any?>?): Playlist? {
        if (map == null) return null
        val id = map["id"] as? String ?: map["listid"] as? String ?: ""
        val name = map["title"] as? String ?: map["name"] as? String ?: ""
        val description = map["header_desc"] as? String
        val type = map["type"] as? String ?: "playlist"
        val year = (map["year"] as? String)?.toIntOrNull() ?: (map["year"] as? Number)?.toInt()
        val playCount = (map["play_count"] as? String)?.toIntOrNull() ?: (map["play_count"] as? Number)?.toInt()
        val language = map["language"] as? String ?: ""
        val explicitContent = map["explicit_content"] == "1"
        val permaUrl = map["perma_url"] as? String ?: ""
        val songCount = (map["list_count"] as? String)?.toIntOrNull() ?: (map["list_count"] as? Number)?.toInt()

        val image = map["image"] as? String ?: ""
        val songsList = (map["list"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()

        return Playlist(
            id = id,
            name = name,
            description = description,
            year = year,
            type = type,
            playCount = playCount,
            language = language,
            explicitContent = explicitContent,
            songCount = songCount,
            url = permaUrl,
            image = createImageLinks(image),
            songs = songsList?.mapNotNull { parseSong(it) }
        )
    }

    private fun parseArtistDetail(map: Map<String, Any?>?): ArtistDetail? {
        if (map == null) return null
        val id = map["artistId"] as? String ?: map["id"] as? String ?: ""
        val name = map["name"] as? String ?: ""
        val permaUrl = (map["urls"] as? Map<*, *>)?.get("overview") as? String ?: map["perma_url"] as? String ?: ""
        val type = map["type"] as? String ?: "artist"
        val followerCount = (map["follower_count"] as? String)?.toIntOrNull() ?: (map["follower_count"] as? Number)?.toInt()
        val fanCount = (map["fan_count"] as? String)?.toIntOrNull() ?: (map["fan_count"] as? Number)?.toInt()
        val isVerified = map["isVerified"] as? Boolean

        val dominantLanguage = map["dominantLanguage"] as? String
        val dominantType = map["dominantType"] as? String

        val circularImage = map["image"] as? String ?: ""

        val rawTopSongs = map["topSongs"]
        val topSongsList = when (rawTopSongs) {
            is List<*> -> rawTopSongs.filterIsInstance<Map<String, Any?>>()
            is Map<*, *> -> (rawTopSongs["songs"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            else -> null
        }

        val rawTopAlbums = map["topAlbums"]
        val topAlbumsList = when (rawTopAlbums) {
            is List<*> -> rawTopAlbums.filterIsInstance<Map<String, Any?>>()
            is Map<*, *> -> (rawTopAlbums["albums"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()
            else -> null
        }

        val singlesList = (map["singles"] as? List<*>)?.filterIsInstance<Map<String, Any?>>()

        return ArtistDetail(
            id = id,
            name = name,
            url = permaUrl,
            type = type,
            image = createImageLinks(circularImage),
            followerCount = followerCount,
            fanCount = fanCount,
            isVerified = isVerified ?: false,
            dominantLanguage = dominantLanguage,
            dominantType = dominantType,
            topSongs = topSongsList?.mapNotNull { parseSong(it) },
            topAlbums = topAlbumsList?.mapNotNull { parseAlbum(it) },
            singles = singlesList?.mapNotNull { parseSong(it) }
        )
    }

    private fun parseModuleItem(map: Map<String, Any?>?): ModuleItem? {
        if (map == null) return null
        val id = map["id"] as? String ?: ""
        val name = map["title"] as? String ?: map["name"] as? String ?: ""
        val subtitle = map["subtitle"] as? String
        val type = map["type"] as? String ?: ""
        val image = map["image"] as? String ?: ""
        val url = map["perma_url"] as? String
        val explicitContent = map["explicit_content"] == "1"
        return ModuleItem(
            id = id,
            name = name,
            subtitle = subtitle,
            type = type,
            image = createImageLinks(image),
            url = url,
            explicitContent = explicitContent
        )
    }



    override suspend fun searchSongs(
        query: String,
        page: Int,
        limit: Int
    ): ApiResponse<SearchSongResult> {
        return try {
            val json = useFetch(
                endpoint = "search.getResults",
                params = mapOf("q" to query, "p" to page.toString(), "n" to limit.toString())
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val total = (map["total"] as? Number)?.toInt() ?: ((map["total"] as? String)?.toIntOrNull() ?: 0)
            val start = (map["start"] as? Number)?.toInt() ?: ((map["start"] as? String)?.toIntOrNull() ?: 0)
            val resultsList = (map["results"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val results = resultsList.mapNotNull { parseSong(it) }.take(limit)
            ApiResponse(true, SearchSongResult(total = total, start = start, results = results))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun searchArtists(
        query: String,
        page: Int,
        limit: Int
    ): ApiResponse<SearchArtistResult> {
        return try {
            val json = useFetch(
                endpoint = "search.getArtistResults",
                params = mapOf("q" to query, "p" to page.toString(), "n" to limit.toString())
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val total = (map["total"] as? Number)?.toInt() ?: ((map["total"] as? String)?.toIntOrNull() ?: 0)
            val start = (map["start"] as? Number)?.toInt() ?: ((map["start"] as? String)?.toIntOrNull() ?: 0)
            val resultsList = (map["results"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val results = resultsList.mapNotNull { parseSearchArtist(it) }.take(limit)
            ApiResponse(true, SearchArtistResult(total = total, start = start, results = results))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun searchAlbums(
        query: String,
        page: Int,
        limit: Int
    ): ApiResponse<SearchAlbumResult> {
        return try {
            val json = useFetch(
                endpoint = "search.getAlbumResults",
                params = mapOf("q" to query, "p" to page.toString(), "n" to limit.toString())
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val total = (map["total"] as? Number)?.toInt() ?: ((map["total"] as? String)?.toIntOrNull() ?: 0)
            val start = (map["start"] as? Number)?.toInt() ?: ((map["start"] as? String)?.toIntOrNull() ?: 0)
            val resultsList = (map["results"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val results = resultsList.mapNotNull { parseAlbum(it) }.take(limit)
            ApiResponse(true, SearchAlbumResult(total = total, start = start, results = results))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun searchPlaylists(
        query: String,
        page: Int,
        limit: Int
    ): ApiResponse<SearchPlaylistResult> {
        return try {
            val json = useFetch(
                endpoint = "search.getPlaylistResults",
                params = mapOf("q" to query, "p" to page.toString(), "n" to limit.toString())
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val total = (map["total"] as? Number)?.toInt() ?: ((map["total"] as? String)?.toIntOrNull() ?: 0)
            val start = (map["start"] as? Number)?.toInt() ?: ((map["start"] as? String)?.toIntOrNull() ?: 0)
            val resultsList = (map["results"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
            val results = resultsList.mapNotNull { parsePlaylist(it) }.take(limit)
            ApiResponse(true, SearchPlaylistResult(total = total, start = start, results = results))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }


    override suspend fun getSongById(id: String): ApiResponse<List<Song>> {
        return try {
            val json = useFetch(
                endpoint = "song.getDetails",
                params = mapOf("pids" to id)
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val songsList = mutableListOf<Song>()
            val rawSongs = map["songs"] as? List<*>
            if (rawSongs != null) {
                rawSongs.filterIsInstance<Map<String, Any?>>().forEach { songMap ->
                    parseSong(songMap)?.let { songsList.add(it) }
                }
            } else {
                map.forEach { (_, value) ->
                    if (value is Map<*, *>) {
                        parseSong(value as? Map<String, Any?>)?.let { songsList.add(it) }
                    }
                }
            }
            ApiResponse(true, songsList)
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun getSongSuggestions(
        songId: String,
        limit: Int
    ): ApiResponse<List<Song>> {
        return try {
            val stationJson = useFetch(
                endpoint = "webradio.createEntityStation",
                params = mapOf("entity_id" to """["$songId"]""", "entity_type" to "queue"),
                context = "android"
            )
            val stationMap = parseJsonToMap(stationJson) ?: return ApiResponse(false, null)
            val stationId = stationMap["stationid"] as? String ?: return ApiResponse(false, null)

            val suggestionsJson = useFetch(
                endpoint = "webradio.getSong",
                params = mapOf("stationid" to stationId, "k" to limit.toString()),
                context = "android"
            )
            val suggestionsMap = parseJsonToMap(suggestionsJson) ?: return ApiResponse(false, null)

            val suggestions = mutableListOf<Song>()
            suggestionsMap.forEach { (key, value) ->
                if (key != "stationid") {
                    val elementMap = value as? Map<String, Any?>
                    val songMap = elementMap?.get("song") as? Map<String, Any?>
                    parseSong(songMap)?.let { suggestions.add(it) }
                }
            }
            ApiResponse(true, suggestions.take(limit))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun getArtistById(
        id: String,
        songCount: Int,
        albumCount: Int
    ): ApiResponse<ArtistDetail> {
        return try {
            val json = useFetch(
                endpoint = "artist.getArtistPageDetails",
                params = mapOf(
                    "artistId" to id,
                    "n_song" to songCount.toString(),
                    "n_album" to albumCount.toString(),
                    "page" to "0",
                    "sort_order" to "desc",
                    "category" to "popularity"
                ),
                context = "android"
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val artistDetail = parseArtistDetail(map) ?: return ApiResponse(false, null)
            ApiResponse(true, artistDetail)
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun getAlbumById(id: String): ApiResponse<Album> {
        return try {
            val json = useFetch(
                endpoint = "content.getAlbumDetails",
                params = mapOf("albumid" to id)
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val album = parseAlbum(map) ?: return ApiResponse(false, null)
            ApiResponse(true, album)
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    override suspend fun getPlaylistById(
        id: String,
        page: Int,
        limit: Int
    ): ApiResponse<Playlist> {
        return try {
            val stationInfo = channelStations[id]
            if (stationInfo != null) {
                var songs = emptyList<Song>()
                try {
                    val stationJson = useFetch(
                        endpoint = "webradio.createFeaturedStation",
                        params = mapOf("name" to stationInfo.name, "language" to stationInfo.language),
                        context = "android"
                    )
                    val stationMap = parseJsonToMap(stationJson)
                    val stationId = stationMap?.get("stationid") as? String
                    if (stationId != null) {
                        val suggestionsJson = useFetch(
                            endpoint = "webradio.getSong",
                            params = mapOf("stationid" to stationId, "k" to "50"),
                            context = "android"
                        )
                        val suggestionsMap = parseJsonToMap(suggestionsJson)
                        val list = mutableListOf<Song>()
                        suggestionsMap?.forEach { (key, value) ->
                            if (key != "stationid") {
                                val elementMap = value as? Map<String, Any?>
                                val songMap = elementMap?.get("song") as? Map<String, Any?>
                                parseSong(songMap)?.let { list.add(it) }
                            }
                        }
                        songs = list
                    }
                } catch (e: Exception) {
                    // ignore
                }

                if (songs.isEmpty()) {
                    try {
                        val searchJson = useFetch(
                            endpoint = "search.getPlaylistResults",
                            params = mapOf("q" to stationInfo.title, "p" to "0", "n" to "5")
                        )
                        val searchMap = parseJsonToMap(searchJson)
                        val resultsList = (searchMap?.get("results") as? List<*>)?.filterIsInstance<Map<String, Any?>>()
                        val playlistId = resultsList?.firstOrNull()?.get("id") as? String
                        if (playlistId != null) {
                            val playlistJson = useFetch(
                                endpoint = "playlist.getDetails",
                                params = mapOf("listid" to playlistId, "n" to "50", "p" to "0")
                            )
                            val playlistMap = parseJsonToMap(playlistJson)
                            val playlist = parsePlaylist(playlistMap)
                            if (playlist != null) {
                                val uniqueSongs = SongDeduplicator.deduplicate(playlist.songs)
                                return ApiResponse(true, playlist.copy(id = id, name = stationInfo.title + " Radio", songCount = uniqueSongs.size, songs = uniqueSongs))
                            }
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }

                val uniqueSongs = SongDeduplicator.deduplicate(songs)
                val defaultPlaylist = Playlist(
                    id = id,
                    name = stationInfo.title + " Radio",
                    description = "Featured ${stationInfo.title} curated dynamically from JioSaavn radio.",
                    year = Calendar.getInstance().get(Calendar.YEAR),
                    type = "playlist",
                    playCount = 999999,
                    language = stationInfo.language,
                    explicitContent = false,
                    songCount = uniqueSongs.size,
                    url = "",
                    image = listOf(
                        DownloadLink("50x50", "https://c.saavncdn.com/editorial/LoveNotes_saavn_radio_20211115090755_150x150.jpg"),
                        DownloadLink("150x150", "https://c.saavncdn.com/editorial/LoveNotes_saavn_radio_20211115090755_150x150.jpg"),
                        DownloadLink("500x500", "https://c.saavncdn.com/editorial/LoveNotes_saavn_radio_20211115090755_150x150.jpg")
                    ),
                    songs = uniqueSongs
                )
                return ApiResponse(true, defaultPlaylist)
            }

            val json = useFetch(
                endpoint = "playlist.getDetails",
                params = mapOf("listid" to id, "n" to "1000", "p" to "0")
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val playlist = parsePlaylist(map) ?: return ApiResponse(false, null)
            val uniqueSongs = SongDeduplicator.deduplicate(playlist.songs)
            ApiResponse(true, playlist.copy(songCount = uniqueSongs.size, songs = uniqueSongs))
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }

    private fun formatSectionTitle(key: String): String {
        return key.split("_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    override suspend fun getModules(): ApiResponse<List<ModuleSection>> {
        return try {
            val json = useFetch(
                endpoint = "content.getBrowseModules",
                params = emptyMap()
            )
            val map = parseJsonToMap(json) ?: return ApiResponse(false, null)
            val sections = mutableListOf<ModuleSection>()
            var position = 0

            map.forEach { (key, value) ->
                val isArray = value is List<*>
                val title = if (isArray) {
                    formatSectionTitle(key)
                } else {
                    val sectionMap = value as? Map<String, Any?>
                    sectionMap?.get("title") as? String ?: formatSectionTitle(key)
                }
                
                val subtitle = if (isArray) {
                    null
                } else {
                    val sectionMap = value as? Map<String, Any?>
                    sectionMap?.get("subtitle") as? String
                }

                val source = if (isArray) {
                    key
                } else {
                    val sectionMap = value as? Map<String, Any?>
                    sectionMap?.get("source") as? String ?: key
                }

                val pos = if (isArray) {
                    position++
                } else {
                    val sectionMap = value as? Map<String, Any?>
                    val rawPos = sectionMap?.get("position")
                    (rawPos as? String)?.toIntOrNull() ?: (rawPos as? Number)?.toInt() ?: position++
                }

                val dataList = when (value) {
                    is List<*> -> value.filterIsInstance<Map<String, Any?>>()
                    is Map<*, *> -> (value["data"] as? List<*>)?.filterIsInstance<Map<String, Any?>>() ?: emptyList()
                    else -> emptyList()
                }

                val data = dataList.mapNotNull { parseModuleItem(it) }

                if (data.isNotEmpty()) {
                    sections.add(
                        ModuleSection(
                            title = title,
                            subtitle = subtitle,
                            source = source,
                            position = pos,
                            data = data
                        )
                    )
                }
            }

            sections.sortBy { it.position }
            ApiResponse(true, sections)
        } catch (e: Exception) {
            ApiResponse(false, null)
        }
    }
}
