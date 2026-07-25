package com.sensorstamp.openwifi.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classifier decides what gets written to the database at all, so the
 * capability strings here are copied from real access points.
 */
class WifiSecurityTest {

    @Test
    fun `bare ESS is an open network`() {
        assertEquals(WifiSecurity.OPEN, WifiSecurity.classify("[ESS]"))
    }

    @Test
    fun `open network advertising WPS is still joinable`() {
        val result = WifiSecurity.classify("[WPS][ESS]")
        assertEquals(WifiSecurity.OPEN_WPS, result)
        assertTrue(result.isJoinableWithoutKey)
    }

    @Test
    fun `WPA2 personal is secured`() {
        assertEquals(
            WifiSecurity.SECURED,
            WifiSecurity.classify("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"),
        )
    }

    @Test
    fun `WPA3 SAE is secured`() {
        assertEquals(WifiSecurity.SECURED, WifiSecurity.classify("[RSN-SAE-CCMP][ESS][MFPR][MFPC]"))
    }

    @Test
    fun `WEP is secured even though it is weak`() {
        assertEquals(WifiSecurity.SECURED, WifiSecurity.classify("[WEP][ESS]"))
    }

    @Test
    fun `enterprise 802 1X is secured`() {
        assertEquals(WifiSecurity.SECURED, WifiSecurity.classify("[WPA2-EAP-CCMP][ESS]"))
    }

    @Test
    fun `OWE is recognised before the RSN rule rejects it`() {
        val result = WifiSecurity.classify("[RSN-OWE-CCMP][ESS][MFPR][MFPC]")
        assertEquals(WifiSecurity.OWE, result)
        assertTrue(result.isJoinableWithoutKey)
    }

    @Test
    fun `OWE transition mode counts as password-free`() {
        assertEquals(WifiSecurity.OWE, WifiSecurity.classify("[RSN-OWE_TRANSITION-CCMP][ESS]"))
    }

    @Test
    fun `secured networks are not joinable without a key`() {
        assertFalse(WifiSecurity.SECURED.isJoinableWithoutKey)
    }

    @Test
    fun `empty and null capabilities fall back to open`() {
        assertEquals(WifiSecurity.OPEN, WifiSecurity.classify(""))
        assertEquals(WifiSecurity.OPEN, WifiSecurity.classify(null))
    }

    @Test
    fun `classification ignores case`() {
        assertEquals(WifiSecurity.SECURED, WifiSecurity.classify("[wpa2-psk-ccmp][ess]"))
    }
}
