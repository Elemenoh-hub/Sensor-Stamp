package com.sensorstamp.openwifi.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OuiVendorsTest {

    @Test
    fun `every table key is a well-formed six-digit OUI`() {
        // Guards against typos in a hand-maintained table — a key with a space or
        // an extra digit can never match and would silently do nothing.
        OuiVendors.table.keys.forEach { key ->
            assertEquals("key '$key' should be 6 characters", 6, key.length)
            assertTrue(
                "key '$key' should be uppercase hex",
                key.all { it in '0'..'9' || it in 'A'..'F' },
            )
        }
    }

    @Test
    fun `no OUI in the table is globally administered by accident`() {
        // A locally-administered prefix in the table could never be returned by
        // lookup(), so its presence would be a mistake.
        OuiVendors.table.keys.forEach { key ->
            assertTrue(
                "key '$key' is locally administered and can never match",
                !OuiVendors.isLocallyAdministered(key),
            )
        }
    }

    @Test
    fun `looks up a vendor from a colon-separated bssid`() {
        assertEquals("Ubiquiti", OuiVendors.lookup("24:a4:3c:aa:bb:cc"))
        assertEquals("Cisco", OuiVendors.lookup("00:00:0c:11:22:33"))
    }

    @Test
    fun `accepts other bssid separators and casing`() {
        assertEquals("Ubiquiti", OuiVendors.lookup("24-A4-3C-AA-BB-CC"))
        assertEquals("Ubiquiti", OuiVendors.lookup("24a43caabbcc"))
    }

    @Test
    fun `unknown prefixes return null rather than a guess`() {
        assertNull(OuiVendors.lookup("de:ad:be:ef:00:01"))
    }

    @Test
    fun `locally administered addresses return null`() {
        // Virtual BSSIDs for extra SSIDs on one radio set bit 1 of octet 0, which
        // makes the OUI meaningless.
        assertTrue(OuiVendors.isLocallyAdministered("02:00:00:00:00:00"))
        assertTrue(OuiVendors.isLocallyAdministered("26:a4:3c:aa:bb:cc"))
        assertNull(OuiVendors.lookup("26:a4:3c:aa:bb:cc"))
    }

    @Test
    fun `globally administered addresses are not flagged`() {
        assertTrue(!OuiVendors.isLocallyAdministered("24:a4:3c:aa:bb:cc"))
        assertTrue(!OuiVendors.isLocallyAdministered("00:00:0c:11:22:33"))
    }

    @Test
    fun `a too-short bssid returns null instead of throwing`() {
        assertNull(OuiVendors.lookup(""))
        assertNull(OuiVendors.lookup("24:a4"))
    }
}
