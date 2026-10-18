package com.auvra.app.data.model

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.auvra.app.utils.resize
import com.squareup.moshi.JsonClass

@Immutable
@JsonClass(generateAdapter = true)
data class DownloadLink(
    val quality: String,
    val url: String
)

@Immutable
@JsonClass(generateAdapter = true)
data class SongAlbum(
    val id: String?,
    val name: String?,
    val url: String?
)

@Immutable
@JsonClass(generateAdapter = true)
data class ArtistMap(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String = ""
)

@Immutable
@JsonClass(generateAdapter = true)
data class SongArtists(
    val primary: List<ArtistMap> = emptyList(),
    val featured: List<ArtistMap> = emptyList(),
    val all: List<ArtistMap> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class Song(
    val id: String,
    val name: String,
    val type: String = "",
    val year: String? = null,
    val releaseDate: String? = null,
    val duration: Int? = null,
    val label: String? = null,
    val explicitContent: Boolean = false,
    val playCount: Int? = null,
    val language: String = "",
    val hasLyrics: Boolean = false,
    val lyricsId: String? = null,
    val url: String = "",
    val copyright: String? = null,
    val album: SongAlbum = SongAlbum(null, null, null),
    val artists: SongArtists = SongArtists(),
    val image: List<DownloadLink> = emptyList(),
    val downloadUrl: List<DownloadLink> = emptyList()
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)
    
    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl
    
    val highQualityDownloadUrl: String?
        get() = downloadUrl.find { it.quality == "320kbps" }?.url
            ?: downloadUrl.lastOrNull()?.url
    
    val primaryArtistNames: String
        get() = artists.primary.map { it.name }.joinToString(", ").ifEmpty {
            artists.all.map { it.name }.joinToString(", ")
        }
    
    val durationFormatted: String
        get() {
            val d = duration ?: return ""
            val min = d / 60
            val sec = d % 60
            return "%d:%02d".format(min, sec)
        }
}

