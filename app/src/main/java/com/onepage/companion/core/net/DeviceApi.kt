package com.onepage.companion.core.net

import com.onepage.companion.core.firmware.FirmwareOtaStatus
import com.onepage.companion.core.firmware.FirmwareParser
import com.onepage.companion.core.model.ApiException
import com.onepage.companion.core.model.DeviceStatus
import com.onepage.companion.core.model.FileEntry
import com.onepage.companion.core.model.FontFamily
import com.onepage.companion.core.model.FontFile
import com.onepage.companion.core.model.FontLibrary
import com.onepage.companion.core.model.OpdsServer
import com.onepage.companion.core.model.SettingItem
import com.onepage.companion.core.model.SettingType
import com.onepage.companion.core.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Thin, typed wrapper over the firmware's HTTP API.
 * Every method maps 1:1 to a route in `CrossPointWebServer.cpp`; nothing here
 * invents behaviour the device does not have.
 */
class DeviceApi(
    private val client: OkHttpClient,
    val host: String,
    val port: Int = 80,
    val wsPort: Int = 81,
) {
    val baseUrl: String = "http://$host:$port"
    val wsUrl: String = "ws://$host:$wsPort"

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    // ---------------------------------------------------------------- status

    suspend fun status(): DeviceStatus = withContext(Dispatchers.IO) {
        Parsers.parseStatus(getRaw("/api/status", timeoutMs = 5_000))
    }

    // -------------------------------------------------------------- settings

    suspend fun settings(): List<SettingItem> = withContext(Dispatchers.IO) {
        Parsers.parseSettings(getRaw("/api/settings", timeoutMs = 10_000))
    }

    /**
     * Partial update: only the provided keys are touched, the device persists
     * with `SETTINGS.saveToFile()` before answering.
     */
    suspend fun updateSettings(values: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { values.forEach { (k, v) -> put(k, v) } }.toString()
        postRaw("/api/settings", body, jsonMedia, timeoutMs = 10_000)
    }

    // ----------------------------------------------------------------- files

    suspend fun listFiles(path: String): List<FileEntry> = withContext(Dispatchers.IO) {
        Parsers.parseFiles(getRaw("/api/files?path=${encode(path)}", timeoutMs = 15_000))
    }

    suspend fun createFolder(path: String, name: String) = withContext(Dispatchers.IO) {
        postQuery("/mkdir", mapOf("path" to path, "name" to name), timeoutMs = 15_000)
    }

    suspend fun rename(path: String, newName: String) = withContext(Dispatchers.IO) {
        postQuery("/rename", mapOf("path" to path, "name" to newName), timeoutMs = 15_000)
    }

    suspend fun move(path: String, dest: String) = withContext(Dispatchers.IO) {
        postQuery("/move", mapOf("path" to path, "dest" to dest), timeoutMs = 20_000)
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        postQuery("/delete", mapOf("path" to path), timeoutMs = 20_000)
    }

    /** Streams to [dest]; returns the file when complete. No Range/resume on device. */
    suspend fun download(path: String, dest: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): File =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/download?path=${encode(path)}"
            val call = client.newBuilder().readTimeout(0, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).get().build())
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException(resp.code, resp.body?.string().orEmpty().ifBlank { "下载失败" })
                val body = resp.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            written += read
                            if (written - lastReport >= 256 * 1024) {
                                lastReport = written
                                onProgress(written, total)
                            }
                        }
                        onProgress(written, total)
                    }
                }
            }
            dest
        }

    // ----------------------------------------------------------------- fonts

    suspend fun fonts(): FontLibrary = withContext(Dispatchers.IO) {
        Parsers.parseFonts(getRaw("/api/fonts", timeoutMs = 10_000))
    }

    /** Uploads one `.cpfont` file under a family name. */
    suspend fun uploadFont(family: String, filename: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("family", family)
                .addFormDataPart("file", filename, bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            val req = Request.Builder().url("$baseUrl/api/fonts/upload").post(body).build()
            execute(req, timeoutMs = 60_000)
        }

    suspend fun deleteFontFamily(family: String) = withContext(Dispatchers.IO) {
        postRaw("/api/fonts/delete", JSONObject().put("family", family).toString(), jsonMedia)
    }

    // ------------------------------------------------------------------ opds

    suspend fun opdsServers(): List<OpdsServer> = withContext(Dispatchers.IO) {
        Parsers.parseOpds(getRaw("/api/opds", timeoutMs = 10_000))
    }

    /** [password] null keeps the stored one (device preserves it when the key is absent). */
    suspend fun saveOpds(name: String, url: String, username: String, password: String?) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("name", name)
                put("url", url)
                put("username", username)
                if (password != null) put("password", password)
            }
            postRaw("/api/opds", body.toString(), jsonMedia)
        }

    suspend fun deleteOpds(index: Int) = withContext(Dispatchers.IO) {
        postRaw("/api/opds/delete", JSONObject().put("index", index).toString(), jsonMedia)
    }

    // ------------------------------------------------------------- wifi creds

    suspend fun wifiNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        Parsers.parseWifi(getRaw("/api/wifi", timeoutMs = 10_000))
    }

    suspend fun saveWifi(ssid: String, password: String?, index: Int? = null) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("ssid", ssid)
                if (password != null) put("password", password)
                if (index != null) put("index", index)
            }
            postRaw("/api/wifi", body.toString(), jsonMedia)
        }

    suspend fun deleteWifi(index: Int) = withContext(Dispatchers.IO) {
        postRaw("/api/wifi/delete", JSONObject().put("index", index).toString(), jsonMedia)
    }

    // ------------------------------------------------------------------- ota

    /** `GET /api/ota/status` — stage/percent of the reader's firmware update. */
    suspend fun otaStatus(): FirmwareOtaStatus = withContext(Dispatchers.IO) {
        FirmwareParser.parseOtaStatus(
            getRaw("/api/ota/status", timeoutMs = 8_000),
        )
    }

    /**
     * `POST /api/ota` (multipart part named `firmware`).
     *
     * The reader writes the image to the SD card, validates it, flashes the next
     * OTA slot and reboots a few seconds later, so callers should keep polling
     * [otaStatus] instead of trusting this response.
     */
    suspend fun uploadFirmware(file: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): String =
        withContext(Dispatchers.IO) {
            val total = file.length()
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()

                override fun contentLength() = total

                override fun writeTo(sink: BufferedSink) {
                    file.source().use { source ->
                        val buffer = Buffer()
                        var sent = 0L
                        var lastReport = 0L
                        while (true) {
                            val read = source.read(buffer, 64 * 1024)
                            if (read == -1L) break
                            sink.write(buffer, read)
                            sent += read
                            if (sent - lastReport >= 256 * 1024) {
                                lastReport = sent
                                onProgress(sent, total)
                            }
                        }
                        onProgress(sent, total)
                    }
                }
            }
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("firmware", file.name, body)
                .build()
            val request = Request.Builder().url("$baseUrl/api/ota").post(multipart).build()
            val call = client.newBuilder()
                .callTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                .writeTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request)
            call.execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.code == 401) {
                    throw ApiException(401, "设备要求 OTA 密码，暂不支持在 App 内配置")
                }
                if (!resp.isSuccessful) throw ApiException(resp.code, friendly(text, resp.code))
                return@use text
            }
        }

    // --------------------------------------------------------------- plumbing

    private fun execute(req: Request, timeoutMs: Long = 15_000): String {
        val call = client.newBuilder()
            .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
            .newCall(req)
        call.execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, friendly(text, resp.code))
            return text
        }
    }

    private fun friendly(body: String, code: Int): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            runCatching { JSONObject(trimmed).optString("error") }.getOrNull()
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return trimmed.ifBlank { "HTTP $code" }.take(300)
    }

    private fun getRaw(path: String, timeoutMs: Long): String =
        execute(Request.Builder().url("$baseUrl$path").get().build(), timeoutMs)

    private fun postRaw(path: String, body: String, media: okhttp3.MediaType, timeoutMs: Long = 15_000): String =
        execute(Request.Builder().url("$baseUrl$path").post(body.toRequestBody(media)).build(), timeoutMs)

    /**
     * Sends the fields as URL query parameters, not as a form body.
     *
     * The firmware uses ESP32 `WebServer::hasArg()/arg()`, which only sees the
     * query string plus `application/x-www-form-urlencoded` bodies. Posting the
     * fields as a `text/plain` body makes every file operation answer
     * `400 Missing \`path\` or \`paths\` argument`. The query string works for
     * every content type and is what `/api/files` and `/download` already use.
     */
    private fun postQuery(path: String, fields: Map<String, String>, timeoutMs: Long = 15_000): String {
        val query = fields.entries.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        return execute(
            Request.Builder().url("$baseUrl$path?$query").post(ByteArray(0).toRequestBody(null)).build(),
            timeoutMs,
        )
    }

    companion object {
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}

/** Small helper so callers can build URLs safely. */
fun httpUrl(base: String, path: String) = "$base$path".toHttpUrl()
