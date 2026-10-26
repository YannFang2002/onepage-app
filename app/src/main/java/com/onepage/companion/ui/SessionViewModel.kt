package com.onepage.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onepage.companion.OnePageApp
import com.onepage.companion.core.data.DeviceStore
import com.onepage.companion.core.model.ApiException
import com.onepage.companion.core.model.DeviceStatus
import com.onepage.companion.core.model.DiscoveredDevice
import com.onepage.companion.core.model.SavedDevice
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.core.net.Discovery
import com.onepage.companion.core.net.WifiJoinFailure
import com.onepage.companion.core.net.WifiJoinResult
import com.onepage.companion.core.net.WifiJoiner
import com.onepage.companion.core.net.wifiJoinMessage
import com.onepage.companion.core.qr.QrPayloadParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class Phase { Idle, Connecting, Connected, Failed }

/** Set when the reader's hotspot could not be joined automatically. */
data class ManualJoinHint(
    val ssid: String,
    val failure: WifiJoinFailure?,
    /** True when granting a runtime permission is what unblocks the join. */
    val needsPermission: Boolean,
)

data class SessionUiState(
    val phase: Phase = Phase.Idle,
    val device: SavedDevice? = null,
    val status: DeviceStatus? = null,
    val error: String? = null,
    val saved: List<SavedDevice> = emptyList(),
    val discovered: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val connectingLabel: String? = null,
    val lastRefreshedAt: Long = 0L,
    val manualJoin: ManualJoinHint? = null,
)

class SessionViewModel(app: Application) : AndroidViewModel(app) {

    private val http = (app as OnePageApp).http
    private val store = DeviceStore(app)
    private val discovery = Discovery(app)
    private val joiner = WifiJoiner(app)

    var api: DeviceApi? = null
        private set

