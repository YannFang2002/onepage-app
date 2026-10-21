package com.onepage.companion.core.net

import com.onepage.companion.core.model.DeviceStatus
import com.onepage.companion.core.model.FileEntry
import com.onepage.companion.core.model.FontFamily
import com.onepage.companion.core.model.FontFile
import com.onepage.companion.core.model.FontLibrary
import com.onepage.companion.core.model.OpdsServer
import com.onepage.companion.core.model.SettingItem
import com.onepage.companion.core.model.SettingType
import com.onepage.companion.core.model.WifiNetwork
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure decoders for the firmware's JSON. Kept separate from [DeviceApi] so the
 * wire contract can be unit-tested on the JVM without a device.
 *
 * Shapes come from `src/network/CrossPointWebServer.cpp` (handlers
 * handleStatus / handleGetSettings / handleFileListData / handleFontList /
 * handleGetOpdsServers / handleGetWifiNetworks).
 */
object Parsers {

    fun parseStatus(json: String): DeviceStatus {
        val o = JSONObject(json)
        return DeviceStatus(
            version = o.optString("version"),
            ip = o.optString("ip"),
            mode = o.optString("mode"),
            rssi = o.optInt("rssi"),
            freeHeap = o.optLong("freeHeap"),
            uptime = o.optLong("uptime"),
            device = o.optString("device"),
        )
    }

    fun parseSettings(json: String): List<SettingItem> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val key = o.optString("key")
                if (key.isBlank()) continue
                add(parseSetting(o, key))
            }
        }
    }

    private fun parseSetting(o: JSONObject, key: String): SettingItem {
        val type = when (o.optString("type")) {
            "toggle" -> SettingType.TOGGLE
            "enum" -> SettingType.ENUM
            "value" -> SettingType.VALUE
            "string" -> SettingType.STRING
            else -> SettingType.UNKNOWN
        }
        val options = o.optJSONArray("options")?.let { opts ->
            List(opts.length()) { idx -> opts.optString(idx) }
        } ?: emptyList()

        val raw = o.opt("value")
        val intValue = when (raw) {
            is Boolean -> if (raw) 1 else 0
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull() ?: 0
            else -> 0
        }
        val stringValue = if (type == SettingType.STRING) raw?.toString().orEmpty() else ""

        return SettingItem(
            key = key,
            name = o.optString("name", key),
            category = o.optString("category", "其他"),
            type = type,
            intValue = intValue,
            stringValue = stringValue,
            options = options,
            min = o.optInt("min", 0),
            max = o.optInt("max", 100),
            step = o.optInt("step", 1).coerceAtLeast(1),
        )
    }

    fun parseFiles(json: String): List<FileEntry> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name")
                if (name.isBlank()) continue
                add(
                    FileEntry(
                        name = name,
                        size = o.optLong("size"),
                        isDirectory = o.optBoolean("isDirectory"),
                        isEpub = o.optBoolean("isEpub"),
                    )
                )
            }
        }
    }

    fun parseFonts(json: String): FontLibrary {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("families") ?: JSONArray()
        val families = buildList {
            for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                val sizesArr = f.optJSONArray("sizes")
                val sizes = buildList {
                    if (sizesArr != null) for (s in 0 until sizesArr.length()) add(sizesArr.optInt(s))
                }
                val filesArr = f.optJSONArray("files")
                val files = buildList {
                    if (filesArr != null) for (s in 0 until filesArr.length()) {
                        val fo = filesArr.optJSONObject(s) ?: continue
                        add(FontFile(fo.optString("name"), fo.optLong("size")))
                    }
                }
                add(FontFamily(f.optString("name"), sizes, files))
            }
        }
        return FontLibrary(families, obj.optInt("maxFamilies"))
    }

    fun parseOpds(json: String): List<OpdsServer> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    OpdsServer(
                        index = o.optInt("index", i),
                        name = o.optString("name"),
                        url = o.optString("url"),
                        username = o.optString("username"),
                        hasPassword = o.optBoolean("hasPassword"),
                    )
                )
            }
        }
    }

    fun parseWifi(json: String): List<WifiNetwork> {
        val arr = JSONArray(json)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    WifiNetwork(
                        index = o.optInt("index", i),
                        ssid = o.optString("ssid"),
                        hasPassword = o.optBoolean("hasPassword"),
                        isLastConnected = o.optBoolean("isLastConnected"),
                    )
                )
            }
        }
    }

    /**
     * Device replies to a UDP `hello` with `crosspoint (on <hostname>);<wsPort>`
     * (see CrossPointWebServer::handleClient). Returns null for foreign packets.
     */
    fun parseDiscoveryReply(text: String, host: String): com.onepage.companion.core.model.DiscoveredDevice? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("crosspoint", ignoreCase = true)) return null
        val hostname = Regex("\\(on ([^)]+)\\)").find(trimmed)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val wsPort = trimmed.substringAfterLast(';', "81").trim().toIntOrNull() ?: 81
        return com.onepage.companion.core.model.DiscoveredDevice(host = host, wsPort = wsPort, hostname = hostname)
    }
}
