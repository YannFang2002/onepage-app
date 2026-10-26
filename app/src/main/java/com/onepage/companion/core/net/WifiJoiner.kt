package com.onepage.companion.core.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
 * The OnePage AP is open (`WIFI:S:CrossPoint-Reader;;`), so no passphrase is
 * given; the WPA branches exist for firmware that adds one later.
 *
 * Two details matter for open networks:
 *  - the request must not require `NET_CAPABILITY_INTERNET`, because the reader
 *    has no uplink and some ROMs never declare such a network "satisfied";
 *  - `NEARBY_WIFI_DEVICES` (Android 13+) / `ACCESS_FINE_LOCATION` (older) must
 *    be granted at runtime, otherwise the request throws / is dropped and the
 *    user only sees "could not connect".
 */
class WifiJoiner(private val context: Context) {

    private val connectivity: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** The runtime permission the platform gates hotspot requests behind. */
    val joinPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        joinPermission,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun join(ssid: String, password: String?, wpa3: Boolean = false): WifiJoinResult {
        if (!hasPermission()) return WifiJoinResult(failure = WifiJoinFailure.Permission)
        if (!isWifiEnabled()) return WifiJoinResult(failure = WifiJoinFailure.WifiDisabled)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            joinModern(ssid, password, wpa3)
        } else {
            joinLegacy(ssid, password)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun joinModern(ssid: String, password: String?, wpa3: Boolean): WifiJoinResult {
        val cm = connectivity
            ?: return WifiJoinResult(failure = WifiJoinFailure.Error, detail = "系统没有 ConnectivityManager")

        val builder = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setIsHiddenSsid(false)
        if (!password.isNullOrEmpty()) {
            if (wpa3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setWpa3Passphrase(password)
            } else {
                builder.setWpa2Passphrase(password)
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            // The reader's softAP has no uplink; requiring INTERNET makes some
            // ROMs keep the request unsatisfied forever.
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(builder.build())
            .build()

        release()
        val startedAt = SystemClock.elapsedRealtime()

        return suspendCancellableCoroutine { cont ->
            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    boundNetwork = network
                    // Route our HTTP/WS traffic through the device's AP.
                    cm.bindProcessToNetwork(network)
                    if (cont.isActive) cont.resume(WifiJoinResult(joined = true, gateway = gatewayOf(network)))
                }

                override fun onUnavailable() {
                    val waited = SystemClock.elapsedRealtime() - startedAt
                    val failure = if (waited >= TIMEOUT_MS - TIMEOUT_GRACE_MS) {
                        WifiJoinFailure.Timeout
                    } else {
                        WifiJoinFailure.Declined
                    }
                    if (cont.isActive) cont.resume(WifiJoinResult(failure = failure))
                }

                override fun onLost(network: Network) {
                    if (boundNetwork == network) boundNetwork = null
                }
            }
            callback = networkCallback
            try {
                cm.requestNetwork(request, networkCallback, TIMEOUT_MS.toInt())
            } catch (security: SecurityException) {
                // Permission revoked between the check above and the request.
                if (cont.isActive) {
                    cont.resume(WifiJoinResult(failure = WifiJoinFailure.Permission, detail = security.message))
                }
            } catch (t: Throwable) {
                if (cont.isActive) {
                    cont.resume(WifiJoinResult(failure = WifiJoinFailure.Error, detail = t.message))
                }
            }

            cont.invokeOnCancellation { release() }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun joinLegacy(ssid: String, password: String?): WifiJoinResult = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext WifiJoinResult(failure = WifiJoinFailure.Error, detail = "系统没有 WifiManager")
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
        if (netId == -1) {
            return@withContext WifiJoinResult(failure = WifiJoinFailure.Error, detail = "系统拒绝添加网络配置")
        }

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

        if (!joined) {
            WifiJoinResult(failure = WifiJoinFailure.Timeout)
        } else {
            WifiJoinResult(joined = true, gateway = intToIp(wifi.dhcpInfo?.gateway ?: 0))
        }
    }

    private fun isWifiEnabled(): Boolean {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return false
        return runCatching { wifi.isWifiEnabled }.getOrDefault(false)
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

        private const val TIMEOUT_MS = 30_000L
        private const val TIMEOUT_GRACE_MS = 1_500L
    }
}
