package com.onepage.companion.core.model

/** `GET /api/status` */
data class DeviceStatus(
    val version: String = "",
    val ip: String = "",
    val mode: String = "",
    val rssi: Int = 0,
    val freeHeap: Long = 0,
    val uptime: Long = 0,
    val device: String = "",
) {
    val isAp: Boolean get() = mode.equals("AP", ignoreCase = true)

    val freeHeapKb: Int get() = (freeHeap / 1024).toInt()

    /** Signal strength 0..4 derived the same way the device UI does it. */
    val signalBars: Int
        get() = when {
            isAp -> 4
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            rssi >= -85 -> 1
            else -> 0
        }

    val uptimeLabel: String
        get() {
            val s = uptime.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}

enum class SettingType { TOGGLE, ENUM, VALUE, STRING, UNKNOWN }

/** One row of `GET /api/settings` — the device is the schema authority. */
data class SettingItem(
    val key: String,
    val name: String,
    val category: String,
    val type: SettingType,
    val intValue: Int = 0,
    val stringValue: String = "",
    val options: List<String> = emptyList(),
    val min: Int = 0,
    val max: Int = 100,
    val step: Int = 1,
) {
    val isToggle: Boolean get() = type == SettingType.TOGGLE
    val isEnum: Boolean get() = type == SettingType.ENUM
    val isRange: Boolean get() = type == SettingType.VALUE
    val isText: Boolean get() = type == SettingType.STRING

    /** Human readable current value, used in collapsed rows and summaries. */
    val valueLabel: String
        get() = when (type) {
            SettingType.TOGGLE -> if (intValue != 0) "开启" else "关闭"
            SettingType.ENUM -> options.getOrNull(intValue) ?: "—"
            SettingType.VALUE -> intValue.toString()
            SettingType.STRING -> stringValue.ifBlank { "未设置" }
            SettingType.UNKNOWN -> ""
        }

    fun withInt(value: Int): SettingItem = copy(intValue = value)

    fun withString(value: String): SettingItem = copy(stringValue = value)
}

/** Settings grouped by the device-provided category label. */
data class SettingGroup(
    val category: String,
    val items: List<SettingItem>,
)

/** `GET /api/files?path=` */
data class FileEntry(
    val name: String,
    val size: Long = 0,
    val isDirectory: Boolean = false,
    val isEpub: Boolean = false,
) {
    val isImage: Boolean
        get() = name.lowercase().let { it.endsWith(".png") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".bmp") }

    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}

data class FontFile(val name: String, val size: Long)

data class FontFamily(
    val name: String,
    val sizes: List<Int> = emptyList(),
    val files: List<FontFile> = emptyList(),
) {
    val sizeLabel: String
        get() = if (sizes.isEmpty()) "—" else sizes.joinToString(" / ")

    val totalBytes: Long get() = files.sumOf { it.size }
}

data class FontLibrary(val families: List<FontFamily> = emptyList(), val maxFamilies: Int = 0)

data class OpdsServer(
    val index: Int,
    val name: String,
    val url: String,
    val username: String = "",
    val hasPassword: Boolean = false,
)

data class WifiNetwork(
    val index: Int,
    val ssid: String,
    val hasPassword: Boolean = false,
    val isLastConnected: Boolean = false,
)

/** A device the user has connected to before. */
data class SavedDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 80,
    val wsPort: Int = 81,
    val lastSeen: Long = 0L,
) {
    val address: String get() = "$host:$port"
}

/** A device found by UDP discovery, before the user saves it. */
data class DiscoveredDevice(
    val host: String,
    val wsPort: Int,
    val hostname: String = "",
)

class ApiException(val statusCode: Int, message: String) : Exception(message)
