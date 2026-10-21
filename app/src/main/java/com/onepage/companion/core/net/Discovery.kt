package com.onepage.companion.core.net

import android.content.Context
import android.net.wifi.WifiManager
import com.onepage.companion.core.model.DiscoveredDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * The firmware answers a UDP "hello" on port 8134 with
 * `crosspoint (on <hostname>);<wsPort>` — see CrossPointWebServer::handleClient.
 * mDNS is compiled out on the OnePage C61 build, so this is the only automatic
 * discovery channel we can rely on.
 */
class Discovery(private val context: Context) {

    suspend fun scan(rounds: Int = 3, windowMs: Long = 1500): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, DiscoveredDevice>()
            val payload = "hello".toByteArray()

            DatagramSocket().use { socket ->
                runCatching {
                    socket.broadcast = true
                    socket.soTimeout = 250
                }

                val targets = buildList {
                    add(runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull())
                    addAll(interfaceBroadcasts())
                }.filterNotNull().distinctBy { it.hostAddress }

                for (round in 0 until rounds) {
                    targets.forEach { addr ->
                        runCatching {
                            socket.send(DatagramPacket(payload, payload.size, addr, DISCOVERY_PORT))
                        }
                    }
                    val deadline = System.currentTimeMillis() + windowMs / rounds
                    while (System.currentTimeMillis() < deadline) {
                        val buf = ByteArray(256)
                        val packet = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(packet)
                        } catch (_: Exception) {
                            delay(20)
                            continue
                        }
                        val text = String(packet.data, 0, packet.length).trim()
                        val host = packet.address?.hostAddress ?: continue
                        val parsed = parseReply(text, host) ?: continue
                        found[host] = parsed
                    }
                }
            }
            found.values.toList()
        }

    private fun parseReply(text: String, host: String): DiscoveredDevice? {
        if (!text.startsWith("crosspoint", ignoreCase = true)) return null
        val hostname = Regex("\\(on ([^)]+)\\)").find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val wsPort = text.substringAfterLast(';', "81").trim().toIntOrNull() ?: 81
        return DiscoveredDevice(host = host, wsPort = wsPort, hostname = hostname)
    }

    @Suppress("DEPRECATION")
    private fun interfaceBroadcasts(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        // Prefer the Wi-Fi interface's own broadcast address.
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wm.dhcpInfo
            if (dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                val broadcast = dhcp.ipAddress or dhcp.netmask.inv()
                result += intToInet(broadcast)
            }
        }
        // Fallback: derive from every non-loopback IPv4 interface.
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                if (!nif.isUp || nif.isLoopback) return@forEach
                nif.interfaceAddresses.forEach { ia ->
                    ia.broadcast?.let { if (it is InetAddress) result += it }
                }
            }
        }
        return result
    }

    private fun intToInet(value: Int): InetAddress =
        InetAddress.getByAddress(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            )
        )

    companion object {
        const val DISCOVERY_PORT = 8134
        const val DEFAULT_WS_PORT = 81
    }
}
