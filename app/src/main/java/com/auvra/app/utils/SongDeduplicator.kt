package com.auvra.app.utils

import com.auvra.app.data.model.Song
import com.auvra.app.data.model.SongArtists
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Domain-aware smart deduplicator for JioSaavn tracks, adapting Bloomee's multi-factor
 * fuzzy matching algorithm.
 *
 * Replaces naive string `distinctBy { name to artist }` with:
 * 1. Text normalization & title simplification (removing noise like feat, live, remix).
 * 2. Blended text similarity (Levenshtein, token sort, partial, and token overlap).
 * 3. Bidirectional artist similarity matching.
 * 4. Duration closeness scoring.
 * 5. Version penalty subtraction (protecting original studio tracks from remix/live variants).
 * 6. Best-variant candidate selection based on play count, metadata completeness, and cleanliness.
 */
object SongDeduplicator {

    private val BRACKET_REGEX = Regex("""\([^)]*\)|\[[^]]*\]|\{[^}]*\}""")
    private val ASCII_PUNCT_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val FEAT_REGEX = Regex("""(?i)\b(ft|feat|featuring)\b.*$""")
    private val VERSION_WORDS_REGEX = Regex(
        """(?i)\b(remix|re-mix|live|concert|acoustic|unplugged|karaoke|instrumental|lofi|lo-fi|cover|radio edit|deluxe|remaster|remastered|extended)\b"""
    )
    private val VERSION_TAG_PATTERNS = mapOf(
        "remix" to Regex("""(?i)\b(remix|re-mix)\b"""),
        "live" to Regex("""(?i)\b(live|concert)\b"""),
        "acoustic" to Regex("""(?i)\b(acoustic|unplugged)\b"""),
        "karaoke" to Regex("""(?i)\b(karaoke|instrumental)\b"""),
        "lofi" to Regex("""(?i)\b(lofi|lo-fi)\b"""),
        "cover" to Regex("""(?i)\b(cover)\b"""),
        "radio" to Regex("""(?i)\b(radio edit)\b""")
    )

    /**
     * Deduplicates a list of [Song] items, returning the list of best representative tracks.
     * Preserves original relative order of appearance.
     */
    fun deduplicate(songs: List<Song>?): List<Song> {
        if (songs.isNullOrEmpty()) return emptyList()
        if (songs.size == 1) return songs

        val groups = mutableListOf<MutableList<Song>>()

        for (song in songs) {
            var matchedGroup: MutableList<Song>? = null

            for (group in groups) {
                val representative = group.first()
                if (isMatch(representative, song)) {
                    matchedGroup = group
                    break
                }
            }

            if (matchedGroup != null) {
                matchedGroup.add(song)
            } else {
                groups.add(mutableListOf(song))
            }
        }

        return groups.map { selectBestCandidate(it) }
    }

    /**
     * Checks if two songs represent the same underlying track.
     */
    fun isMatch(songA: Song, songB: Song, threshold: Double = 0.82): Boolean {
        if (songA.id == songB.id) return true

        val normTitleA = normalize(songA.name)
        val normTitleB = normalize(songB.name)
        val artistKeyA = artistKey(songA.artists)
        val artistKeyB = artistKey(songB.artists)

        // Fast-path exact title + artist match
        if (normTitleA.isNotEmpty() && normTitleA == normTitleB &&
            artistKeyA.isNotEmpty() && artistKeyA == artistKeyB
        ) {
            return true
        }

        val confidence = scoreMatch(songA, songB)
        return confidence >= threshold
    }

