package com.onepage.companion.core.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Mirror-preference contract: Gitee is asked first, GitHub is the fallback, and
 * a total failure must still explain both attempts in one readable line.
 */
class UpdateApiTest {

    private lateinit var server: MockWebServer
    private val tempDir: File = Files.createTempDirectory("update-api-test").toFile()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun api() = UpdateApi(
        client = OkHttpClient(),
        sources = listOf(UpdateSource.Gitee, UpdateSource.GitHub),
        urls = mapOf(
            UpdateSource.Gitee to server.url("/gitee/releases/latest").toString(),
            UpdateSource.GitHub to server.url("/github/releases/latest").toString(),
        ),
    )

    private fun enqueue(code: Int, body: String = RELEASE_JSON) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `gitee is preferred when it answers`() = runBlocking {
        enqueue(200)

        val result = api().latest()

        assertEquals(UpdateSource.Gitee, result.source)
        assertEquals("v1.0.2", result.release.tag)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("/gitee/releases/latest", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals(UpdateApi.USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `github is used when gitee fails`() = runBlocking {
        enqueue(500, "boom")
        enqueue(200)

        val result = api().latest()

        assertEquals(UpdateSource.GitHub, result.source)
        assertEquals("/gitee/releases/latest", server.takeRequest().path)
        assertEquals("/github/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `both mirrors failing reports both attempts`() = runBlocking {
        enqueue(500, "boom")
        enqueue(404, """{"message":"Not Found"}""")

        try {
            api().latest()
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            val message = expected.message!!
            assertTrue(message.contains("Gitee："))
            assertTrue(message.contains("GitHub："))
            assertTrue(message.contains("HTTP 500"))
            assertTrue(message.contains("还没有发布 Release"))
        }
    }

    @Test
    fun `primary rate limit is not hidden behind a generic 403`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("x-ratelimit-remaining", "0")
                .setBody("""{"message":"API rate limit exceeded"}""")
        )
        enqueue(200)

        // Gitee rejected us, so the updater must still fall through to GitHub.
        val result = api().latest()

        assertEquals(UpdateSource.GitHub, result.source)
    }

    @Test
    fun `unparseable body is rejected instead of silently producing empty data`() = runBlocking {
        enqueue(200, "<html>not json</html>")
        enqueue(200, "<html>not json</html>")

        try {
            api().latest()
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            assertTrue(expected.message!!.contains("无法解析"))
        }
    }

    @Test
    fun `gitee payload without html_url gets a tag specific release page`() = runBlocking {
        // Real Gitee latest-release payloads leave html_url empty, so the
        // "open in browser" button has to be built from owner/repo/tag.
        enqueue(
            200,
            """{"tag_name":"v1.0.4","name":"v1.0.4","assets":[{"name":"OnePage-1.0.4.apk","browser_download_url":"https://gitee.com/x/y.apk"}]}""",
        )

        val result = api().latest()

        assertEquals(
            "https://gitee.com/Yann_Fang/onepage-app/releases/tag/v1.0.4",
            result.release.pageUrl,
        )
    }

    @Test
    fun `apk download verifies the digest published by the release`() = runBlocking {
        val bytes = ByteArray(300 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        val asset = ReleaseAsset(
            name = "OnePage-1.0.3.apk",
            url = server.url("/OnePage-1.0.3.apk").toString(),
            size = bytes.size.toLong(),
            digest = "sha256:${sha256(bytes)}",
        )
        val dest = File(tempDir, "OnePage-1.0.3.apk")
        var reported = 0L

        api().download(asset, dest) { written, _ -> reported = written }

        assertEquals(bytes.size.toLong(), dest.length())
        assertEquals(bytes.size.toLong(), reported)
        assertEquals("/OnePage-1.0.3.apk", server.takeRequest().path)
    }

    @Test
    fun `apk whose digest does not match is deleted`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("definitely not the apk"))
        val asset = ReleaseAsset(
            name = "OnePage-1.0.3.apk",
            url = server.url("/tampered.apk").toString(),
            size = 25,
            digest = "sha256:${"0".repeat(64)}",
        )
        val dest = File(tempDir, "tampered.apk")

        try {
            api().download(asset, dest)
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            assertTrue(expected.message!!.contains("校验失败"))
            assertTrue(!dest.exists())
        }
    }

    @Test
    fun `short apk download is rejected and removed`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setChunkedBody("short", 4))
        val asset = ReleaseAsset(
            name = "OnePage-1.0.3.apk",
            url = server.url("/short.apk").toString(),
            size = 4096,
        )
        val dest = File(tempDir, "short.apk")

        try {
            api().download(asset, dest)
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            assertTrue(expected.message!!.contains("不完整"))
            assertTrue(!dest.exists())
        }
    }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val RELEASE_JSON = """
{
  "tag_name": "v1.0.2",
  "name": "v1.0.2",
  "body": "新增检查更新。",
  "html_url": "https://gitee.com/YannFang2002/onepage-app/releases/tag/v1.0.2",
  "published_at": "2026-10-26T01:30:00Z",
  "assets": [
    {
      "name": "OnePage-1.0.2.apk",
      "size": 2320123,
      "browser_download_url": "https://gitee.com/YannFang2002/onepage-app/releases/download/v1.0.2/OnePage-1.0.2.apk"
    }
  ]
}
"""
    }
}
