package com.onepage.companion.ui.screens.firmware

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onepage.companion.OnePageApp
import com.onepage.companion.core.firmware.FirmwareApi
import com.onepage.companion.core.firmware.FirmwareException
import com.onepage.companion.core.firmware.FirmwareOtaStatus
import com.onepage.companion.core.firmware.FirmwareRelease
import com.onepage.companion.core.firmware.FirmwareSource
import com.onepage.companion.core.firmware.isHubFirmware
import com.onepage.companion.core.model.ApiException
import com.onepage.companion.core.net.DeviceApi
import com.onepage.companion.core.update.AppVersion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class FirmwarePhase { Idle, Checking, UpToDate, Available, Downloading, Uploading, Flashing, Done, Failed }

data class FirmwareUiState(
    val phase: FirmwarePhase = FirmwarePhase.Idle,
    val currentVersion: String = "",
    val release: FirmwareRelease? = null,
    val source: FirmwareSource? = null,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val checkedAt: Long = 0L,
) {
    val latestVersion: String? get() = release?.version

    val hasUpdate: Boolean get() = phase == FirmwarePhase.Available

    val busy: Boolean
        get() = phase == FirmwarePhase.Checking ||
            phase == FirmwarePhase.Downloading ||
            phase == FirmwarePhase.Uploading ||
            phase == FirmwarePhase.Flashing

    val sourceLabel: String get() = source?.displayName ?: "—"

    val firmwareBytes: Long get() = release?.firmware?.size ?: 0L

    /** Short line for the 更多 list entry. */
    val summary: String
        get() = when (phase) {
            FirmwarePhase.Idle -> "当前 $currentVersion"
            FirmwarePhase.Checking -> "正在查询固件版本…"
            FirmwarePhase.Available -> "发现新固件 ${release?.tag ?: latestVersion.orEmpty()}"
            FirmwarePhase.UpToDate -> "当前 $currentVersion · 已是最新"
            FirmwarePhase.Downloading -> "正在下载固件…"
            FirmwarePhase.Uploading -> "正在写入设备…"
            FirmwarePhase.Flashing -> "设备正在校验/写入…"
            FirmwarePhase.Done -> "升级完成，设备正在重启"
            FirmwarePhase.Failed -> "升级失败 · ${error ?: "未知错误"}"
        }
}

/**
 * Firmware OTA for the hub build (`onepage-hub-*`).
 *
 * Mirrors the flow of the firmware's own web settings page: pull the newest
 * release from Gitee (GitHub as fallback), download the `firmware.bin` asset,
 * POST it to `/api/ota`, then poll `/api/ota/status` until the reader reports
 * Done and reboots.
 */
