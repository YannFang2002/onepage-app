package com.onepage.companion.core.firmware

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Firmware mirror preference and the "download exactly what the release
 * announces" rule; a truncated image would fail the reader's OTA validation.
 */
class FirmwareApiTest {

    private val server = MockWebServer().apply { start() }
    private val tempDir: File = Files.createTempDirectory("firmware-test").toFile()

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun api() = FirmwareApi(
        client = OkHttpClient(),
        sources = listOf(FirmwareSource.Gitee, FirmwareSource.GitHub),
        urls = mapOf(
            FirmwareSource.Gitee to server.url("/gitee/releases/latest").toString(),
            FirmwareSource.GitHub to server.url("/github/releases/latest").toString(),
        ),
    )

    private fun enqueue(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `gitee is asked first`() = runBlocking {
        enqueue(200, RELEASE_JSON)

        val latest = api().latest()

        assertEquals(FirmwareSource.Gitee, latest.source)
        assertEquals("v0.2.2", latest.release.tag)
        assertEquals("/gitee/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `github answers when gitee is down`() = runBlocking {
        enqueue(503, "down")
        enqueue(200, RELEASE_JSON)

        val latest = api().latest()

        assertEquals(FirmwareSource.GitHub, latest.source)
        assertEquals("/gitee/releases/latest", server.takeRequest().path)
        assertEquals("/github/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `both mirrors down reports both attempts`() = runBlocking {
        enqueue(500, "boom")
        enqueue(404, """{"message":"Not Found"}""")

        try {
            api().latest()
            fail("expected FirmwareException")
        } catch (expected: FirmwareException) {
            val message = expected.message!!
            assertTrue(message.contains("Gitee："))
            assertTrue(message.contains("GitHub："))
            assertTrue(message.contains("还没有发布固件"))
        }
    }

    @Test
    fun `download streams the image and reports progress`() = runBlocking {
        val bytes = ByteArray(300 * 1024) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", bytes.size.toString())
                .setBody(Buffer().write(bytes))
        )
        val asset = FirmwareAsset(
            name = "firmware.bin",
            url = server.url("/firmware.bin").toString(),
            size = bytes.size.toLong(),
        )
        val dest = File(tempDir, "firmware.bin")
        var lastWritten = 0L
        var lastTotal = 0L

        api().download(asset, dest) { written, total ->
            lastWritten = written
            lastTotal = total
        }

        assertEquals(bytes.size.toLong(), dest.length())
        assertEquals(bytes.size.toLong(), lastWritten)
        assertEquals(bytes.size.toLong(), lastTotal)
        assertEquals("/firmware.bin", server.takeRequest().path)
    }

    @Test
    fun `connection dropped mid image is rejected and the file removed`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(ByteArray(200 * 1024)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val asset = FirmwareAsset("firmware.bin", server.url("/firmware.bin").toString(), 200L * 1024)
        val dest = File(tempDir, "broken.bin")

        try {
            api().download(asset, dest)
            fail("expected FirmwareException")
        } catch (expected: FirmwareException) {
            assertTrue(expected.message!!.contains("中断"))
            assertTrue(!dest.exists())
        }
    }

    @Test
    fun `chunked body shorter than the release metadata is rejected`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setChunkedBody("only a few bytes", 8)
        )
        // Content-Length is absent for chunked responses, so the release size is
        // the only yardstick left — a short image must not reach the device.
        val asset = FirmwareAsset("firmware.bin", server.url("/firmware.bin").toString(), 1024)
        val dest = File(tempDir, "short.bin")

        try {
            api().download(asset, dest)
            fail("expected FirmwareException")
        } catch (expected: FirmwareException) {
            assertTrue(expected.message!!.contains("不完整"))
            assertTrue(!dest.exists())
        }
    }

    private companion object {
        const val RELEASE_JSON = """
{
  "tag_name": "v0.2.2",
  "published_at": "2026-10-25T05:43:22Z",
  "assets": [
    {
      "name": "firmware.bin",
      "size": 5724192,
      "browser_download_url": "https://example.test/firmware.bin"
    }
  ]
}
"""
    }
}
