package com.onepage.companion.core.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Dotted-numeric version handling for release tags.
 *
 * GitHub tags are hand-written (`v1.0.2`, occasionally `release-1.0.3`), so the
 * comparison is deliberately forgiving about decoration but strict about
 * numbers: anything that does not yield at least one leading digit segment is
 * treated as "unknown" and never reported as an update.
 */
object AppVersion {

    /** Numeric segments of [raw]; empty when the string carries no version. */
    fun segments(raw: String): List<Int> {
        val start = raw.indexOfFirst { it.isDigit() }
        if (start < 0) return emptyList()
        val body = raw.substring(start).substringBefore('-').substringBefore('+').trim()
        val parts = body.split('.').map { part -> part.takeWhile { it.isDigit() } }
        if (parts.any { it.isEmpty() }) return emptyList()
        return parts.map { it.toIntOrNull() ?: 0 }
    }

    /** `v1.0.2` / `release-1.0.3` / `1.0.4-beta` → `1.0.2` / `1.0.3` / `1.0.4`. */
    fun label(raw: String): String {
        val segments = segments(raw)
        if (segments.isEmpty()) return raw.trim()
        return segments.joinToString(".")
    }

    /** Negative when [a] is older than [b], 0 when equal, positive when newer. */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        if (left.isEmpty() || right.isEmpty()) return 0
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0
}

/** Turns a "latest release" payload into [AppRelease]. Throws [UpdateException]. */
object ReleaseParser {

    /**
     * @param fallbackPageUrl release page of the mirror that answered, used when
     *   the payload has no `html_url` (Gitee omits it on some payloads).
     */
    fun parse(json: String, fallbackPageUrl: String = ""): AppRelease {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw UpdateException("无法解析更新源返回的数据", e)
        }
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) throw UpdateException("更新源未返回版本号（tag_name 为空）")

        return AppRelease(
            tag = tag,
            title = root.optString("name").trim().ifBlank { tag },
            notes = root.optString("body").trim(),
            pageUrl = root.optString("html_url").trim().ifBlank { fallbackPageUrl },
            publishedAt = root.optString("published_at").trim().ifBlank { root.optString("created_at").trim() },
            assets = parseAssets(root.optJSONArray("assets")),
        )
    }

    private fun parseAssets(array: JSONArray?): List<ReleaseAsset> {
        if (array == null) return emptyList()
        val assets = ArrayList<ReleaseAsset>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("browser_download_url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            assets += ReleaseAsset(
                name = name,
                url = url,
                size = item.optLong("size"),
                digest = item.optString("digest").trim(),
            )
        }
        return assets
    }
}

private val releaseDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val releaseDateFallback = Regex("""\d{4}-\d{2}-\d{2}""")

/** `2026-10-23T01:52:09Z` → `2026-10-23` in the phone's time zone. */
fun formatReleaseDate(iso: String?): String {
    val raw = iso?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return runCatching {
        Instant.parse(raw).atZone(ZoneId.systemDefault()).format(releaseDate)
    }.getOrElse {
        releaseDateFallback.find(raw)?.value ?: ""
    }
}