    /**
     * Scores confidence between 0.0 and 1.0 that [songA] and [songB] are the same track.
     */
    fun scoreMatch(songA: Song, songB: Song): Double {
        val normTitleA = normalize(songA.name)
        val normTitleB = normalize(songB.name)
        val artistKeyA = artistKey(songA.artists)
        val artistKeyB = artistKey(songB.artists)

        // Fast path check
        if (normTitleA.isNotEmpty() && normTitleA == normTitleB &&
            artistKeyA.isNotEmpty() && artistKeyA == artistKeyB
        ) {
            val dur = durationSimilarity(songA.duration, songB.duration)
            return (0.92 + dur * 0.08).coerceIn(0.0, 1.0)
        }

        val titleScore = blendedTextSimilarity(songA.name, songB.name)
        val simplifiedTitleScore = blendedTextSimilarity(
            simplifyTitle(songA.name),
            simplifyTitle(songB.name)
        )

        val artistScore = artistNamesSimilarity(songA.artists, songB.artists)
        val durScore = durationSimilarity(songA.duration, songB.duration)
        val vPenalty = versionPenalty(songA.name, songB.name)

        val wTitle = 0.38
        val wSimplified = 0.16
        val wArtist = 0.32
        val wDuration = 0.14

        var score = (titleScore * wTitle) +
                (simplifiedTitleScore * wSimplified) +
                (artistScore * wArtist) +
                (durScore * wDuration)

        // Exact match bonuses
        if (normTitleA.isNotEmpty() && normTitleA == normTitleB) {
            score += 0.06
        }
        val simpleA = simplifyTitle(songA.name)
        val simpleB = simplifyTitle(songB.name)
        if (simpleA.isNotEmpty() && simpleB.isNotEmpty() && simpleA == simpleB) {
            score += 0.04
        }
        if (artistKeyA.isNotEmpty() && artistKeyA == artistKeyB) {
            score += 0.05
        }

        score -= vPenalty
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Selects the single best representative song from a group of duplicate candidates.
     */
    private fun selectBestCandidate(group: List<Song>): Song {
        if (group.size == 1) return group.first()

        return group.maxByOrNull { song ->
            var score = 0.0

            // 1. Version score: favor studio/original over remixes or live recordings
            val tags = versionTags(song.name)
            if (tags.isEmpty()) {
                score += 50.0 // Big bonus for clean original track
            } else {
                score -= tags.size * 10.0
            }

            // 2. Play count bonus (higher popularity = better metadata on JioSaavn)
            val playCount = song.playCount ?: 0
            score += min(playCount / 10000.0, 30.0)

            // 3. Metadata quality bonus
            if (!song.highQualityImageUrl.isNullOrEmpty()) score += 5.0
            if (!song.highQualityDownloadUrl.isNullOrEmpty()) score += 5.0
            if (song.hasLyrics) score += 3.0
            if (song.duration != null && song.duration > 0) score += 2.0

            // 4. Title cleanliness
            val simple = simplifyTitle(song.name)
            if (simple == normalize(song.name)) score += 5.0

            score
        } ?: group.first()
    }

    // ── Text Processing & Similarity Algorithms ──────────────────────────────

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.lowercase()
            .replace("&", " and ")
            .replace(BRACKET_REGEX, " ")
            .replace(ASCII_PUNCT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    fun simplifyTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        return title
            .replace(BRACKET_REGEX, " ")
            .lowercase()
            .replace(FEAT_REGEX, "")
            .replace(VERSION_WORDS_REGEX, " ")
            .replace("&", " and ")
            .replace(ASCII_PUNCT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    fun blendedTextSimilarity(left: String?, right: String?): Double {
        val l = normalize(left)
        val r = normalize(right)
        if (l.isEmpty() || r.isEmpty()) return 0.0
        if (l == r) return 1.0

        val direct = levenshteinSimilarity(l, r)
        val partial = partialRatio(l, r)
        val sorted = tokenSortRatio(l, r)
        val overlap = tokenOverlap(l, r)

        return (direct * 0.35 + partial * 0.20 + sorted * 0.25 + overlap * 0.20).coerceIn(0.0, 1.0)
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(a, b)
        return (1.0 - dist.toDouble() / maxLen).coerceIn(0.0, 1.0)
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(min(dp[j] + 1, dp[j - 1] + 1), prev + cost)
                prev = temp
            }
        }
        return dp[b.length]
    }

    private fun partialRatio(a: String, b: String): Double {
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        if (shorter.isEmpty()) return 0.0

        var maxSim = 0.0
        val shortLen = shorter.length
        for (i in 0..(longer.length - shortLen)) {
            val sub = longer.substring(i, i + shortLen)
            val sim = levenshteinSimilarity(shorter, sub)
            if (sim > maxSim) maxSim = sim
            if (maxSim >= 0.99) break
        }
        return maxSim
    }

    private fun tokenSortRatio(a: String, b: String): Double {
        val tokensA = a.split(" ").filter { it.isNotEmpty() }.sorted().joinToString(" ")
        val tokensB = b.split(" ").filter { it.isNotEmpty() }.sorted().joinToString(" ")
        return levenshteinSimilarity(tokensA, tokensB)
    }

    private fun tokenOverlap(a: String, b: String): Double {
        val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        val intersection = setA.intersect(setB).size
        if (intersection == 0) return 0.0
        val p = intersection.toDouble() / setB.size
        val r = intersection.toDouble() / setA.size
        return (2 * p * r) / (p + r)
    }

    // ── Artist Similarity ───────────────────────────────────────────────────

    fun artistNamesSimilarity(artistsA: SongArtists, artistsB: SongArtists): Double {
        val namesA = extractArtistNames(artistsA)
        val namesB = extractArtistNames(artistsB)

        if (namesA.isEmpty() && namesB.isEmpty()) return 0.0
        if (namesA.isEmpty() || namesB.isEmpty()) {
            return blendedTextSimilarity(namesA.joinToString(" "), namesB.joinToString(" "))
        }

        var fwd = 0.0
        for (nA in namesA) {
            var best = 0.0
            for (nB in namesB) {
                best = max(best, blendedTextSimilarity(nA, nB))
            }
            fwd += best
        }

        var rev = 0.0
        for (nB in namesB) {
            var best = 0.0
            for (nA in namesA) {
                best = max(best, blendedTextSimilarity(nB, nA))
            }
            rev += best
        }

        val combined = blendedTextSimilarity(namesA.joinToString(" "), namesB.joinToString(" "))
        val primaryBonus = if (blendedTextSimilarity(namesA.first(), namesB.first()) > 0.85) 0.05 else 0.0

        return ((fwd / namesA.size) * 0.35 + (rev / namesB.size) * 0.30 + combined * 0.25 + primaryBonus)
            .coerceIn(0.0, 1.0)
    }

    private fun extractArtistNames(artists: SongArtists): List<String> {
        val primary = artists.primary.map { it.name.trim() }.filter { it.isNotEmpty() }
        val all = artists.all.map { it.name.trim() }.filter { it.isNotEmpty() }
        return (if (primary.isNotEmpty()) primary else all).ifEmpty {
            listOfNotNull(artists.primary.firstOrNull()?.name)
        }
    }

    private fun artistKey(artists: SongArtists): String {
        return extractArtistNames(artists)
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .sorted()
            .joinToString("|")
    }

    // ── Duration Similarity ─────────────────────────────────────────────────

    fun durationSimilarity(durASeconds: Int?, durBSeconds: Int?): Double {
        if (durASeconds == null || durBSeconds == null || durASeconds <= 0 || durBSeconds <= 0) {
            return 0.35
        }
        val diff = abs(durASeconds - durBSeconds)
        val maxDur = max(durASeconds, durBSeconds)
        val relErr = diff.toDouble() / maxDur

        val absScore = when {
            diff <= 2 -> 1.00
            diff <= 4 -> 0.85
            diff <= 8 -> 0.55
            diff <= 15 -> 0.25
            diff <= 30 -> 0.08
            else -> 0.00
        }

        val relScore = when {
            relErr <= 0.02 -> 1.00
            relErr <= 0.05 -> 0.85
            relErr <= 0.10 -> 0.55
            relErr <= 0.20 -> 0.25
            relErr <= 0.35 -> 0.08
            else -> 0.00
        }

        return (absScore * 0.50 + relScore * 0.50).coerceIn(0.0, 1.0)
    }

    // ── Version Penalty ─────────────────────────────────────────────────────

    private fun versionTags(title: String?): Set<String> {
        if (title.isNullOrBlank()) return emptySet()
        val norm = normalize(title)
        val result = mutableSetOf<String>()
        for ((tag, pattern) in VERSION_TAG_PATTERNS) {
            if (pattern.containsMatchIn(norm)) {
                result.add(tag)
            }
        }
        return result
    }

    fun versionPenalty(titleA: String?, titleB: String?): Double {
        val tagsA = versionTags(titleA)
        val tagsB = versionTags(titleB)
        if (tagsA.isEmpty() && tagsB.isEmpty()) return 0.0

        val missing = (tagsA - tagsB).size
        val extra = (tagsB - tagsA).size
        return (missing * 0.08 + extra * 0.04).coerceIn(0.0, 0.25)
    }
}
