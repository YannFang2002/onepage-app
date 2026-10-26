package com.onepage.companion.core.firmware

import com.onepage.companion.core.update.AppVersion
import com.onepage.companion.core.update.formatReleaseDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Asset name `/api/ota` expects: the app-only image, not the 0x0 web-flasher image. */
const val FIRMWARE_ASSET = "firmware.bin"

/**
 * Hub firmware reports `onepage-hub-<version>` in `/api/status`; the reader
 * firmware reports plain `onepage-<version>`. Only the hub build exposes
 * `/api/ota`, so only that one gets in-app firmware updates.
 */
fun isHubFirmware(version: String?): Boolean =
    version?.trim()?.lowercase()?.startsWith("onepage-hub") == true

data class FirmwareAsset(
    val name: String,
    val url: String,
    val size: Long = 0,
)

data class FirmwareRelease(
    val tag: String,
    val notes: String = "",
    val pageUrl: String = "",
    val publishedAt: String = "",
    val assets: List<FirmwareAsset> = emptyList(),
) {
    val version: String get() = AppVersion.label(tag)

    /** The only asset the device accepts; null when the release is source-only. */
    val firmware: FirmwareAsset? get() = assets.firstOrNull { it.name.equals(FIRMWARE_ASSET, ignoreCase = true) }

    val publishedDate: String get() = formatReleaseDate(publishedAt)
}

/**
 * `GET /api/ota/status` on the reader.
 *
 * After a successful flash the device reboots with a fresh server, so the final
 * proof of success is not the in-RAM stage but `lastResult`, which the firmware
 * persists as `/system/.crosspoint/ota_result.json` right before restarting:
 * `{"ok":true,"version":"onepage-hub-0.2.2","slot":"app1"}`.
 */
data class FirmwareOtaStatus(
    val stage: String,
    val percent: Int = 0,
    val received: Long = 0,
    val message: String = "",
    val lastResultOk: Boolean? = null,
    val lastResultVersion: String = "",
    val lastResultSlot: String = "",
) {
    val isDone: Boolean get() = stage.equals("Done", ignoreCase = true)
    val isFailed: Boolean get() = stage.equals("Failed", ignoreCase = true)
    val isFinished: Boolean get() = isDone || isFailed

    /** True when the device already rebooted into the new image. */
    val appliedAfterReboot: Boolean get() = lastResultOk == true
}

class FirmwareException(message: String, cause: Throwable? = null) : Exception(message, cause)

object FirmwareParser {

    /**
     * @param fallbackPageUrl release page of the mirror that answered, used when
     *   the payload has no `html_url`.
     */
    fun parse(json: String, fallbackPageUrl: String = ""): FirmwareRelease {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw FirmwareException("无法解析固件源返回的数据", e)
        }
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) throw FirmwareException("固件源未返回版本号（tag_name 为空）")

        return FirmwareRelease(
            tag = tag,
            notes = root.optString("body").trim(),
            pageUrl = root.optString("html_url").trim().ifBlank { fallbackPageUrl },
            publishedAt = root.optString("published_at").trim().ifBlank { root.optString("created_at").trim() },
            assets = parseAssets(root.optJSONArray("assets")),
        )
    }

    /** Parses `{stage,percent,received,message,lastResult}` from `/api/ota/status`. */
    fun parseOtaStatus(json: String): FirmwareOtaStatus {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw FirmwareException("无法解析设备的 OTA 状态", e)
        }
        val last = root.optJSONObject("lastResult")
        return FirmwareOtaStatus(
            stage = root.optString("stage").trim().ifBlank { "Idle" },
            percent = root.optInt("percent").coerceIn(0, 100),
            received = root.optLong("received").coerceAtLeast(0),
            message = root.optString("message").trim(),
            lastResultOk = last?.let { if (it.has("ok")) it.optBoolean("ok") else null },
            lastResultVersion = last?.optString("version").orEmpty().trim(),
            lastResultSlot = last?.optString("slot").orEmpty().trim(),
        )
    }

    private fun parseAssets(array: JSONArray?): List<FirmwareAsset> {
        if (array == null) return emptyList()
        val assets = ArrayList<FirmwareAsset>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("browser_download_url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            assets += FirmwareAsset(name = name, url = url, size = item.optLong("size"))
        }
        return assets
    }
}
