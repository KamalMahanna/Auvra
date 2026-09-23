package com.mymusic.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class AppRelease(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String,
    val apkSize: Long
) {
    val formattedApkSize: String
        get() {
            if (apkSize <= 0) return ""
            val mb = apkSize.toDouble() / (1024 * 1024)
            return "%.1f MB".format(mb)
        }

    fun cleanReleaseNotes(): String {
        if (releaseNotes.isBlank()) {
            return "• Performance improvements and bug fixes."
        }

        val lines = releaseNotes.lines()
        val cleaned = mutableListOf<String>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isBlank()) continue

            // Skip automated CI boilerplate or release metadata
            if (line.startsWith("Automated release generated", ignoreCase = true)) continue
            if (line.contains("Version Name:", ignoreCase = true)) continue
            if (line.contains("Version Code:", ignoreCase = true)) continue
            if (line.startsWith("Full Changelog:", ignoreCase = true) || line.contains("github.com", ignoreCase = true)) continue

            // Skip generic header lines
            if (line.startsWith("#")) {
                val heading = line.replace(Regex("^#+\\s*"), "").trim()
                if (heading.equals("What's Changed", ignoreCase = true) ||
                    heading.equals("What's New", ignoreCase = true) ||
                    heading.equals("Changelog", ignoreCase = true)
                ) {
                    continue
                }
            }

            // Strip markdown asterisks, underscores, and backticks
            var item = line
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim()

            // Normalize bullet points to unicode bullet
            if (item.startsWith("- ") || item.startsWith("* ")) {
                item = "• " + item.substring(2).trim()
            } else if (!item.startsWith("• ") && !item.startsWith("•")) {
                item = "• $item"
            }

            cleaned.add(item)
        }

        return if (cleaned.isEmpty()) {
            "• Performance improvements and bug fixes."
        } else {
            cleaned.joinToString("\n")
        }
    }
}

