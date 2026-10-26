package com.onepage.companion.core.update

/** One file attached to a GitHub release. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long = 0,
) {
    val isApk: Boolean get() = name.endsWith(".apk", ignoreCase = true)
}

/**
 * `GET /repos/{owner}/{repo}/releases/latest`, flattened to the fields the
 * updater actually shows. Unknown / missing metadata degrades to empty strings
 * instead of throwing, because a release with a tag but no notes is still a
 * usable update.
 */
data class AppRelease(
    val tag: String,
    val title: String = "",
    val notes: String = "",
    val pageUrl: String = "",
    val publishedAt: String = "",
    val assets: List<ReleaseAsset> = emptyList(),
) {
    /** `v1.0.2` → `1.0.2`; falls back to the tag when nothing parses. */
    val version: String get() = AppVersion.label(tag)

    /** The APK asset when the release ships one; null for source-only releases. */
    val apk: ReleaseAsset? get() = assets.firstOrNull { it.isApk }

    val publishedDate: String get() = formatReleaseDate(publishedAt)
}

/** Updater-specific failure with a message that is safe to show in the UI. */
class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Long release notes trimmed for cards and the launch dialog. */
fun notesPreview(notes: String, limit: Int = 1_200): String =
    if (notes.length <= limit) notes else notes.take(limit).trimEnd() + "\n…"
