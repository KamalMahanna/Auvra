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
}