@Immutable
@JsonClass(generateAdapter = true)
data class SearchSongResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<Song> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SearchArtistResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<SearchArtist> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SearchAlbumResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<Album> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SearchPlaylistResult(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<Playlist> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class SearchArtist(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String = ""
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl
}

@Immutable
@JsonClass(generateAdapter = true)
data class ArtistDetail(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val followerCount: Int? = null,
    val fanCount: Int? = null,
    val isVerified: Boolean? = false,
    val dominantLanguage: String? = null,
    val dominantType: String? = null,
    val topSongs: List<Song>? = null,
    val topAlbums: List<Album>? = null,
    val singles: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl
}

@Immutable
@JsonClass(generateAdapter = true)
data class Album(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val year: Int? = null,
    val type: String = "",
    val playCount: Int? = null,
    val language: String = "",
    val explicitContent: Boolean = false,
    val songCount: Int? = null,
    val url: String = "",
    val artists: SongArtists? = null,
    val image: List<DownloadLink> = emptyList(),
    val songs: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl
}

@Immutable
@JsonClass(generateAdapter = true)
data class Playlist(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val year: Int? = null,
    val type: String = "",
    val playCount: Int? = null,
    val language: String = "",
    val explicitContent: Boolean = false,
    val songCount: Int? = null,
    val url: String = "",
    val image: List<DownloadLink> = emptyList(),
    val songs: List<Song>? = null
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl
}

@Immutable
@JsonClass(generateAdapter = true)
data class HomeChip(
    val title: String = "",
    val params: String? = null,
    val browseId: String? = null
)

@Immutable
data class HomeFeed(
    val chips: List<HomeChip> = emptyList(),
    val sections: List<ModuleSection> = emptyList(),
    val continuation: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class DailyDiscoverItem(
    val seedTitle: String = "",
    val seedArtist: String = "",
    val song: Song
)

@Immutable
@JsonClass(generateAdapter = true)
data class CommunityPlaylistItem(
    val playlist: Playlist,
    val previewSongs: List<Song> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class MoodAndGenreItem(
    val title: String = "",
    val stripeColor: Long? = null,
    val browseId: String = "",
    val params: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class ModuleItem(
    val id: String = "",
    val name: String = "",
    val subtitle: String? = null,
    val type: String = "",
    val image: List<DownloadLink> = emptyList(),
    val url: String? = null,
    val explicitContent: Boolean = false,
    val duration: Int? = null,
    val endpointBrowseId: String? = null,
    val endpointParams: String? = null
) {
    val highQualityImageUrl: String?
        get() = image.find { it.quality == "1080x1080" }?.url
            ?: image.lastOrNull()?.url?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = image.find { it.quality == "544x544" }?.url
            ?: image.find { it.quality == "150x150" }?.url?.resize(544, 544)
            ?: image.getOrNull(1)?.url?.resize(544, 544)
            ?: image.firstOrNull()?.url?.resize(544, 544)
            ?: highQualityImageUrl

    val lowQualityImageUrl: String?
        get() = image.find { it.quality == "200x200" }?.url
            ?: image.find { it.quality == "50x50" }?.url?.resize(200, 200)
            ?: image.firstOrNull()?.url?.resize(200, 200)
            ?: mediumQualityImageUrl

    val durationFormatted: String
        get() {
            val d = duration ?: return ""
            val min = d / 60
            val sec = d % 60
            return "%d:%02d".format(min, sec)
        }
}

@Immutable
@JsonClass(generateAdapter = true)
data class ModuleSection(
    val title: String = "",
    val subtitle: String? = null,
    val thumbnail: String? = null,
    val source: String? = null,
    val position: Int = 0,
    val data: List<ModuleItem> = emptyList(),
    val endpointBrowseId: String? = null,
    val endpointParams: String? = null
) {
    val isSongsOnly: Boolean
        get() = data.isNotEmpty() && data.all { it.type.lowercase().trim() == "song" }
}

@Entity(tableName = "downloaded_songs")
@Immutable
data class DownloadedSong(
    @PrimaryKey val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val duration: Int?,
    val filePath: String,
    val imageUrl: String?,
    val fileSize: Long = 0
) {
    val highQualityImageUrl: String?
        get() = imageUrl?.resize(1080, 1080)

    val mediumQualityImageUrl: String?
        get() = imageUrl?.resize(544, 544)

    val lowQualityImageUrl: String?
        get() = imageUrl?.resize(200, 200)

    val durationFormatted: String
        get() {
            val d = duration ?: return ""
            val min = d / 60
            val sec = d % 60
            return "%d:%02d".format(min, sec)
        }
}

fun String.unescapeHtml(): String {
    return try {
        android.text.Html.fromHtml(this, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    } catch (e: Exception) {
        this
    }
}

fun Song.clean(): Song {
    return this.copy(
        name = name.unescapeHtml(),
        album = album.copy(name = album.name?.unescapeHtml()),
        artists = artists.copy(
            primary = artists.primary.map { it.copy(name = it.name.unescapeHtml()) },
            featured = artists.featured.map { it.copy(name = it.name.unescapeHtml()) },
            all = artists.all.map { it.copy(name = it.name.unescapeHtml()) }
        )
    )
}

fun Album.clean(): Album {
    return this.copy(
        name = name.unescapeHtml(),
        description = description?.unescapeHtml(),
        songs = songs?.map { it.clean() }
    )
}

fun Playlist.clean(): Playlist {
    return this.copy(
        name = name.unescapeHtml(),
        description = description?.unescapeHtml(),
        songs = songs?.map { it.clean() }
    )
}

fun ArtistDetail.clean(): ArtistDetail {
    return this.copy(
        name = name.unescapeHtml(),
        topSongs = topSongs?.map { it.clean() },
        topAlbums = topAlbums?.map { it.clean() },
        singles = singles?.map { it.clean() }
    )
}

fun SearchSongResult.clean(): SearchSongResult {
    return this.copy(
        results = results.map { it.clean() }
    )
}

fun SearchArtist.clean(): SearchArtist {
    return this.copy(
        name = name.unescapeHtml()
    )
}

fun SearchArtistResult.clean(): SearchArtistResult {
    return this.copy(
        results = results.map { it.clean() }
    )
}

fun SearchAlbumResult.clean(): SearchAlbumResult {
    return this.copy(
        results = results.map { it.clean() }
    )
}

fun SearchPlaylistResult.clean(): SearchPlaylistResult {
    return this.copy(
        results = results.map { it.clean() }
    )
}

fun ModuleItem.clean(): ModuleItem {
    return this.copy(
        name = name.unescapeHtml(),
        subtitle = subtitle?.unescapeHtml()
    )
}

fun ModuleSection.clean(): ModuleSection {
    return this.copy(
        title = title.unescapeHtml(),
        subtitle = subtitle?.unescapeHtml(),
        data = data.map { it.clean() }
    )
}

fun DownloadedSong.toSong(): Song {
    val normalizedImage = imageUrl?.let {
        if (it.startsWith("/") && !it.startsWith("file://")) "file://$it" else it
    }
    val links = normalizedImage?.let { url ->
        listOf(
            DownloadLink("200x200", url.resize(200, 200)),
            DownloadLink("544x544", url.resize(544, 544)),
            DownloadLink("1080x1080", url.resize(1080, 1080))
        )
    } ?: emptyList()
    return Song(
        id = id,
        name = name,
        duration = duration,
        artists = SongArtists(primary = listOf(ArtistMap(name = artist))),
        image = links,
        url = filePath
    )
}

fun DownloadedSong.toModuleItem(): ModuleItem {
    val normalizedImage = imageUrl?.let {
        if (it.startsWith("/") && !it.startsWith("file://")) "file://$it" else it
    }
    val links = normalizedImage?.let { url ->
        listOf(
            DownloadLink("200x200", url.resize(200, 200)),
            DownloadLink("544x544", url.resize(544, 544)),
            DownloadLink("1080x1080", url.resize(1080, 1080))
        )
    } ?: emptyList()
    return ModuleItem(
        id = id,
        name = name,
        subtitle = artist,
        type = "song",
        image = links,
        url = filePath
    )
}

fun Song.toModuleItem(): ModuleItem = ModuleItem(
    id = id,
    name = name,
    subtitle = primaryArtistNames,
    type = "song",
    image = image,
    url = url
)

fun ModuleItem.toSong(): Song = Song(
    id = id,
    name = name,
    duration = duration,
    artists = SongArtists(primary = listOf(ArtistMap(name = subtitle.orEmpty()))),
    image = image,
    url = url.orEmpty()
)

fun Album.toModuleItem(): ModuleItem = ModuleItem(
    id = id,
    name = name,
    subtitle = artists?.primary?.joinToString(", ") { it.name } ?: type,
    type = "album",
    image = image
)


