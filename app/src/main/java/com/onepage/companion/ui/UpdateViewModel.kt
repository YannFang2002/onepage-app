package com.onepage.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onepage.companion.BuildConfig
import com.onepage.companion.OnePageApp
import com.onepage.companion.core.update.AppRelease
import com.onepage.companion.core.update.AppVersion
import com.onepage.companion.core.update.UpdateApi
import com.onepage.companion.core.update.UpdateException
import com.onepage.companion.core.update.UpdateSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class UpdatePhase { Idle, Checking, UpToDate, Available, Failed }

/** In-app APK download, so installing never needs a browser round-trip. */
data class UpdateDownload(
    val running: Boolean = false,
    val progress: Float = 0f,
    val label: String = "",
    val apk: java.io.File? = null,
    val error: String? = null,
) {
    val ready: Boolean get() = apk != null
}

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val release: AppRelease? = null,
    val source: UpdateSource? = null,
    val error: String? = null,
    val checkedAt: Long = 0L,
    val download: UpdateDownload = UpdateDownload(),
) {
    val latestVersion: String? get() = release?.version

    val hasUpdate: Boolean get() = phase == UpdatePhase.Available

    /** The APK to download, when the newest release ships one. */
    val downloadUrl: String? get() = release?.apk?.url ?: release?.pageUrl

    /** Label of the mirror that answered, for the UI. */
    val sourceLabel: String get() = source?.displayName ?: "—"

    /** One-liner under the 更多 list entry. */
    val summary: String
        get() = when (phase) {
            UpdatePhase.Idle -> "当前 v$currentVersion · 点按检查"
            UpdatePhase.Checking -> "正在查询最新版本…"
            UpdatePhase.Available -> "发现新版本 ${release?.tag ?: latestVersion.orEmpty()}"
            UpdatePhase.UpToDate -> "当前 v$currentVersion · 已是最新"
            UpdatePhase.Failed -> "检查失败 · ${error ?: "未知错误"}"
        }
}

/**
 * Owns the app's own update channel (Gitee mirror first, then GitHub Releases).
 *
 * Deliberately separate from [SessionViewModel]: checking for an APK must work
 * before a reader is connected, and its failures must never surface as device
 * errors.
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val api = UpdateApi((app as OnePageApp).http)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var downloadJob: Job? = null

    /** Silent probe on launch; the shell turns [UpdatePhase.Available] into a prompt. */
    fun autoCheck() {
        if (_state.value.phase != UpdatePhase.Idle) return
        check()
    }

    fun check() {
        if (_state.value.phase == UpdatePhase.Checking) return
        job?.cancel()
        _state.update { it.copy(phase = UpdatePhase.Checking, error = null) }
        job = viewModelScope.launch {
            runCatching { api.latest() }
                .onSuccess { result ->
                    val newer = AppVersion.isNewer(result.release.version, BuildConfig.VERSION_NAME)
                    _state.update {
                        it.copy(
                            phase = if (newer) UpdatePhase.Available else UpdatePhase.UpToDate,
                            release = result.release,
                            source = result.source,
                            error = null,
                            checkedAt = System.currentTimeMillis(),
                            // A different release invalidates a previously downloaded APK.
                            download = if (it.release?.tag == result.release.tag) {
                                it.download
                            } else {
                                UpdateDownload()
                            },
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(
                            phase = UpdatePhase.Failed,
                            error = describe(t),
                            checkedAt = System.currentTimeMillis(),
                        )
                    }
                }
        }
    }

    private fun describe(t: Throwable): String = when (t) {
        is UpdateException -> t.message ?: "更新源返回异常"
        is UnknownHostException -> "域名解析失败，请检查网络或代理"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is IOException -> "网络不可达：${t.message ?: "请检查网络"}"
        else -> t.message ?: t::class.java.simpleName
    }

    /**
     * Downloads the APK into the app cache and leaves it in
     * [UpdateUiState.download]; the UI opens the system installer as soon as the
     * file appears, so no browser is involved.
     */
    fun download() {
        val release = _state.value.release ?: return
        if (_state.value.download.running) return
        val asset = release.apk
        if (asset == null) {
            _state.update {
                it.copy(download = UpdateDownload(error = "这个发行版没有 APK 附件，请到 Release 页面手动下载"))
            }
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val dir = java.io.File(getApplication<Application>().cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { stale -> runCatching { stale.delete() } }
            val dest = java.io.File(dir, "OnePage-${release.version}.apk")
            _state.update { it.copy(download = UpdateDownload(running = true, label = "正在下载安装包")) }
            runCatching {
                api.download(asset, dest) { written, total ->
                    _state.update { s ->
                        s.copy(
                            download = s.download.copy(
                                progress = fraction(written, total),
                                label = "正在下载安装包 ${percent(written, total)}%",
                            )
                        )
                    }
                }
            }.onSuccess { file ->
                _state.update {
                    it.copy(download = UpdateDownload(progress = 1f, label = "下载完成，正在打开安装器", apk = file))
                }
            }.onFailure { t ->
                _state.update { it.copy(download = UpdateDownload(error = describe(t))) }
            }
        }
    }

    /** Clears only the error so the "下载并安装" button can be retried. */
    fun dismissDownloadError() {
        _state.update {
            if (it.download.apk == null) it.copy(download = UpdateDownload()) else it
        }
    }

    private fun fraction(value: Long, total: Long): Float =
        if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f)

    private fun percent(value: Long, total: Long): Int =
        if (total <= 0) 0 else ((value * 100) / total).toInt().coerceIn(0, 100)
}
