package com.auvra.app.utils

private val GOOGLEUSERCONTENT_SIZE_PATTERN =
    Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")
private val GGPHT_SIZE_PATTERN =
    Regex("^(https://yt3\\.ggpht\\.com/[^?=]+)=(?:s\\d+|w\\d+-h\\d+)[^?]*(\\?.*)?$")

/**
 * Resizes YouTube Music / Google CDN thumbnail URLs to the requested dimensions.
 * For Google CDN (`googleusercontent.com`, `ggpht.com`), it adjusts image server parameters
 * to request high-definition (e.g. 1080x1080 or 544x544) artwork.
 * For YouTube video thumbnails (`i.ytimg.com`), it upgrades low-res thumbnails to `maxresdefault.jpg`.
 * Local files or unrecognized URLs are returned unchanged.
 */
fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this
    if (startsWith("/") || startsWith("file:")) return this

    GOOGLEUSERCONTENT_SIZE_PATTERN
        .matchEntire(this)
        ?.groupValues
        ?.let { group ->
            val baseUrl = group[1]
            val query = group[2] ?: ""
            return if (width != null && height != null) {
                "${baseUrl}=w${width.coerceAtLeast(1)}-h${height.coerceAtLeast(1)}-p-l90-rj$query"
            } else {
                "${baseUrl}=s${width ?: height}$query"
            }
        }

    GGPHT_SIZE_PATTERN
        .matchEntire(this)
        ?.groupValues
        ?.let { group ->
            val baseUrl = group[1]
            val query = group[2] ?: ""
            return if (width != null && height != null) {
                "${baseUrl}=w${width.coerceAtLeast(1)}-h${height.coerceAtLeast(1)}-p-l90-rj$query"
            } else {
                "${baseUrl}=s${width ?: height}$query"
            }
        }

    // Upgrade standard YouTube video thumbnails to max-res
    if (contains("i.ytimg.com/vi/") || contains("img.youtube.com/vi/")) {
        return this.replace("/default.jpg", "/maxresdefault.jpg")
            .replace("/mqdefault.jpg", "/maxresdefault.jpg")
            .replace("/hqdefault.jpg", "/maxresdefault.jpg")
            .replace("/sddefault.jpg", "/maxresdefault.jpg")
    }

    return this
}

