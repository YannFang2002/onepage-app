package com.onepage.companion.core.net

import com.onepage.companion.core.model.SettingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-contract tests. Payloads below are byte-for-byte shapes produced by
 * `CrossPointWebServer.cpp` handlers, so a green run means the app decodes what
 * the firmware actually emits.
 */
class ParsersTest {

    @Test
    fun `status decodes device fields`() {
        val status = Parsers.parseStatus(
            """{"version":"onepage-0.1.1","ip":"192.168.1.42","mode":"STA","rssi":-47,
               "freeHeap":132456,"uptime":7920,"device":"X4"}"""
        )
        assertEquals("onepage-0.1.1", status.version)
        assertEquals("192.168.1.42", status.ip)
        assertEquals("STA", status.mode)
        assertEquals(-47, status.rssi)
        assertEquals(132456L, status.freeHeap)
        assertEquals(7920L, status.uptime)
        assertEquals("X4", status.device)
        assertFalse(status.isAp)
        assertEquals(129, status.freeHeapKb)
        assertEquals("2h 12m", status.uptimeLabel)
    }

    @Test
    fun `status in AP mode reports hotspot`() {
        val status = Parsers.parseStatus("""{"version":"v","ip":"192.168.4.1","mode":"AP","rssi":0}""")
        assertTrue(status.isAp)
        assertEquals(4, status.signalBars)
    }

    @Test
    fun `settings decode every type the firmware can emit`() {
        val json = """
            [
              {"key":"sleepScreen","name":"休眠画面","category":"显示","type":"enum","value":3,
               "options":["深色","浅色","自定义","封面"]},
              {"key":"fadingFix","name":"残影修复","category":"显示","type":"toggle","value":1},
              {"key":"screenMargin","name":"页边距","category":"阅读","type":"value","value":15,
               "min":5,"max":40,"step":5},
              {"key":"koreaderUsername","name":"KOReader 用户名","category":"系统","type":"string",
               "value":"reader"}
            ]
        """.trimIndent()

        val items = Parsers.parseSettings(json)
        assertEquals(4, items.size)

        val sleep = items.first { it.key == "sleepScreen" }
        assertEquals(SettingType.ENUM, sleep.type)
        assertEquals(3, sleep.intValue)
        assertEquals("封面", sleep.valueLabel)
        assertEquals(4, sleep.options.size)

        val toggle = items.first { it.key == "fadingFix" }
        assertEquals(SettingType.TOGGLE, toggle.type)
        assertEquals("开启", toggle.valueLabel)

        val range = items.first { it.key == "screenMargin" }
        assertEquals(SettingType.VALUE, range.type)
        assertEquals(5, range.min)
        assertEquals(40, range.max)
        assertEquals(5, range.step)
        assertEquals("15", range.valueLabel)

        val text = items.first { it.key == "koreaderUsername" }
        assertEquals(SettingType.STRING, text.type)
        assertEquals("reader", text.stringValue)
    }

    @Test
    fun `settings tolerate bool values, string numbers and missing labels`() {
        val json = """
            [
              {"key":"t","type":"toggle","value":true},
              {"key":"e","type":"enum","value":"2","options":["a","b","c"]},
              {"key":"v","type":"value","value":7}
            ]
        """.trimIndent()
        val items = Parsers.parseSettings(json)

        assertEquals(1, items.first { it.key == "t" }.intValue)
        assertEquals(2, items.first { it.key == "e" }.intValue)
        assertEquals("c", items.first { it.key == "e" }.valueLabel)
        // name falls back to the key so a row is never blank
        assertEquals("v", items.first { it.key == "v" }.name)
        // missing min/max/step get safe defaults rather than 0..0
        assertEquals(100, items.first { it.key == "v" }.max)
        assertEquals(1, items.first { it.key == "v" }.step)
    }

