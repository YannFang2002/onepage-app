package com.onepage.companion.core.data

import android.content.Context
import com.onepage.companion.core.model.SavedDevice
import org.json.JSONArray
import org.json.JSONObject

/** Persists the list of devices the user has paired with. */
class DeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences("onepage.devices", Context.MODE_PRIVATE)

    fun devices(): List<SavedDevice> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SavedDevice(
                            id = o.optString("id"),
                            name = o.optString("name", o.optString("host")),
                            host = o.optString("host"),
                            port = o.optInt("port", 80),
                            wsPort = o.optInt("wsPort", 81),
                            lastSeen = o.optLong("lastSeen"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(device: SavedDevice) {
        val list = devices().toMutableList()
        val idx = list.indexOfFirst { it.id == device.id || (it.host == device.host) }
        if (idx >= 0) list[idx] = device else list.add(device)
        write(list)
    }

    fun remove(id: String) {
        write(devices().filterNot { it.id == id })
    }

    fun touch(id: String, timestamp: Long = System.currentTimeMillis()) {
        val list = devices().map { if (it.id == id) it.copy(lastSeen = timestamp) else it }
        write(list)
    }

    private fun write(list: List<SavedDevice>) {
        val arr = JSONArray()
        list.forEach { d ->
            arr.put(
                JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("host", d.host)
                    put("port", d.port)
                    put("wsPort", d.wsPort)
                    put("lastSeen", d.lastSeen)
                }
            )
        }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        private const val KEY_LIST = "list"
    }
}
