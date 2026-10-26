package com.onepage.companion.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wording of the failed auto-join. Every branch has to name the SSID and point
 * at the manual fallback, because that is the only way the user can recover.
 */
class WifiJoinTest {

    private val ssid = "CrossPoint-Reader"

    @Test
    fun `permission failure tells the user what to allow`() {
        val message = wifiJoinMessage(ssid, WifiJoinFailure.Permission)

        assertTrue(message.contains(ssid))
        assertTrue(message.contains("权限"))
        assertTrue(message.contains("手动"))
    }

    @Test
    fun `disabled wifi points at the radio`() {
        val message = wifiJoinMessage(ssid, WifiJoinFailure.WifiDisabled)

        assertTrue(message.contains(ssid))
        assertTrue(message.contains("Wi-Fi 是关闭"))
    }

    @Test
    fun `declined and timeout stay distinguishable`() {
        val declined = wifiJoinMessage(ssid, WifiJoinFailure.Declined)
        val timeout = wifiJoinMessage(ssid, WifiJoinFailure.Timeout)

        assertTrue(declined.contains("取消"))
        assertTrue(timeout.contains("超时"))
        assertTrue(declined != timeout)
    }

    @Test
    fun `platform detail is kept in the generic branch`() {
        val message = wifiJoinMessage(ssid, WifiJoinFailure.Error, "SecurityException: denied")

        assertTrue(message.contains(ssid))
        assertTrue(message.contains("SecurityException: denied"))
    }

    @Test
    fun `missing failure still produces an actionable sentence`() {
        val message = wifiJoinMessage(ssid, null)

        assertTrue(message.contains(ssid))
        assertTrue(message.contains("手动"))
        assertFalse(message.isBlank())
    }

    @Test
    fun `join result defaults to failed`() {
        val result = WifiJoinResult(failure = WifiJoinFailure.Timeout)

        assertFalse(result.joined)
        assertEquals(null, result.gateway)
        assertEquals(WifiJoinFailure.Timeout, result.failure)
    }
}
