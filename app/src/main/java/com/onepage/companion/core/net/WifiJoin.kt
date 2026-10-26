package com.onepage.companion.core.net

/**
 * Why an automatic join of the reader's hotspot did not finish.
 *
 * Kept platform-free so the wording (and the unit tests around it) do not need
 * an Android runtime.
 */
enum class WifiJoinFailure {
    /** Missing location / nearby-devices runtime permission. */
    Permission,

    /** Wi-Fi radio is switched off. */
    WifiDisabled,

    /** The system sheet was dismissed, or the request was rejected. */
    Declined,

    /** Nothing happened within the request timeout. */
    Timeout,

    /** Anything else; [WifiJoinResult.detail] carries the platform message. */
    Error,
}

data class WifiJoinResult(
    val joined: Boolean = false,
    val gateway: String? = null,
    val failure: WifiJoinFailure? = null,
    val detail: String? = null,
)

/**
 * One-sentence, actionable explanation for a failed auto-join.
 *
 * The reader's AP has no internet, so every failure ends with the manual
 * fallback — the point is telling the user *which* button to press next.
 */
fun wifiJoinMessage(ssid: String, failure: WifiJoinFailure?, detail: String? = null): String =
    when (failure) {
        WifiJoinFailure.Permission ->
            "缺少「附近的设备」权限，App 没法自己加入「$ssid」。允许权限后可自动加入，也可以现在手动连接。"
        WifiJoinFailure.WifiDisabled ->
            "手机 Wi-Fi 是关闭的。打开 Wi-Fi 后再试，或在系统 Wi-Fi 列表里选择「$ssid」。"
        WifiJoinFailure.Declined ->
            "系统没有连上「$ssid」：可能取消了弹窗，或被系统的「自动切换网络」打断了。可以重试，或手动连接。"
        WifiJoinFailure.Timeout ->
            "等待加入「$ssid」超时。请手动连接一次，或再扫一次码。"
        WifiJoinFailure.Error, null ->
            detail?.takeIf { it.isNotBlank() }?.let { "自动加入「$ssid」失败：$it" }
                ?: "自动加入「$ssid」失败，请手动连接。"
    }
