package com.auvra.app.utils

import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongArtists
import kotlin.math.min

/**
 * Simple deduplicator for tracks based on:
 * - Normalized song name (lowercased, brackets removed, punctuation cleared)
 * - Sorted normalized artist names
 *
 * Selects the best representative candidate from duplicate groups based on play count and metadata quality.
 */
object SongDeduplicator {

    private val BRACKET_REGEX = Regex("""\([^)]*\)|\[[^]]*\]|\{[^}]*\}""")
    private val ASCII_PUNCT_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val VERSION_TAGS_REGEX = Regex(
        """(?i)\b(remix|re-mix|live|concert|acoustic|unplugged|karaoke|instrumental|lofi|lo-fi|cover)\b"""
    )

    /**
     * Deduplicates a list of [Song] items, returning the list of best representative tracks.
     * Preserves original relative order of appearance.
     */
    fun deduplicate(songs: List<Song>?): List<Song> {
        if (songs.isNullOrEmpty()) return emptyList()
        if (songs.size == 1) return songs

        return songs
            .groupBy { song ->
                val titleKey = normalize(song.name)
                val artistKey = artistKey(song.artists)
                if (titleKey.isEmpty()) song.id else "$titleKey#$artistKey"
            }
            .values
            .map { group -> selectBestCandidate(group) }
    }

    /**
     * Checks if two songs represent the same track based on normalized title and sorted artist names.
     */
    fun isMatch(songA: Song, songB: Song): Boolean {
        if (songA.id == songB.id) return true
        val normTitleA = normalize(songA.name)
        val normTitleB = normalize(songB.name)
        if (normTitleA.isEmpty() || normTitleB.isEmpty()) return false
        val artistKeyA = artistKey(songA.artists)
        val artistKeyB = artistKey(songB.artists)
        return normTitleA == normTitleB && artistKeyA == artistKeyB
    }

    /**
     * Selects the single best representative song from a group of duplicate candidates.
     */
    private fun selectBestCandidate(group: List<Song>): Song {
        if (group.size == 1) return group.first()

        return group.maxByOrNull { song ->
            var score = 0.0

            // 1. Version preference: favor studio/original over remixes or live recordings
            if (!VERSION_TAGS_REGEX.containsMatchIn(song.name)) {
                score += 50.0
            }

            // 2. Play count bonus (higher popularity = better metadata on JioSaavn)
            val playCount = song.playCount ?: 0
            score += min(playCount / 10000.0, 30.0)

            // 3. Metadata quality bonus
            if (!song.highQualityImageUrl.isNullOrEmpty()) score += 5.0
            if (!song.highQualityDownloadUrl.isNullOrEmpty()) score += 5.0
            if (song.hasLyrics) score += 3.0
            if (song.duration != null && song.duration > 0) score += 2.0

            score
        } ?: group.first()
    }

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lowercase()
            .replace("&", " and ")
            .replace(BRACKET_REGEX, " ")
            .replace(ASCII_PUNCT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private fun extractArtistNames(artists: SongArtists): List<String> {
        val primary = artists.primary.map { it.name.trim() }.filter { it.isNotEmpty() }
        val all = artists.all.map { it.name.trim() }.filter { it.isNotEmpty() }
        val list = if (primary.isNotEmpty()) primary else all
        return list.flatMap { name ->
            name.split(",", "&")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }

    fun artistKey(artists: SongArtists): String {
        return extractArtistNames(artists)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString("|")
    }
}
