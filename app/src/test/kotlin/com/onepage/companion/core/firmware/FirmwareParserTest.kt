package com.onepage.companion.core.firmware

import com.onepage.companion.core.update.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.TimeZone

/**
 * Firmware feed contract: the release must expose an app-only `firmware.bin`
 * (the 0x0 web-flasher image is rejected by `/api/ota`), and the version compare
 * has to work with the `onepage-hub-` prefix the device reports.
 */
class FirmwareParserTest {

    @Test
    fun `hub firmware is told apart from the reader build`() {
        assertTrue(isHubFirmware("onepage-hub-0.2.2"))
        assertTrue(isHubFirmware("  ONEPAGE-HUB-1.0.0 "))
        assertFalse(isHubFirmware("onepage-0.2.2"))
        assertFalse(isHubFirmware("crossmux-1.0.0"))
        assertFalse(isHubFirmware(null))
    }

    @Test
    fun `release tag compares against the device version string`() {
        assertTrue(AppVersion.isNewer("v0.2.2", "onepage-hub-0.2.1"))
        assertTrue(AppVersion.isNewer("0.2.10", "onepage-hub-0.2.9"))
        assertFalse(AppVersion.isNewer("v0.2.2", "onepage-hub-0.2.2"))
        assertFalse(AppVersion.isNewer("v0.2.1", "onepage-hub-0.2.2"))
    }

    @Test
    fun `ota image is the asset named firmware_bin`() {
        val release = FirmwareParser.parse(RELEASE_JSON)

        assertEquals("v0.2.2", release.tag)
        assertEquals("0.2.2", release.version)
        assertEquals("firmware.bin", release.firmware?.name)
        assertEquals(5_724_192L, release.firmware?.size)
        assertEquals(2, release.assets.size)
        assertEquals("https://github.com/YannFang2002/onepage-hub/releases/tag/v0.2.2", release.pageUrl)
    }

    @Test
    fun `web flasher image alone is not an ota payload`() {
        val release = FirmwareParser.parse(
            """{"tag_name":"v0.2.2","assets":[{"name":"onepage-firmware-v0.2.2.bin","size":10,"browser_download_url":"https://x/y.bin"}]}"""
        )

        assertNull(release.firmware)
    }

    @Test
    fun `missing page url falls back to the mirror`() {
        val release = FirmwareParser.parse(
            json = """{"tag_name":"v0.2.2"}""",
            fallbackPageUrl = FirmwareSource.Gitee.releasesPageUrl,
        )

        assertEquals(FirmwareSource.Gitee.releasesPageUrl, release.pageUrl)
        assertNull(release.firmware)
    }

    @Test
    fun `payload without a tag is rejected`() {
        try {
            FirmwareParser.parse("""{"name":"no tag"}""")
            fail("expected FirmwareException")
        } catch (expected: FirmwareException) {
            assertTrue(expected.message!!.contains("版本号"))
        }
    }

    @Test
    fun `ota status is parsed into stage percent and message`() {
        val status = FirmwareParser.parseOtaStatus(
            """{"stage":"Writing","percent":42,"received":2400000,"message":"写入固件","lastResult":null}"""
        )

        assertEquals("Writing", status.stage)
        assertEquals(42, status.percent)
        assertEquals(2_400_000L, status.received)
        assertEquals("写入固件", status.message)
        assertFalse(status.isDone)
        assertFalse(status.isFailed)
        assertEquals(null, status.lastResultOk)
        assertFalse(status.appliedAfterReboot)
    }

    @Test
    fun `post reboot result proves the update applied`() {
        // The firmware persists this file right before restarting, so it is the
        // only trustworthy "it really booted" signal after the reboot.
        val status = FirmwareParser.parseOtaStatus(
            """{"stage":"Idle","percent":0,"received":0,"message":"","lastResult":{"ok":true,"version":"onepage-hub-0.2.2","slot":"app1"}}"""
        )

        assertTrue(status.appliedAfterReboot)
        assertEquals("onepage-hub-0.2.2", status.lastResultVersion)
        assertEquals("app1", status.lastResultSlot)
    }

    @Test
    fun `done and failed stages are recognised`() {
        val done = FirmwareParser.parseOtaStatus("""{"stage":"Done","percent":100,"message":"升级完成"}""")
        val failed = FirmwareParser.parseOtaStatus("""{"stage":"Failed","message":"BAD_SIZE"}""")

        assertTrue(done.isDone)
        assertTrue(done.isFinished)
        assertTrue(failed.isFailed)
        assertTrue(failed.isFinished)
        assertEquals(0, failed.percent)
    }

    @Test
    fun `published date tolerates the two payload shapes`() {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val github = FirmwareParser.parse(RELEASE_JSON)
            val gitee = FirmwareParser.parse(RELEASE_JSON.replace("\"published_at\"", "\"created_at\""))

            assertEquals("2026-10-25", github.publishedDate)
            assertEquals("2026-10-25", gitee.publishedDate)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private companion object {
        const val RELEASE_JSON = """
{
  "tag_name": "v0.2.2",
  "name": "OnePage Hub v0.2.2",
  "html_url": "https://github.com/YannFang2002/onepage-hub/releases/tag/v0.2.2",
  "published_at": "2026-10-25T05:43:22Z",
  "body": "## 变更\n- 修 OTA 回滚\n",
  "assets": [
    {
      "name": "firmware.bin",
      "size": 5724192,
      "browser_download_url": "https://github.com/YannFang2002/onepage-hub/releases/download/v0.2.2/firmware.bin"
    },
    {
      "name": "onepage-firmware-v0.2.2.bin",
      "size": 5789728,
      "browser_download_url": "https://github.com/YannFang2002/onepage-hub/releases/download/v0.2.2/onepage-firmware-v0.2.2.bin"
    }
  ]
}
"""
    }
}
