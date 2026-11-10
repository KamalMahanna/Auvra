package com.auvra.app.player

import com.auvra.app.data.model.Song
import java.util.Locale

/**
 * Builds and extends the playback queue / radio station with intelligent
 * deduplication and per-artist diversity caps, ported from BitChord.
 *
 * Solves two common issues:
 *  1. YouTube's watch queues routinely carry the same recording twice — the
 *     official audio and music video have different videoIds with near identical
 *     titles. Matching is performed on normalized title plus artist set overlap.
 *  2. A radio mix leans hard on the seed's own artist. Capping how many tracks
 *     any one artist contributes prevents a station from turning into a single
 *     artist's discography.
 */
object QueueBuilder {

    /** No more than this many tracks by one artist in a single batch. */
    const val PER_ARTIST_LIMIT = 2

    /** The artist the station was seeded on gets more room, but not the run of it. */
    const val SEED_ARTIST_LIMIT = 4

    /**
     * Appends up to [limit] candidate tracks to [existing], filtering out any that
     * are already queued or represent the same recording, and capping per-artist contribution.
     */
    fun extend(
        existing: List<Song>,
        candidates: List<Song>,
        limit: Int,
    ): List<Song> {
        val taken = existing.toMutableList()
        val perArtist = mutableMapOf<String, Int>()
        val seedArtists = existing.lastOrNull()?.let { artistSet(it) }.orEmpty()
        val out = mutableListOf<Song>()

        for (candidate in candidates) {
            if (out.size >= limit) break
            if (taken.any { isSameRecording(it, candidate) }) continue

            val artists = artistSet(candidate)
            val key = artists.minOrNull()
            if (key != null) {
                val cap = if (artists.any { it in seedArtists }) {
                    SEED_ARTIST_LIMIT
                } else {
                    PER_ARTIST_LIMIT
                }
                val played = perArtist[key] ?: 0
                if (played >= cap) continue
                perArtist[key] = played + 1
            }

            taken += candidate
            out += candidate
        }
        return out
    }

    /**
     * Whether two entries represent the same recording. VideoIds differ between
     * the audio and video cuts of a track on YouTube, which is handled here via
     * normalized title and artist set overlap.
     */
    fun isSameRecording(a: Song, b: Song): Boolean {
        if (a.id == b.id) return true
        val titleA = normalisedTitle(a.name)
        val titleB = normalisedTitle(b.name)
        if (titleA.isEmpty() || titleA != titleB) return false

        val left = artistSet(a)
        val right = artistSet(b)
        return left.isEmpty() || right.isEmpty() || left.any { it in right }
    }

    /**
     * `Kesariya (From "Brahmastra") | Official Video` and `Kesariya` are one
     * recording as far as a queue is concerned. Remix and cover markers are
     * deliberately left in — those really are different tracks.
     */
    fun normalisedTitle(raw: String): String = raw.lowercase(Locale.ROOT)
        .substringBefore(" | ")
        .replace(NOISE, " ")
        .replace(PUNCTUATION, " ")
        .replace(SPACES, " ")
        .trim()

    /** Extract cleaned artist set from a song. */
    fun artistSet(song: Song): Set<String> {
        val primary = song.artists.primary.map { it.name }.filter { it.isNotBlank() }
        val all = song.artists.all.map { it.name }.filter { it.isNotBlank() }
        val names = if (primary.isNotEmpty()) primary else all
        val combined = if (names.isNotEmpty()) names.joinToString(", ") else song.primaryArtistNames
        return artistSet(combined)
    }

    /** The cast behind a credit, split out so billing order stops mattering. */
    fun artistSet(raw: String): Set<String> = raw.lowercase(Locale.ROOT)
        .replace(TOPIC, " ")
        .split(",", "&", "·", "•", ";", " feat", " ft.", " ft ", " x ", " with ")
        .map { it.replace(PUNCTUATION, " ").replace(SPACES, " ").trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private val NOISE = Regex(
        """\((?:official|lyric|lyrics|lyrical|audio|video|visuali[sz]er|full song|hd|4k)[^)]*\)""" +
            """|\(from[^)]*\)""" +
            """|\[[^]]*]""" +
            """|\b(?:official (?:video|audio|music video)|lyrical video|full video|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC = Regex("""\s*-\s*topic\b""", RegexOption.IGNORE_CASE)
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")
}
