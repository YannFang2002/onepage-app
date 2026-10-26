package com.onepage.companion.core.update

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * Parsing / version-comparison contract for the update feed. The payloads are
 * copied from real GitHub and Gitee "latest release" responses.
 */
class ReleaseParserTest {

    private lateinit var previousZone: TimeZone

    @Before
    fun pinTimeZone() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousZone)
    }

    // ------------------------------------------------------------- versioning

    @Test
    fun `labels drop the tag decoration`() {
        assertEquals("1.0.2", AppVersion.label("v1.0.2"))
        assertEquals("1.0.3", AppVersion.label("release-1.0.3"))
        assertEquals("1.0.4", AppVersion.label("1.0.4-beta"))
        assertEquals("nonsense", AppVersion.label("nonsense"))
    }

    @Test
    fun `newer patch wins`() {
        assertTrue(AppVersion.isNewer("1.0.2", "1.0.1"))
        assertFalse(AppVersion.isNewer("1.0.1", "1.0.2"))
        assertFalse(AppVersion.isNewer("1.0.2", "1.0.2"))
    }

    @Test
    fun `segments compare numerically not lexically`() {
        assertTrue(AppVersion.isNewer("1.0.10", "1.0.9"))
        assertTrue(AppVersion.isNewer("2", "1.9.9"))
        assertTrue(AppVersion.isNewer("1.1", "1.0.9"))
        assertEquals(0, AppVersion.compare("v1.0", "1.0.0"))
    }

    @Test
    fun `tags without numbers never look like an update`() {
        assertFalse(AppVersion.isNewer("latest", "1.0.2"))
        assertFalse(AppVersion.isNewer("", "1.0.2"))
        assertTrue(AppVersion.segments("latest").isEmpty())
    }

    // ---------------------------------------------------------------- parsing

    @Test
    fun `github latest release is flattened and the apk is picked`() {
        val release = ReleaseParser.parse(GITHUB_LATEST)

        assertEquals("v1.0.2", release.tag)
        assertEquals("1.0.2", release.version)
        assertEquals("v1.0.2 — 增加检查更新", release.title)
        assertTrue(release.notes.contains("检查更新"))
        assertEquals("https://github.com/YannFang2002/onepage-app/releases/tag/v1.0.2", release.pageUrl)
        assertEquals("2026-10-26", release.publishedDate)
        assertEquals("OnePage-1.0.2.apk", release.apk?.name)
        assertEquals(2_320_123L, release.apk?.size)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", release.apk?.sha256)
        assertEquals(2, release.assets.size)
    }

    @Test
    fun `gitee payloads without a digest fall back to no checksum`() {
        val release = ReleaseParser.parse(GITEE_LATEST)

        assertEquals(null, release.apk?.sha256)
    }

    @Test
    fun `gitee payload without html_url falls back to the mirror page`() {
        val release = ReleaseParser.parse(
            json = GITEE_LATEST,
            fallbackPageUrl = UpdateSource.Gitee.releasesPageUrl,
        )

        assertEquals("v1.0.2", release.tag)
        assertEquals("1.0.2", release.version)
        assertEquals(UpdateSource.Gitee.releasesPageUrl, release.pageUrl)
        assertEquals("OnePage-1.0.2.apk", release.apk?.name)
        assertEquals("2026-10-26", release.publishedDate)
    }

    @Test
    fun `source only release has no apk`() {
        val release = ReleaseParser.parse(
            """{"tag_name":"v1.1.0","html_url":"https://example.test/releases/v1.1.0"}"""
        )

        assertNull(release.apk)
        assertEquals("1.1.0", release.version)
        assertEquals("v1.1.0", release.title)
        assertEquals("", release.notes)
    }

    @Test
    fun `invalid payload is rejected with a readable message`() {
        try {
            ReleaseParser.parse("<html>502 Bad Gateway</html>")
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            assertTrue(expected.message!!.contains("无法解析"))
        }
    }

    @Test
    fun `payload without a tag is rejected`() {
        try {
            ReleaseParser.parse("""{"name":"no tag here"}""")
            fail("expected UpdateException")
        } catch (expected: UpdateException) {
            assertTrue(expected.message!!.contains("版本号"))
        }
    }

    // ------------------------------------------------------------------ dates

    @Test
    fun `release date falls back to the plain date and ignores junk`() {
        assertEquals("2026-10-24", formatReleaseDate("2026-10-24"))
        assertEquals("2026-10-24", formatReleaseDate("2026-10-24T08:00:00Z"))
        assertEquals("", formatReleaseDate(""))
        assertEquals("", formatReleaseDate(null))
        assertEquals("", formatReleaseDate("yesterday"))
    }

    @Test
    fun `long notes are trimmed for cards`() {
        val notes = "a".repeat(2_000)

        assertEquals(1_202, notesPreview(notes).length)
        assertTrue(notesPreview(notes).endsWith("…"))
        assertEquals(5, notesPreview("12345", limit = 5).length)
    }

    private companion object {
        /** Trimmed real response of `/repos/{owner}/{repo}/releases/latest`. */
        const val GITHUB_LATEST = """
{
  "url": "https://api.github.com/repos/YannFang2002/onepage-app/releases/421372632",
  "html_url": "https://github.com/YannFang2002/onepage-app/releases/tag/v1.0.2",
  "id": 421372632,
  "author": {"login": "YannFang2002", "id": 113813533},
  "tag_name": "v1.0.2",
  "target_commitish": "main",
  "name": "v1.0.2 — 增加检查更新",
  "draft": false,
  "prerelease": false,
  "created_at": "2026-10-25T01:00:00Z",
  "published_at": "2026-10-26T01:30:00Z",
  "assets": [
    {
      "name": "OnePage-1.0.2.apk",
      "content_type": "application/vnd.android.package-archive",
      "size": 2320123,
      "digest": "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "browser_download_url": "https://github.com/YannFang2002/onepage-app/releases/download/v1.0.2/OnePage-1.0.2.apk"
    },
    {
      "name": "checksums.txt",
      "content_type": "text/plain",
      "size": 128,
      "browser_download_url": "https://github.com/YannFang2002/onepage-app/releases/download/v1.0.2/checksums.txt"
    }
  ],
  "body": "新增检查更新：启动时向 Gitee 镜像 / GitHub Releases 查询最新版本。\n\n## 变更\n- 版本 1.0.2\n"
}
"""

        /** Gitee's payload shape: no `html_url`, `created_at` only. */
        const val GITEE_LATEST = """
{
  "id": 991,
  "tag_name": "v1.0.2",
  "target_commitish": "main",
  "prerelease": false,
  "name": "v1.0.2 — 增加检查更新",
  "body": "新增检查更新。",
  "created_at": "2026-10-26T01:30:00Z",
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