    private val _state = MutableStateFlow(SessionUiState(saved = store.devices()))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var manualJoinJob: Job? = null

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, discovered = emptyList()) }
        viewModelScope.launch {
            runCatching { discovery.scan() }
                .onSuccess { found -> _state.update { it.copy(discovered = found) } }
                .onFailure { /* discovery is best-effort; the user can type an IP */ }
            _state.update { it.copy(scanning = false) }
        }
    }

    fun connect(
        host: String,
        port: Int = 80,
        wsPort: Int = Discovery.DEFAULT_WS_PORT,
        name: String? = null,
        remember: Boolean = true,
    ) {
        val cleanHost = host.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (cleanHost.isBlank()) return
        cancelManualJoinWatch()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = Phase.Connecting,
                    connectingLabel = "正在连接设备…",
                    error = null,
                    manualJoin = null,
                )
            }
            connectInternal(cleanHost, port, wsPort, name, remember)
        }
    }

    /** Handles a scanned QR: either join the device hotspot, or dial the URL it encodes. */
    fun connectViaQr(raw: String) {
        when (val payload = QrPayloadParser.parse(raw)) {
            is QrPayloadParser.Payload.Wifi -> joinHotspotAndConnect(payload)
            is QrPayloadParser.Payload.Endpoint -> connect(payload.host, payload.port)
            null -> _state.update {
                it.copy(
                    phase = Phase.Failed,
                    error = "无法识别的二维码：请扫阅读器「文件传输」页上的 Wi-Fi 码或网址码",
                )
            }
        }
    }

    private fun joinHotspotAndConnect(wifi: QrPayloadParser.Payload.Wifi) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    phase = Phase.Connecting,
                    connectingLabel = "正在加入「${wifi.ssid}」…",
                    error = null,
                    manualJoin = null,
                )
            }
            val result = runCatching {
                joiner.join(wifi.ssid, wifi.password, wpa3 = wifi.security.equals("SAE", true))
            }.getOrElse { WifiJoinResult(failure = WifiJoinFailure.Error, detail = it.message) }

            if (!result.joined) {
                _state.update {
                    it.copy(
                        phase = Phase.Failed,
                        connectingLabel = null,
                        error = wifiJoinMessage(wifi.ssid, result.failure, result.detail),
                        manualJoin = ManualJoinHint(
                            ssid = wifi.ssid,
                            failure = result.failure,
                            needsPermission = !joiner.hasPermission(),
                        ),
                    )
                }
                watchForManualJoin()
                return@launch
            }

            val host = result.gateway ?: WifiJoiner.DEFAULT_AP_ADDRESS
            _state.update { it.copy(connectingLabel = "已连上热点，正在访问 $host…") }
            if (!connectInternal(host, 80, Discovery.DEFAULT_WS_PORT, name = null, remember = true)) {
                // Some ROMs report the open network as available but keep routing
                // traffic elsewhere (or the AP's DHCP is late). Keep probing that
                // address; a manual tap in the Wi-Fi list is the other way out.
                _state.update {
                    it.copy(
                        phase = Phase.Failed,
                        connectingLabel = null,
                        error = "已加入「${wifi.ssid}」，但访问不到 $host。App 会自动重试，也可以到 Wi-Fi 列表里手动确认一次。",
                        manualJoin = ManualJoinHint(wifi.ssid, WifiJoinFailure.Error, needsPermission = false),
                    )
                }
                watchForManualJoin(host)
            }
        }
    }

    /**
     * The system sheet is the only path on Android 10+, and some ROMs drop it for
     * open networks. When it fails we send the user to the Wi-Fi list, but keep
     * probing the reader's AP so the app finishes the connection by itself once
     * they tap the network — no second QR scan needed.
     */
    private fun watchForManualJoin(host: String = WifiJoiner.DEFAULT_AP_ADDRESS) {
        manualJoinJob?.cancel()
        manualJoinJob = viewModelScope.launch {
            val probeClient = http.newBuilder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val probe = DeviceApi(probeClient, host, 80, Discovery.DEFAULT_WS_PORT)
            repeat(MANUAL_JOIN_PROBES) {
                delay(MANUAL_JOIN_INTERVAL_MS)
                if (!isActive) return@launch
                if (runCatching { probe.status() }.isSuccess) {
                    _state.update {
                        it.copy(
                            phase = Phase.Connecting,
                            connectingLabel = "已连上热点，正在访问 $host…",
                            error = null,
                            manualJoin = null,
                        )
                    }
                    connectInternal(host, 80, Discovery.DEFAULT_WS_PORT, name = null, remember = true)
                    return@launch
                }
            }
        }
    }

    private fun cancelManualJoinWatch() {
        manualJoinJob?.cancel()
        manualJoinJob = null
    }

    private suspend fun connectInternal(
        cleanHost: String,
        port: Int,
        wsPort: Int,
        name: String?,
        remember: Boolean,
    ): Boolean {
        val candidate = DeviceApi(http, cleanHost, port, wsPort)
        return try {
            val status = candidate.status()
            api = candidate
            val saved = SavedDevice(
                id = "$cleanHost:$port",
                name = name?.takeIf { it.isNotBlank() }
                    ?: store.devices().firstOrNull { it.host == cleanHost }?.name
                    ?: status.device.ifBlank { cleanHost },
                host = cleanHost,
                port = port,
                wsPort = wsPort,
                lastSeen = System.currentTimeMillis(),
            )
            if (remember) store.save(saved) else store.touch(saved.id)
            _state.update {
                it.copy(
                    phase = Phase.Connected,
                    device = saved,
                    status = status,
                    saved = store.devices(),
                    error = null,
                    connectingLabel = null,
                    lastRefreshedAt = System.currentTimeMillis(),
                )
            }
            startPolling()
            true
        } catch (t: Throwable) {
            api = null
            _state.update {
                it.copy(
                    phase = Phase.Failed,
                    connectingLabel = null,
                    error = describe(t),
                    status = null,
                )
            }
            false
        }
    }

    fun connectTo(saved: SavedDevice) {
        // A hotspot joined by QR keeps working; anything else should not stay bound.
        if (saved.host != WifiJoiner.DEFAULT_AP_ADDRESS) joiner.release()
        connect(saved.host, saved.port, saved.wsPort, saved.name)
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        cancelManualJoinWatch()
        api = null
        joiner.release()
        _state.update {
            it.copy(
                phase = Phase.Idle,
                device = null,
                status = null,
                error = null,
                connectingLabel = null,
                manualJoin = null,
            )
        }
    }

    fun forget(saved: SavedDevice) {
        store.remove(saved.id)
        _state.update { it.copy(saved = store.devices()) }
    }

    fun renameSaved(saved: SavedDevice, name: String) {
        store.save(saved.copy(name = name))
        _state.update { s ->
            s.copy(
                saved = store.devices(),
                device = if (s.device?.id == saved.id) saved.copy(name = name) else s.device,
            )
        }
    }

    fun refreshStatus() {
        val current = api ?: return
        viewModelScope.launch {
            runCatching { current.status() }
                .onSuccess { status ->
                    _state.update {
                        it.copy(status = status, lastRefreshedAt = System.currentTimeMillis(), error = null)
                    }
                }
                .onFailure { t ->
                    _state.update { it.copy(error = describe(t)) }
                }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(6_000)
                val current = api ?: break
                runCatching { current.status() }
                    .onSuccess { status ->
                        _state.update {
                            it.copy(status = status, lastRefreshedAt = System.currentTimeMillis(), error = null)
                        }
                    }
                    .onFailure { t ->
                        _state.update { it.copy(error = describe(t)) }
                    }
            }
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is ApiException -> t.message ?: "设备返回 ${t.statusCode}"
        is java.net.SocketTimeoutException -> "连接超时，请确认手机与设备在同一网络"
        is java.net.UnknownHostException -> "找不到该地址"
        is IOException -> "网络不可达：${t.message ?: "请检查 Wi-Fi"}"
        else -> t.message ?: t::class.java.simpleName
    }

    private companion object {
        /** 2 s × 45 = 90 s of watching for the user to finish the manual join. */
        const val MANUAL_JOIN_PROBES = 45
        const val MANUAL_JOIN_INTERVAL_MS = 2_000L
    }
}
