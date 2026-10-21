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
import com.onepage.companion.core.net.WifiJoiner
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

enum class Phase { Idle, Connecting, Connected, Failed }

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
        viewModelScope.launch {
            _state.update { it.copy(phase = Phase.Connecting, connectingLabel = "正在连接设备…", error = null) }
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
                )
            }
            val result = runCatching {
                joiner.join(wifi.ssid, wifi.password, wpa3 = wifi.security.equals("SAE", true))
            }.getOrDefault(WifiJoiner.JoinResult(null, false))

            if (!result.joined) {
                _state.update {
                    it.copy(
                        phase = Phase.Failed,
                        connectingLabel = null,
                        error = "没能加入「${wifi.ssid}」。请在系统 Wi-Fi 列表里选中它，或再扫一次码。",
                    )
                }
                return@launch
            }

            val host = result.gateway ?: WifiJoiner.DEFAULT_AP_ADDRESS
            _state.update { it.copy(connectingLabel = "已连上热点，正在访问 $host…") }
            connectInternal(host, 80, Discovery.DEFAULT_WS_PORT, name = null, remember = true)
        }
    }

    private suspend fun connectInternal(
        cleanHost: String,
        port: Int,
        wsPort: Int,
        name: String?,
        remember: Boolean,
    ) {
        val candidate = DeviceApi(http, cleanHost, port, wsPort)
        try {
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
        api = null
        joiner.release()
        _state.update {
            it.copy(phase = Phase.Idle, device = null, status = null, error = null, connectingLabel = null)
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
}
