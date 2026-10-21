package com.onepage.companion.core.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The payloads below are what the firmware draws on the e-ink screen. */
class QrPayloadParserTest {

    @Test
    fun `firmware hotspot payload without type or password`() {
        val payload = QrPayloadParser.parse("WIFI:S:CrossPoint-Reader;;")

        assertTrue(payload is QrPayloadParser.Payload.Wifi)
        val wifi = payload as QrPayloadParser.Payload.Wifi
        assertEquals("CrossPoint-Reader", wifi.ssid)
        assertNull(wifi.password)
        assertTrue(wifi.isOpen)
        assertEquals("开放网络", wifi.securityLabel)
    }

    @Test
    fun `standard wpa payload keeps the passphrase`() {
        val wifi = QrPayloadParser.parse("WIFI:T:WPA;S:MyBook;P:p@ss:word;H:true;;")
            as QrPayloadParser.Payload.Wifi

        assertEquals("MyBook", wifi.ssid)
        assertEquals("p@ss:word", wifi.password)
        assertTrue(!wifi.isOpen)
        assertEquals("WPA/WPA2", wifi.securityLabel)
    }

    @Test
    fun `escaped characters survive`() {
        val wifi = QrPayloadParser.parse("""WIFI:S:My\;Net;P:a\\b;;""") as QrPayloadParser.Payload.Wifi
        assertEquals("My;Net", wifi.ssid)
        assertEquals("""a\b""", wifi.password)
    }

    @Test
    fun `device url payload yields host and port`() {
        val endpoint = QrPayloadParser.parse("http://192.168.4.1/") as QrPayloadParser.Payload.Endpoint
        assertEquals("192.168.4.1", endpoint.host)
        assertEquals(80, endpoint.port)
    }

    @Test
    fun `explicit port and path are handled`() {
        val endpoint = QrPayloadParser.parse("http://192.168.1.42:8080/index.html")
            as QrPayloadParser.Payload.Endpoint
        assertEquals("192.168.1.42", endpoint.host)
        assertEquals(8080, endpoint.port)
    }

    @Test
    fun `bare address is accepted`() {
        val endpoint = QrPayloadParser.parse("192.168.4.1") as QrPayloadParser.Payload.Endpoint
        assertEquals("192.168.4.1", endpoint.host)
        assertEquals(80, endpoint.port)
    }

    @Test
    fun `hostname payload is accepted for non-C61 builds`() {
        val endpoint = QrPayloadParser.parse("http://crosspoint.local/")
            as QrPayloadParser.Payload.Endpoint
        assertEquals("crosspoint.local", endpoint.host)
    }

    @Test
    fun `junk is rejected instead of guessed`() {
        assertNull(QrPayloadParser.parse(""))
        assertNull(QrPayloadParser.parse("hello world"))
        assertNull(QrPayloadParser.parse("WIFI:S:;;"))
        assertNull(QrPayloadParser.parse("https://"))
    }
}
