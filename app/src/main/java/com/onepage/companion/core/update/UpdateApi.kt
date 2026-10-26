package com.onepage.companion.core.update

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
 * Reads the newest public release of the project from the configured mirrors,
 * in order (Gitee first, then GitHub).
 *
 * No token is used on purpose: the app is distributed through release pages, so
 * an unauthenticated "latest release" call is plenty for a launch probe plus a
 * manual check, and it keeps the APK free of embedded secrets.
 */
class UpdateApi(
    private val client: OkHttpClient,
    private val sources: List<UpdateSource> = UpdateSource.byPreference,
    /** Overrides used by tests; production always hits [UpdateSource.latestUrl]. */
    private val urls: Map<UpdateSource, String> = emptyMap(),
) {

    suspend fun latest(): UpdateResult = withContext(Dispatchers.IO) {
        val failures = ArrayList<String>(sources.size)
        for (source in sources) {
            try {
                val release = fetch(source, urls[source] ?: source.latestUrl)
                return@withContext UpdateResult(release = release, source = source)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                failures += "${source.label}：${describe(t)}"
            }
        }
        throw UpdateException(
            if (failures.isEmpty()) "没有配置更新源" else failures.joinToString("；")
        )
    }

    /**
     * Streams the APK to [dest], checking the byte count and — when the release
     * published one — the SHA-256 digest. A mismatch deletes the file so the
     * package installer never sees a corrupted or tampered APK.
     */
    suspend fun download(
        asset: ReleaseAsset,
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
            .readTimeout(0, TimeUnit.SECONDS) // a few MB over a slow link
            .build()
            .newCall(request)
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw UpdateException("下载安装包失败（HTTP ${response.code}）")
            }
            val body = response.body ?: throw UpdateException("安装包响应为空")
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
                throw UpdateException("下载中断：${e.message ?: "连接被关闭"}", e)
            }
            onProgress(written, total)
            if (total > 0 && written != total) {
                dest.delete()
                throw UpdateException("安装包不完整（$written / $total 字节）")
            }
            asset.sha256?.let { expected ->
                if (!sha256Of(dest).equals(expected, ignoreCase = true)) {
                    dest.delete()
                    throw UpdateException("安装包校验失败（SHA-256 与 Release 不符）")
                }
            }
            dest
        }
    }

    private fun sha256Of(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetch(source: UpdateSource, url: String): AppRelease {        val request = Request.Builder()
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
                throw UpdateException(
                    httpMessage(
                        code = response.code,
                        rateLimitRemaining = response.header("x-ratelimit-remaining"),
                        body = body,
                    )
                )
            }
            return ReleaseParser.parse(body, fallbackPageUrl = source.releasesPageUrl)
        }
    }

    private fun httpMessage(code: Int, rateLimitRemaining: String?, body: String): String = when {
        code == 429 || (rateLimitRemaining == "0" && body.contains("rate limit", ignoreCase = true)) ->
            "接口限流，请稍后再试"
        code == 401 || code == 403 -> "没有读取权限（HTTP $code）"
        code == 404 -> "还没有发布 Release（HTTP 404）"
        code >= 500 -> "服务暂时不可用（HTTP $code）"
        else -> "返回 HTTP $code"
    }

    private fun describe(t: Throwable): String = when (t) {
        is UpdateException -> t.message ?: "返回了无法识别的数据"
        is UnknownHostException -> "域名解析失败，可能被网络阻断"
        is SocketTimeoutException -> "连接超时"
        is IOException -> "网络错误：${t.message ?: "无响应"}"
        else -> t.message ?: t::class.java.simpleName
    }

    companion object {
        const val USER_AGENT = "OnePage-Companion-Android"
    }
}
