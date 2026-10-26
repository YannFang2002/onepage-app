package com.onepage.companion.core.firmware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Reads the newest hub-firmware release and downloads its `firmware.bin`.
 *
 * Same mirror policy as the app updater: Gitee first, GitHub second, no token.
 * Unlike the app updater the payload is several megabytes, so the download
 * streams straight to a cache file instead of being held in memory.
 */
class FirmwareApi(
    private val client: OkHttpClient,
    private val sources: List<FirmwareSource> = FirmwareSource.byPreference,
    /** Overrides used by tests; production hits [FirmwareSource.latestUrl]. */
    private val urls: Map<FirmwareSource, String> = emptyMap(),
) {

    suspend fun latest(): FirmwareLatest = withContext(Dispatchers.IO) {
        val failures = ArrayList<String>(sources.size)
        for (source in sources) {
            try {
                return@withContext FirmwareLatest(
                    release = fetch(source, urls[source] ?: source.latestUrl),
                    source = source,
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                failures += "${source.label}：${describe(t)}"
            }
        }
        throw FirmwareException(
            if (failures.isEmpty()) "没有配置固件更新源" else failures.joinToString("；")
        )
    }

    /**
     * Streams [asset] into [dest]; returns [dest] when the byte count matches the
     * release metadata (or the server did not announce a length).
     */
    suspend fun download(
        asset: FirmwareAsset,
        dest: File,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(asset.url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val call = client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // several MB over a slow link
            .build()
            .newCall(request)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw FirmwareException("下载固件失败（HTTP ${response.code}）")
            }
            val body = response.body ?: throw FirmwareException("固件响应为空")
            val total = body.contentLength().takeIf { it > 0 } ?: asset.size
            dest.parentFile?.mkdirs()
            var written = 0L
            var lastReport = 0L
            try {
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read
                            if (written - lastReport >= 256 * 1024) {
                                lastReport = written
                                onProgress(written, total)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                dest.delete()
                throw FirmwareException("固件下载中断：${e.message ?: "连接被关闭"}", e)
            }
            onProgress(written, total)
            if (total > 0 && written != total) {
                dest.delete()
                throw FirmwareException("固件下载不完整（$written / $total 字节）")
            }
            dest
        }
    }

    private fun fetch(source: FirmwareSource, url: String): FirmwareRelease {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val call = client.newBuilder()
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
            .newCall(request)
        call.execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw FirmwareException(httpMessage(response.code, body))
            }
            return FirmwareParser.parse(body, fallbackPageUrl = source.releasesPageUrl)
        }
    }

    private fun httpMessage(code: Int, body: String): String = when {
        code == 404 -> "还没有发布固件（HTTP 404）"
        code == 401 || code == 403 || code == 429 -> "没有读取权限或已限流（HTTP $code）"
        code >= 500 -> "固件源暂时不可用（HTTP $code）"
        body.isBlank() -> "返回 HTTP $code"
        else -> "返回 HTTP $code"
    }

    private fun describe(t: Throwable): String = when (t) {
        is FirmwareException -> t.message ?: "返回了无法识别的数据"
        is UnknownHostException -> "域名解析失败，可能被网络阻断"
        is SocketTimeoutException -> "连接超时"
        is IOException -> "网络错误：${t.message ?: "无响应"}"
        else -> t.message ?: t::class.java.simpleName
    }

    companion object {
        const val USER_AGENT = "OnePage-Companion-Android"
    }
}
