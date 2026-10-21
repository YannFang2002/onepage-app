package com.onepage.companion.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Joins the reader's hotspot.
 *
 * Android 10+ uses [WifiNetworkSpecifier]: the system shows a one-tap
 * "connect to this network?" sheet and hands us a [Network] to bind the process
 * to — that is the only way a third-party app may join a Wi-Fi network without
 * leaving the app. Android 8/9 falls back to the legacy WifiManager path.
 *
 * The OnePage AP is open (`WIFI:S:CrossPoint-Reader;;`), so the passphrase
 * branches exist mostly for future firmware that adds one.
 */
class WifiJoiner(private val context: Context) {

    data class JoinResult(val gateway: String?, val joined: Boolean)

    private val connectivity: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    suspend fun join(ssid: String, password: String?, wpa3: Boolean = false): JoinResult =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            joinModern(ssid, password, wpa3)
        } else {
            joinLegacy(ssid, password)
        }

    @Suppress("DEPRECATION")
    private suspend fun joinModern(ssid: String, password: String?, wpa3: Boolean): JoinResult {
        val cm = connectivity ?: return JoinResult(null, false)
        val builder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!password.isNullOrEmpty()) {
            if (wpa3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setWpa3Passphrase(password)
            } else {
                builder.setWpa2Passphrase(password)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(builder.build())
            .build()

        release()

        return suspendCancellableCoroutine { cont ->
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    boundNetwork = network
                    // Route our HTTP/WS traffic through the device's AP.
                    cm.bindProcessToNetwork(network)
                    val gateway = gatewayOf(network)
                    if (cont.isActive) cont.resume(JoinResult(gateway, true))
                }

                override fun onUnavailable() {
                    if (cont.isActive) cont.resume(JoinResult(null, false))
                }

                override fun onLost(network: Network) {
                    if (boundNetwork == network) boundNetwork = null
                }
            }
            callback = networkCallback
            runCatching { cm.requestNetwork(request, networkCallback, TIMEOUT_MS.toInt()) }
                .onFailure { if (cont.isActive) cont.resume(JoinResult(null, false)) }

            cont.invokeOnCancellation { release() }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun joinLegacy(ssid: String, password: String?): JoinResult = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext JoinResult(null, false)
        val config = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (password.isNullOrEmpty()) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }
        }
        val netId = runCatching { wifi.addNetwork(config) }.getOrDefault(-1)
        if (netId == -1) return@withContext JoinResult(null, false)

        wifi.disconnect()
        wifi.enableNetwork(netId, true)
        wifi.reconnect()

        val joined = withTimeoutOrNull(TIMEOUT_MS) {
            while (true) {
                if (wifi.connectionInfo?.networkId == netId) break
                kotlinx.coroutines.delay(300)
            }
            true
        } ?: false

        if (!joined) JoinResult(null, false)
        else JoinResult(intToIp(wifi.dhcpInfo?.gateway ?: 0), true)
    }

    private fun gatewayOf(network: Network): String? {
        val cm = connectivity ?: return null
        return runCatching {
            cm.getLinkProperties(network)?.routes
                ?.firstOrNull { it.isDefaultRoute }
                ?.gateway
                ?.hostAddress
        }.getOrNull()
    }

    private fun intToIp(value: Int): String? {
        if (value == 0) return null
        return "${value and 0xFF}.${(value shr 8) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 24) and 0xFF}"
    }

    /** Unbinds and forgets the network so normal connectivity resumes. */
    fun release() {
        runCatching {
            callback?.let { connectivity?.unregisterNetworkCallback(it) }
        }
        callback = null
        runCatching { connectivity?.bindProcessToNetwork(null) }
        boundNetwork = null
    }

    companion object {
        /** The AP's own address on the OnePage softAP subnet. */
        const val DEFAULT_AP_ADDRESS = "192.168.4.1"
        private const val TIMEOUT_MS = 35_000L
    }
}
