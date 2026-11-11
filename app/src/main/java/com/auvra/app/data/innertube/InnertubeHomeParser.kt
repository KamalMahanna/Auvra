package com.auvra.app.data.innertube

import com.auvra.app.data.model.HomeFeedData
import com.auvra.app.data.model.HomeShelf
import com.auvra.app.data.model.ShelfItem
import com.auvra.app.data.model.shelfLayoutFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.Locale

object InnertubeHomeParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseHomeJson(jsonString: String): HomeFeedData {
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
            ?: return HomeFeedData(emptyList(), null)
        val shelves = parseHome(root)
        val continuation = continuationToken(root)
        return HomeFeedData(shelves, continuation)
    }

    fun parseHomeContinuationJson(jsonString: String): HomeFeedData {
        val root = runCatching { json.parseToJsonElement(jsonString).jsonObject }.getOrNull()
            ?: return HomeFeedData(emptyList(), null)
        val shelves = parseHomeContinuation(root)
        val continuation = continuationToken(root)
        return HomeFeedData(shelves, continuation)
    }

    fun parseHome(response: JsonObject): List<HomeShelf> {
        val sections = response.o("contents")
            ?.o("singleColumnBrowseResultsRenderer")?.a("tabs")?.firstOrNull()
            ?.o("tabRenderer")?.o("content")?.o("sectionListRenderer")?.a("contents")
            .orEmpty()

        return sections.mapNotNull { section ->
            section.o("musicCarouselShelfRenderer")?.let(::carouselShelf)
                ?: section.o("musicShelfRenderer")?.let(::plainShelf)
        }
    }

    fun parseHomeContinuation(root: JsonElement): List<HomeShelf> {
        val out = mutableListOf<HomeShelf>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node["musicCarouselShelfRenderer"] as? JsonObject)
                        ?.let(::carouselShelf)?.let(out::add)
                    (node["musicShelfRenderer"] as? JsonObject)
                        ?.let(::plainShelf)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    fun continuationToken(root: JsonElement): String? {
        val endpoint = collectRenderers(root, "continuationItemRenderer").firstOrNull()
            ?.o("continuationEndpoint")
        (endpoint?.o("continuationCommand")
            ?: endpoint?.o("commandExecutorCommand")?.a("commands")
                ?.firstNotNullOfOrNull { it.o("continuationCommand") })
            ?.s("token")
            ?.let { return it }
        return collectRenderers(root, "nextContinuationData").firstOrNull()?.s("continuation")
    }

    private fun carouselShelf(carousel: JsonObject): HomeShelf? {
        val header = carousel.o("header")?.o("musicCarouselShelfBasicHeaderRenderer")
        val title = header?.o("title").runs()
        val strapline = header?.o("strapline").runs()
        val contents = carousel.a("contents").orEmpty()
        val parsed = contents.mapNotNull { item ->
            parseTwoRowItem(item.o("musicTwoRowItemRenderer"))
                ?: parseResponsiveListItem(item.o("musicResponsiveListItemRenderer"))
                ?: parseArtistRow(item.o("musicResponsiveListItemRenderer"))
        }
        val items = if (VIDEO_WORD.containsMatchIn(title)) parsed.filter { it.videoId != null } else parsed
        if (items.isEmpty()) return null
        val shelfTitle = title.ifBlank { "For you" }
        val fromList = contents.firstOrNull()?.o("musicResponsiveListItemRenderer") != null
        return HomeShelf(shelfTitle, items, strapline, layout = shelfLayoutFor(shelfTitle, items, fromList))
    }

    private fun plainShelf(shelf: JsonObject): HomeShelf? {
        val title = shelf.o("title").runs()
        val items = shelf.a("contents").orEmpty().mapNotNull {
            parseResponsiveListItem(it.o("musicResponsiveListItemRenderer"))
        }
        if (items.isEmpty()) return null
        val shelfTitle = title.ifBlank { "For you" }
        return HomeShelf(shelfTitle, items, layout = shelfLayoutFor(shelfTitle, items, fromList = true))
    }

    private fun parseTwoRowItem(renderer: JsonObject?): ShelfItem? {
        if (renderer == null) return null
        val title = renderer.o("title").runs()
        if (title.isBlank()) return null
        val endpoint = renderer.o("navigationEndpoint")
        val browseId = endpoint?.o("browseEndpoint")?.s("browseId")
        val videoId = endpoint?.o("watchEndpoint")?.s("videoId")
            ?: browseId?.takeIf { it.startsWith("MPED") }?.removePrefix("MPED")
        val resolvedBrowseId = browseId?.takeUnless { it.startsWith("MPED") }
        val thumbnails = renderer.o("thumbnailRenderer")?.o("musicThumbnailRenderer")
            ?.o("thumbnail")?.a("thumbnails")
        val subtitle = renderer.o("subtitle").runs()
        val isVideo = resolvedBrowseId == null && videoId != null && (
            thumbnails.isNotSquare() ||
                renderer.s("aspectRatio")?.contains("16_9") == true
            )
        if (resolvedBrowseId != null &&
            !resolvedBrowseId.startsWith("VL") &&
            !resolvedBrowseId.startsWith("PL") &&
            !resolvedBrowseId.startsWith("MPRE") &&
            !resolvedBrowseId.startsWith("UC") &&
            (VIDEO_WORD.containsMatchIn(title) || VIDEO_WORD.containsMatchIn(subtitle))
        ) {
            return null
        }
        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = videoId,
            browseId = resolvedBrowseId,
            isVideo = isVideo,
        )
    }

    private fun parseResponsiveListItem(renderer: JsonObject?): ShelfItem? {
        if (renderer == null) return null
        val videoId = renderer.o("playlistItemData")?.s("videoId")
            ?: renderer.o("overlay")
                ?.o("musicItemThumbnailOverlayRenderer")?.o("content")
                ?.o("musicPlayButtonRenderer")?.o("playNavigationEndpoint")
                ?.o("watchEndpoint")?.s("videoId")
            ?: return null

        val columns = renderer.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0)
            ?.o("musicResponsiveListItemFlexColumnRenderer")?.o("text").runs()
        if (title.isBlank()) return null

        val subtitleRuns = columns.getOrNull(1)
            ?.o("musicResponsiveListItemFlexColumnRenderer")?.o("text")?.a("runs").orEmpty()
        val subtitle = subtitleRuns.joinToString("") { it.s("text").orEmpty() }
        val parts = subtitle.split(" • ").filter { it.isNotBlank() }
        val rowType = parts.firstOrNull { it.lowercase(Locale.ROOT) in TYPE_WORDS }?.lowercase(Locale.ROOT)
        val thumbnails = renderer.o("thumbnail")?.o("musicThumbnailRenderer")
            ?.o("thumbnail")?.a("thumbnails")
        val isVideo = rowType == "video" || thumbnails.isNotSquare()

        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = videoId,
            browseId = null,
            isVideo = isVideo,
        )
    }

    private fun parseArtistRow(renderer: JsonObject?): ShelfItem? {
        if (renderer == null) return null
        val endpoint = renderer.o("navigationEndpoint")?.o("browseEndpoint")
        val pageType = endpoint?.o("browseEndpointContextSupportedConfigs")
            ?.o("browseEndpointContextMusicConfig")?.s("pageType").orEmpty()
        if ("ARTIST" !in pageType) return null
        val browseId = endpoint?.s("browseId") ?: return null

        val columns = renderer.a("flexColumns").orEmpty()
        val title = columns.getOrNull(0)
            ?.o("musicResponsiveListItemFlexColumnRenderer")?.o("text").runs()
        if (title.isBlank()) return null
        val subtitle = columns.getOrNull(1)
            ?.o("musicResponsiveListItemFlexColumnRenderer")?.o("text").runs()

        val thumbnails = renderer.o("thumbnail")?.o("musicThumbnailRenderer")
            ?.o("thumbnail")?.a("thumbnails")
        return ShelfItem(
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnails.best(),
            videoId = null,
            browseId = browseId,
        )
    }

    fun artistFromSubtitle(subtitle: String): String {
        val parts = subtitle.split(" • ").map(String::trim).filter { it.isNotBlank() }
        return parts.firstOrNull {
            it.lowercase(Locale.ROOT) !in TYPE_WORDS && !it.matches(TALLY) && !it.matches(DURATION)
        } ?: subtitle
    }

    private fun collectRenderers(root: JsonElement, name: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(node: JsonElement) {
            when (node) {
                is JsonObject -> {
                    (node[name] as? JsonObject)?.let(out::add)
                    node.values.forEach(::walk)
                }
                is JsonArray -> node.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private val DURATION = Regex("""\d+:\d{2}""")
    private val TALLY = Regex(
        """[\d.,]+\s*[KMB]?\s+(plays|views|likes|songs|tracks|subscribers|""" +
            """hours?|minutes?|seconds?)\b.*""",
        RegexOption.IGNORE_CASE,
    )
    private val TYPE_WORDS = setOf(
        "song", "video", "album", "single", "ep", "artist",
        "playlist", "podcast", "episode",
    )
    private val VIDEO_WORD = Regex("""\bvideos?\b""", RegexOption.IGNORE_CASE)

    // JSON Helper extensions
    private fun JsonElement?.o(key: String): JsonObject? =
        (this as? JsonObject)?.get(key) as? JsonObject

    private fun JsonElement?.a(key: String): JsonArray? =
        (this as? JsonObject)?.get(key) as? JsonArray

    private fun JsonElement?.s(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.runs(): String =
        this.a("runs")?.joinToString("") { it.s("text").orEmpty() }.orEmpty()

    private fun JsonArray?.best(): String? = this?.lastOrNull().s("url")

    private fun JsonArray?.isNotSquare(): Boolean {
        val last = this?.lastOrNull()
        val width = last.s("width")?.toDoubleOrNull() ?: return false
        val height = last.s("height")?.toDoubleOrNull() ?: return false
        if (width <= 0 || height <= 0) return false
        return width / height !in 0.85..1.15
    }
}
