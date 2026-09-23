package com.auvra.app.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.auvra.app.data.model.AppRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var hasCheckedThisSession = false

    /**
     * Retrieves the currently installed version name from PackageManager.
     */
    fun getInstalledVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed version name", e)
            "1.0.0"
        }
    }


    /**
     * Checks GitHub for the latest release on app opening.
     * Skips checking if offline or if already checked during this app session (unless force=true).
     */
    suspend fun checkForUpdate(force: Boolean = false): AppRelease? = withContext(Dispatchers.IO) {
        if (!force && hasCheckedThisSession) {
            Log.d(TAG, "Already checked for update this session, skipping.")
            return@withContext null
        }

        if (!networkMonitor.isOnlineNow()) {
            Log.d(TAG, "Device is offline, skipping update check.")
            return@withContext null
        }

        hasCheckedThisSession = true

        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Auvra-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub release check failed: HTTP ${response.code}")
                return@withContext null
            }

            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)

            val tagName = json.optString("tag_name", "")
            val name = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", "https://github.com/KamalMahanna/Auvra/releases")

            var downloadUrl = htmlUrl
            var apkSize = 0L

            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val asset = assetsArray.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", downloadUrl)
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val currentVersion = getInstalledVersionName()
            val versionForComparison = if (tagName.isNotBlank()) tagName else name

            Log.d(TAG, "Current version: '$currentVersion', Latest GitHub release: '$versionForComparison'")

            if (isUpdateAvailable(currentVersion, versionForComparison)) {
                val dismissedTag = prefs.getString(KEY_DISMISSED_TAG, null)
                if (!force && dismissedTag == tagName) {
                    Log.d(TAG, "Update '$tagName' was previously dismissed by user.")
                    return@withContext null
                }

                return@withContext AppRelease(
                    tagName = tagName,
                    versionName = name.ifBlank { tagName },
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    publishedAt = publishedAt,
                    apkSize = apkSize
                )
            } else {
                Log.d(TAG, "App is up to date.")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for update: ${e.message}", e)
            null
        }
    }

    /**
     * Records that the user dismissed this release tag so they are not prompted again for it.
     */
    fun dismissRelease(tagName: String) {
        prefs.edit().putString(KEY_DISMISSED_TAG, tagName).apply()
    }

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val PREFS_NAME = "auvra_update_prefs"
        private const val KEY_DISMISSED_TAG = "dismissed_release_tag"
        private const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/KamalMahanna/Auvra/releases/latest"

        /**
         * Compares two version strings numerically (e.g. "26.07.10.21" vs "1.0.0" or "v1.2.3").
         * Returns: 1 if v1 > v2, -1 if v1 < v2, 0 if equal
         */
        fun compareVersions(v1: String, v2: String): Int {
            val v1Parts = v1.removePrefix("v").substringBefore("-").trim().split(".").map { it.toIntOrNull() ?: 0 }
            val v2Parts = v2.removePrefix("v").substringBefore("-").trim().split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(v1Parts.size, v2Parts.size)

            for (i in 0 until maxLength) {
                val part1 = v1Parts.getOrNull(i) ?: 0
                val part2 = v2Parts.getOrNull(i) ?: 0
                if (part1 > part2) return 1
                if (part1 < part2) return -1
            }
            return 0
        }

        /**
         * Checks if latestVersion is newer than currentVersion.
         */
        fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
            return compareVersions(latestVersion, currentVersion) > 0
        }
    }
}

