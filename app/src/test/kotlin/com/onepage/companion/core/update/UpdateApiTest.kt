package com.onepage.companion.core.update

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Mirror-preference contract: Gitee is asked first, GitHub is the fallback, and
 * a total failure must still explain both attempts in one readable line.
 */
class UpdateApiTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
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
