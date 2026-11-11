package com.auvra.app.data.model

import androidx.compose.runtime.Immutable

/** How a shelf's items are laid out — the web player's ShelfLayout, by the same rules. */
enum class ShelfLayout {
    /** Square cards in one scrolling row. */
    CARDS,

    /** Track rows, four to a column — Quick picks, Listen again, Recents. */
    LIST,

    /** Widescreen cards for music videos. */
    VIDEO,
}

/** A card in a home-feed carousel: either a track (videoId) or an album/playlist (browseId). */
@Immutable
data class ShelfItem(
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val videoId: String?,
    val browseId: String?,
    /**
     * A music-video upload rather than a catalogue track: widescreen art, and a
     * picture the player can show. Drawn as a 16:9 card, as the web player does.
     */
    val isVideo: Boolean = false,
)

@Immutable
data class HomeShelf(
    val title: String,
    val items: List<ShelfItem>,
    val subtitle: String = "",
    val moreBrowseId: String? = null,
    val moreParams: String? = null,
    val layout: ShelfLayout = ShelfLayout.CARDS,
)

data class HomeFeedData(
    val shelves: List<HomeShelf>,
    val continuation: String?,
)

/** These shelves without their music videos, and without any shelf that leaves empty. */
fun List<HomeShelf>.withoutVideos(): List<HomeShelf> = mapNotNull { shelf ->
    if (shelf.layout == ShelfLayout.VIDEO) return@mapNotNull null
    val items = shelf.items.filterNot { it.isVideo }
    if (items.isEmpty()) null else shelf.copy(items = items)
}

/**
 * The layout a shelf of [items] gets, as the web player decides it: rows when
 * YouTube itself listed tracks as rows (or the shelf is Listen again, which the
 * web player shows that way), widescreen cards when most of it is videos.
 */
fun shelfLayoutFor(title: String, items: List<ShelfItem>, fromList: Boolean): ShelfLayout {
    if (items.isEmpty()) return ShelfLayout.CARDS
    val first = items.first()
    if (fromList && first.videoId != null) return ShelfLayout.LIST
    if (title.trim().equals("Listen again", ignoreCase = true)) return ShelfLayout.LIST
    if (items.count { it.isVideo } * 2 > items.size) return ShelfLayout.VIDEO
    return ShelfLayout.CARDS
}

/**
 * These shelves minus any that mostly repeat one already on the page, or one earlier in this
 * batch. YouTube serves the same new releases from FEmusic_new_releases ("Albums & singles") and
 * FEmusic_explore ("New albums & singles"), lightly reordered under a different heading, so a
 * title check alone lets both through.
 *
 * Only browsable items (albums, playlists, artists) are compared. Song shelves overlap on
 * purpose: Recents and YouTube's own "Listen again" share tracks but rank them differently.
 */
fun List<HomeShelf>.withoutRepeatsOf(existing: List<HomeShelf>): List<HomeShelf> {
    val shown = existing.toMutableList()
    return filter { shelf ->
        val keys = shelf.browseKeys()
        val repeat = keys.isNotEmpty() && shown.any { other ->
            val shared = other.browseKeys().count { it in keys }
            shared > 0 && shared >= REPEAT_SHELF_SHARE * minOf(shelf.items.size, other.items.size)
        }
        if (!repeat) shown += shelf
        !repeat
    }
}

private const val REPEAT_SHELF_SHARE = 0.6

private fun HomeShelf.browseKeys(): Set<String> = items.mapNotNullTo(HashSet()) { it.browseId }

const val ROW_ART_PX = 120
const val CARD_ART_PX = 480

private val SIZE_HINT = Regex("""w\d+-h\d+""")
private val PARAM_SIZE_HINT = Regex("""=s\d+""")

fun String?.artworkAt(px: Int): String? {
    if (this == null) return null
    val sized = this.replace(SIZE_HINT, "w$px-h$px")
    return if (isGoogleImageHost(this)) {
        sized.replace(PARAM_SIZE_HINT, "=s$px")
    } else {
        sized
    }
}

private fun isGoogleImageHost(url: String): Boolean {
    val host = url.substringAfter("://", "")
        .ifEmpty { url.substringAfter("//", "") }
        .substringBefore('/')
        .substringBefore(':')
        .substringBefore('?')
        .lowercase()
    return host == "ggpht.com" || host.endsWith(".ggpht.com") ||
        host == "googleusercontent.com" || host.endsWith(".googleusercontent.com")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
