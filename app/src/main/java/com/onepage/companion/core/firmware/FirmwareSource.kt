package com.onepage.companion.core.firmware

/**
 * Where the firmware comes from.
 *
 * Mirrors [com.onepage.companion.core.update.UpdateSource]: Gitee first because
 * the GitHub release endpoints are unreliable from mainland China, GitHub as
 * the fallback. This is the same order the firmware's own OTA updater uses.
 */
enum class FirmwareSource(
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
        repo = "onepage-hub",
        latestUrl = "https://gitee.com/api/v5/repos/Yann_Fang/onepage-hub/releases/latest",
        releasesPageUrl = "https://gitee.com/Yann_Fang/onepage-hub/releases",
        repoUrl = "https://gitee.com/Yann_Fang/onepage-hub",
    ),
    GitHub(
        label = "GitHub",
        owner = "YannFang2002",
        repo = "onepage-hub",
        latestUrl = "https://api.github.com/repos/YannFang2002/onepage-hub/releases/latest",
        releasesPageUrl = "https://github.com/YannFang2002/onepage-hub/releases",
        repoUrl = "https://github.com/YannFang2002/onepage-hub",
    ),
    ;

    val displayName: String
        get() = when (this) {
            Gitee -> "Gitee 镜像"
            GitHub -> "GitHub Releases"
        }

    companion object {
        val byPreference: List<FirmwareSource> = listOf(Gitee, GitHub)
    }
}

data class FirmwareLatest(
    val release: FirmwareRelease,
    val source: FirmwareSource,
)