    @Test
    fun `files decode epub and directory flags`() {
        val json = """
            [
              {"name":"Books","size":0,"isDirectory":true,"isEpub":false},
              {"name":"三体.epub","size":4812344,"isDirectory":false,"isEpub":true},
              {"name":"cover.bmp","size":120000,"isDirectory":false,"isEpub":false}
            ]
        """.trimIndent()
        val files = Parsers.parseFiles(json)

        assertEquals(3, files.size)
        assertTrue(files[0].isDirectory)
        assertTrue(files[1].isEpub)
        assertEquals(4812344L, files[1].size)
        assertTrue(files[2].isImage)
        assertEquals("bmp", files[2].extension)
    }

    @Test
    fun `fonts decode families, sizes and files`() {
        val json = """
            {"maxFamilies":8,"families":[
              {"name":"Noto Serif SC","sizes":[12,14,16],
               "files":[{"name":"a_12.cpfont","size":100},{"name":"a_14.cpfont","size":200}]}
            ]}
        """.trimIndent()
        val lib = Parsers.parseFonts(json)

        assertEquals(8, lib.maxFamilies)
        assertEquals(1, lib.families.size)
        val family = lib.families.first()
        assertEquals("Noto Serif SC", family.name)
        assertEquals(listOf(12, 14, 16), family.sizes)
        assertEquals(2, family.files.size)
        assertEquals(300L, family.totalBytes)
        assertEquals("12 / 14 / 16", family.sizeLabel)
    }

    @Test
    fun `empty font library is not an error`() {
        val lib = Parsers.parseFonts("""{"maxFamilies":8,"families":[]}""")
        assertTrue(lib.families.isEmpty())
        assertEquals(8, lib.maxFamilies)
    }

    @Test
    fun `opds servers never expose passwords`() {
        val json = """
            [{"index":0,"name":"Calibre","url":"http://192.168.1.10:8080/opds",
              "username":"reader","hasPassword":true}]
        """.trimIndent()
        val servers = Parsers.parseOpds(json)

        assertEquals(1, servers.size)
        assertEquals("Calibre", servers.first().name)
        assertTrue(servers.first().hasPassword)
        assertEquals("reader", servers.first().username)
    }

    @Test
    fun `wifi credentials decode last connected flag`() {
        val json = """
            [{"index":0,"ssid":"Home-2.4G","hasPassword":true,"isLastConnected":true},
             {"index":1,"ssid":"Studio","hasPassword":false,"isLastConnected":false}]
        """.trimIndent()
        val nets = Parsers.parseWifi(json)

        assertEquals(2, nets.size)
        assertEquals("Home-2.4G", nets[0].ssid)
        assertTrue(nets[0].isLastConnected)
        assertFalse(nets[1].hasPassword)
    }

    @Test
    fun `discovery reply is parsed from the firmware format`() {
        val found = Parsers.parseDiscoveryReply("crosspoint (on crosspoint-42);81", "192.168.1.42")

        assertEquals("192.168.1.42", found?.host)
        assertEquals(81, found?.wsPort)
        assertEquals("crosspoint-42", found?.hostname)
    }

    @Test
    fun `discovery reply falls back to port 81 when omitted`() {
        val found = Parsers.parseDiscoveryReply("crosspoint (on bedroom)", "192.168.1.77")
        assertEquals(81, found?.wsPort)
        assertEquals("bedroom", found?.hostname)
    }

    @Test
    fun `foreign udp packets are ignored`() {
        assertNull(Parsers.parseDiscoveryReply("hello", "192.168.1.5"))
        assertNull(Parsers.parseDiscoveryReply("", "192.168.1.5"))
    }

    @Test
    fun `malformed rows are skipped instead of crashing`() {
        val files = Parsers.parseFiles("""[{"name":""},{"name":"ok.txt","size":1}]""")
        assertEquals(1, files.size)
        assertEquals("ok.txt", files.first().name)

        val settings = Parsers.parseSettings("""[{"name":"no key"},{"key":"k","type":"weird","value":1}]""")
        assertEquals(1, settings.size)
        assertEquals(SettingType.UNKNOWN, settings.first().type)
    }
}