class FirmwareViewModel(
    app: Application,
    private val api: DeviceApi,
    private val currentVersion: String,
) : AndroidViewModel(app) {

    private val firmwareApi = FirmwareApi((app as OnePageApp).http)

    private val _state = MutableStateFlow(
        FirmwareUiState(currentVersion = currentVersion, phase = FirmwarePhase.Idle),
    )
    val state: StateFlow<FirmwareUiState> = _state.asStateFlow()

    private var job: Job? = null

    val supported: Boolean get() = isHubFirmware(currentVersion)

    fun check() {
        if (!supported) return
        if (_state.value.phase == FirmwarePhase.Checking || isInstalling()) return
        job?.cancel()
        _state.update { it.copy(phase = FirmwarePhase.Checking, error = null) }
        job = viewModelScope.launch {
            runCatching { firmwareApi.latest() }
                .onSuccess { latest ->
                    val newer = AppVersion.isNewer(latest.release.version, currentVersion)
                    _state.update {
                        it.copy(
                            phase = if (newer) FirmwarePhase.Available else FirmwarePhase.UpToDate,
                            release = latest.release,
                            source = latest.source,
                            error = null,
                            checkedAt = System.currentTimeMillis(),
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            phase = FirmwarePhase.Failed,
                            error = describe(t),
                            checkedAt = System.currentTimeMillis(),
                        )
                    }
                }
        }
    }

    /** Download → upload → poll. Safe to call only from [FirmwarePhase.Available]. */
    fun install() {
        val release = _state.value.release ?: return
        val asset = release.firmware
        if (asset == null) {
            _state.update {
                it.copy(phase = FirmwarePhase.Failed, error = "这个发行版里没有 firmware.bin，无法 OTA")
            }
            return
        }
        if (isInstalling()) return
        job?.cancel()
        job = viewModelScope.launch {
            val dest = File(getApplication<Application>().cacheDir, "ota-${release.version}.bin")
            try {
                _state.update {
                    it.copy(
                        phase = FirmwarePhase.Downloading,
                        progress = 0f,
                        progressLabel = "正在下载固件",
                        error = null,
                    )
                }
                firmwareApi.download(asset, dest) { written, total ->
                    _state.update {
                        it.copy(
                            progress = fraction(written, total),
                            progressLabel = "正在下载固件 ${percent(written, total)}%",
                        )
                    }
                }

                _state.update {
                    it.copy(
                        phase = FirmwarePhase.Uploading,
                        progress = 0f,
                        progressLabel = "正在上传到设备",
                    )
                }
                val uploadError = runCatching {
                    api.uploadFirmware(dest) { sent, total ->
                        _state.update {
                            it.copy(
                                progress = fraction(sent, total),
                                progressLabel = "正在上传到设备 ${percent(sent, total)}%",
                            )
                        }
                    }
                }.exceptionOrNull()

                // The reader may close the socket while it starts flashing; only
                // treat that as fatal when it never picked the image up.
                if (uploadError != null && !deviceTookOver()) {
                    throw uploadError
                }

                _state.update {
                    it.copy(
                        phase = FirmwarePhase.Flashing,
                        progress = 0f,
                        progressLabel = "设备正在校验镜像",
                    )
                }
                watchFlashing()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        phase = FirmwarePhase.Failed,
                        error = describe(t),
                        progressLabel = "",
                    )
                }
            } finally {
                dest.delete()
            }
        }
    }

    private fun isInstalling(): Boolean = _state.value.phase in setOf(
        FirmwarePhase.Downloading,
        FirmwarePhase.Uploading,
        FirmwarePhase.Flashing,
    )

    /** True when the device already staged the image (stage past Idle). */
    private suspend fun deviceTookOver(): Boolean = runCatching {
        val status = api.otaStatus()
        status.stage.equals("Receiving", true) ||
            status.stage.equals("Ready", true) ||
            status.stage.equals("Verifying", true) ||
            status.stage.equals("Writing", true) ||
            status.isDone
    }.getOrDefault(false)

    private suspend fun watchFlashing() {
        var last: FirmwareOtaStatus? = null
        var misses = 0
        repeat(FLASH_POLLS) {
            delay(FLASH_INTERVAL_MS)
            val status = runCatching { api.otaStatus() }.getOrNull()
            if (status == null) {
                misses++
                // The reader reboots right after Done; a few dropped polls then
                // mean it is rebooting rather than that the update failed.
                if (last?.isDone == true || misses >= 3) {
                    finishSuccessfully()
                    return
                }
                return@repeat
            }
            misses = 0
            last = status
            val progress = if (status.percent in 1..100) status.percent / 100f else 0f
            _state.update {
                it.copy(
                    phase = FirmwarePhase.Flashing,
                    progress = progress,
                    progressLabel = stageLabel(status),
                )
            }
            if (status.isFailed) {
                _state.update {
                    it.copy(
                        phase = FirmwarePhase.Failed,
                        error = status.message.ifBlank { "设备写入固件失败" },
                        progressLabel = "",
                    )
                }
                return
            }
            if (status.isDone) {
                finishSuccessfully()
                return
            }
        }
        _state.update {
            it.copy(
                phase = FirmwarePhase.Failed,
                error = "等待设备完成升级超时。设备可能仍在写入，请稍后在设备上确认版本。",
                progressLabel = "",
            )
        }
    }

    private fun finishSuccessfully() {
        _state.update {
            it.copy(
                phase = FirmwarePhase.Done,
                progress = 1f,
                progressLabel = "升级完成",
                error = null,
            )
        }
    }

    private fun stageLabel(status: FirmwareOtaStatus): String = when {
        status.stage.equals("Receiving", true) -> "设备正在接收固件 ${status.percent}%"
        status.stage.equals("Ready", true) -> "已接收，等待写入"
        status.stage.equals("Verifying", true) -> "设备正在校验镜像"
        status.stage.equals("Writing", true) -> "设备正在写入固件 ${status.percent}%"
        status.isDone -> "写入完成，设备准备重启"
        else -> status.stage
    }

    private fun fraction(value: Long, total: Long): Float =
        if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)

    private fun percent(value: Long, total: Long): Int =
        if (total <= 0) 0 else ((value * 100) / total).toInt().coerceIn(0, 100)

    private fun describe(t: Throwable): String = when (t) {
        is FirmwareException -> t.message ?: "固件源返回异常"
        is ApiException -> t.message ?: "设备返回 ${t.statusCode}"
        is UnknownHostException -> "域名解析失败，请检查网络或代理"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is IOException -> "网络不可达：${t.message ?: "请检查网络"}"
        else -> t.message ?: t::class.java.simpleName
    }

    companion object {
        private const val FLASH_INTERVAL_MS = 1_500L

        /** 1.5 s × 120 = 3 minutes, roughly what a 5.7 MB image needs. */
        private const val FLASH_POLLS = 120

        fun factory(api: DeviceApi, currentVersion: String) = viewModelFactory {
            initializer { FirmwareViewModel(OnePageApp.instance, api, currentVersion) }
        }
    }
}
