package com.onepage.companion.core.net

import com.onepage.companion.core.model.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Client-side contract tests: asserts the exact requests the firmware's
 * ESP32 WebServer expects (paths, query encoding, form fields, JSON bodies,
 * content types). A green run means the app speaks the device's dialect.
 */
class DeviceApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DeviceApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = DeviceApi(OkHttpClient(), "127.0.0.1", server.port, 81)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `status hits api status`() = runBlocking {
        enqueueJson("""{"version":"onepage-0.1.1","ip":"192.168.1.42","mode":"STA","rssi":-47}""")

        val status = api.status()

        val request = server.takeRequest()
        assertEquals("/api/status", request.path)
        assertEquals("GET", request.method)
        assertEquals("192.168.1.42", status.ip)
    }

    @Test
    fun `settings GET decodes the streamed array`() = runBlocking {
        enqueueJson("""[{"key":"uiTheme","name":"界面主题","category":"显示","type":"enum","value":1,"options":["经典","Lyra"]}]""")

        val items = api.settings()

        assertEquals("/api/settings", server.takeRequest().path)
        assertEquals(1, items.size)
        assertEquals("uiTheme", items.first().key)
    }

    @Test
    fun `updateSettings posts a partial JSON body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("Applied 2 setting(s)"))

        val result = api.updateSettings(mapOf("fontSize" to 3, "fadingFix" to 1))

        val request = server.takeRequest()
        assertEquals("/api/settings", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(3, body.getInt("fontSize"))
        assertEquals(1, body.getInt("fadingFix"))
        assertEquals("Applied 2 setting(s)", result)
    }

    @Test
    fun `listFiles url-encodes the path`() = runBlocking {
        enqueueJson("""[{"name":"三体.epub","size":4812344,"isDirectory":false,"isEpub":true}]""")

        val files = api.listFiles("/Books 科幻")

        val request = server.takeRequest()
        assertTrue(request.path!!.startsWith("/api/files?path="))
        // encoded, not raw
        assertTrue(request.path!!.contains("%2FBooks%20%E7%A7%91%E5%B9%BB") || request.path!!.contains("%2FBooks+%E7%A7%91%E5%B9%BB"))
        assertEquals(1, files.size)
        assertTrue(files.first().isEpub)
    }

    @Test
    fun `mkdir sends path and name as query parameters`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        api.createFolder("/Books", "科幻")

        val request = server.takeRequest()
        // The firmware's ESP32 WebServer::arg() only reads the query string (or a
        // urlencoded body), so these must be in the URL, never in a text/plain body.
        val target = request.path!!
        assertTrue(target.startsWith("/mkdir?"))
        assertTrue(target.contains("path=%2FBooks"))
        assertTrue(target.contains("name=%E7%A7%91%E5%B9%BB"))
        assertEquals("", request.body.readUtf8())
    }

    @Test
    fun `rename move delete send the parameter names the firmware reads`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setBody("OK")) }

        api.rename("/Books/a.epub", "b.epub")
        api.move("/Books/b.epub", "/Read")
        api.delete("/Books/b.epub")

        val rename = server.takeRequest()
        assertTrue(rename.path!!.startsWith("/rename?"))
        assertTrue(rename.path!!.contains("path=%2FBooks%2Fa.epub"))
        assertTrue(rename.path!!.contains("name=b.epub"))

        val move = server.takeRequest()
        assertTrue(move.path!!.startsWith("/move?"))
        assertTrue(move.path!!.contains("path=%2FBooks%2Fb.epub"))
        assertTrue(move.path!!.contains("dest=%2FRead"))

        val delete = server.takeRequest()
        assertTrue(delete.path!!.startsWith("/delete?"))
        assertTrue(delete.path!!.contains("path=%2FBooks%2Fb.epub"))
    }

    @Test
    fun `font upload is multipart with family and file parts`() = runBlocking {
        enqueueJson("""{"ok":true}""")

        api.uploadFont("Noto Serif", "NotoSerif_16.cpfont", ByteArray(16) { 1 })

        val request = server.takeRequest()
        assertEquals("/api/fonts/upload", request.path)
        val contentType = request.getHeader("Content-Type")!!
        assertTrue(contentType.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"family\""))
        assertTrue(body.contains("Noto Serif"))
        assertTrue(body.contains("filename=\"NotoSerif_16.cpfont\""))
    }

    @Test
    fun `wifi save omits password when not provided so the device keeps it`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        api.saveWifi("Home-2.4G", null, index = 0)
        api.saveWifi("Studio", "secret")

        val withoutPassword = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("Home-2.4G", withoutPassword.getString("ssid"))
        assertEquals(0, withoutPassword.getInt("index"))
        assertFalse(withoutPassword.has("password"))

        val withPassword = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("secret", withPassword.getString("password"))
    }

    @Test
    fun `opds delete sends index`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("OK"))

        api.deleteOpds(2)

        val request = server.takeRequest()
        assertEquals("/api/opds/delete", request.path)
        assertEquals(2, JSONObject(request.body.readUtf8()).getInt("index"))
    }

    @Test
    fun `device errors surface as ApiException with its message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Missing path"))

        try {
            api.listFiles("/")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(400, e.statusCode)
            assertEquals("Missing path", e.message)
        }
    }

    @Test
    fun `json error bodies surface the error field`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"Invalid .cpfont file"}""")
        )

        try {
            api.deleteFontFamily("Bad")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals("Invalid .cpfont file", e.message)
        }
    }

    @Test
    fun `ota status is read from the device`() = runBlocking {
        enqueueJson("""{"stage":"Writing","percent":37,"received":2000000,"message":"写入固件","lastResult":null}""")

        val status = api.otaStatus()

        assertEquals("/api/ota/status", server.takeRequest().path)
        assertEquals("Writing", status.stage)
        assertEquals(37, status.percent)
        assertEquals(2_000_000L, status.received)
    }

    @Test
    fun `firmware upload posts the firmware part to api ota`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val firmware = File.createTempFile("firmware", ".bin").apply { writeBytes(ByteArray(2048)) }
        var reportedTotal = 0L
        try {
            api.uploadFirmware(firmware) { _, total -> reportedTotal = total }

            val request = server.takeRequest()
            assertEquals("/api/ota", request.path)
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"firmware\""))
            assertTrue(body.contains("filename=\"${firmware.name}\""))
            assertEquals(firmware.length(), reportedTotal)
        } finally {
            firmware.delete()
        }
    }

    @Test
    fun `ota endpoint asking for a password is reported as such`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        try {
            api.uploadFirmware(File.createTempFile("firmware", ".bin").apply { writeBytes(ByteArray(16)) })
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.statusCode)
            assertTrue(e.message!!.contains("OTA 密码"))
        }
    }
}
