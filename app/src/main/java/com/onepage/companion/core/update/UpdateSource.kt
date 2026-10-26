package com.onepage.companion.core.update

/**
 * Where the app looks for its own APK.
 *
 * [Gitee] is tried first because the GitHub release endpoints are unreliable
 * from mainland China; the GitHub repository stays the source of truth and the
 * Gitee repository (`Yann_Fang/onepage-app`) is a mirror of the same project
 * that is updated by hand.
 */
enum class UpdateSource(
    val label: String,
    val owner: String,
    val repo: String,
    val latestUrl: String,
    val releasesPageUrl: String,
    val repoUrl: String,
) {
    Gitee(
        label = "Gitee",
        owner = "Yann_Fang",
        repo = "onepage-app",
        latestUrl = "https://gitee.com/api/v5/repos/Yann_Fang/onepage-app/releases/latest",
        releasesPageUrl = "https://gitee.com/Yann_Fang/onepage-app/releases",
        repoUrl = "https://gitee.com/Yann_Fang/onepage-app",
    ),
    GitHub(
        label = "GitHub",
        owner = "YannFang2002",
        repo = "onepage-app",
        latestUrl = "https://api.github.com/repos/YannFang2002/onepage-app/releases/latest",
        releasesPageUrl = "https://github.com/YannFang2002/onepage-app/releases",
        repoUrl = "https://github.com/YannFang2002/onepage-app",
    ),
    ;

    /** Label for the UI: the mirror relation matters to the reader. */
    val displayName: String
        get() = when (this) {
            Gitee -> "Gitee 镜像"
            GitHub -> "GitHub Releases"
        }

    /** Release page for one tag; Gitee payloads omit `html_url`, so we build it. */
    fun releasePageUrl(tag: String): String = "$repoUrl/releases/tag/$tag"

    companion object {
        /** Order matters: first reachable mirror wins. */
        val byPreference: List<UpdateSource> = listOf(Gitee, GitHub)
    }
}

/** The newest published release plus the mirror that answered. */
data class UpdateResult(
    val release: AppRelease,
    val source: UpdateSource,
)
