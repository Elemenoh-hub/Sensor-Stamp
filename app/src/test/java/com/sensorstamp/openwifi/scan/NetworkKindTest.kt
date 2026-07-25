package com.sensorstamp.openwifi.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkKindTest {

    @Test
    fun `printers and streaming sticks are devices, not venues`() {
        // The interesting case: the name contains "guest", which would otherwise
        // match the venue rules. Device patterns are checked first for exactly this.
        assertEquals(NetworkKind.DEVICE, NetworkKind.classify("HP-Print-Guest"))
        assertEquals(NetworkKind.DEVICE, NetworkKind.classify("DIRECT-A4-HP OfficeJet"))
        assertEquals(NetworkKind.DEVICE, NetworkKind.classify("Chromecast1234"))
    }

    @Test
    fun `phone hotspots are recognised`() {
        assertEquals(NetworkKind.PERSONAL_HOTSPOT, NetworkKind.classify("Dave's iPhone"))
        assertEquals(NetworkKind.PERSONAL_HOTSPOT, NetworkKind.classify("AndroidAP3F2C"))
        assertEquals(NetworkKind.PERSONAL_HOTSPOT, NetworkKind.classify("Galaxy S23 5390"))
    }

    @Test
    fun `on-board transit wifi is recognised`() {
        assertEquals(NetworkKind.TRANSIT, NetworkKind.classify("AmtrakConnect"))
        assertEquals(NetworkKind.TRANSIT, NetworkKind.classify("gogoinflight"))
        assertEquals(NetworkKind.TRANSIT, NetworkKind.classify("TfL WiFi"))
    }

    @Test
    fun `carrier hotspots are recognised`() {
        assertEquals(NetworkKind.CARRIER, NetworkKind.classify("xfinitywifi"))
        assertEquals(NetworkKind.CARRIER, NetworkKind.classify("attwifi"))
        assertEquals(NetworkKind.CARRIER, NetworkKind.classify("BTWiFi-with-FON"))
    }

    @Test
    fun `municipal and institutional wifi is recognised`() {
        assertEquals(NetworkKind.MUNICIPAL, NetworkKind.classify("LinkNYC Free Wi-Fi"))
        assertEquals(NetworkKind.MUNICIPAL, NetworkKind.classify("Seattle Public Library"))
        assertEquals(NetworkKind.MUNICIPAL, NetworkKind.classify("Heathrow Airport WiFi"))
    }

    @Test
    fun `venue guest wifi is recognised`() {
        assertEquals(NetworkKind.VENUE, NetworkKind.classify("Starbucks WiFi"))
        assertEquals(NetworkKind.VENUE, NetworkKind.classify("Hotel_Guest"))
        assertEquals(NetworkKind.VENUE, NetworkKind.classify("Free WiFi"))
    }

    @Test
    fun `default router names read as residential`() {
        assertEquals(NetworkKind.RESIDENTIAL, NetworkKind.classify("NETGEAR47"))
        assertEquals(NetworkKind.RESIDENTIAL, NetworkKind.classify("TP-Link_A3F2"))
        assertEquals(NetworkKind.RESIDENTIAL, NetworkKind.classify("FRITZ!Box 7530"))
    }

    @Test
    fun `an unrecognisable name falls back to the vendor`() {
        assertEquals(NetworkKind.RESIDENTIAL, NetworkKind.classify("kjhsdf", vendor = "Netgear"))
        assertEquals(NetworkKind.PERSONAL_HOTSPOT, NetworkKind.classify("kjhsdf", vendor = "Apple"))
        assertEquals(NetworkKind.DEVICE, NetworkKind.classify("kjhsdf", vendor = "Roku"))
    }

    @Test
    fun `an unrecognisable name with no vendor stays unclassified`() {
        assertEquals(NetworkKind.UNKNOWN, NetworkKind.classify("zzqqxx99"))
        assertEquals(NetworkKind.UNKNOWN, NetworkKind.classify("zzqqxx99", vendor = "Cisco"))
    }

    @Test
    fun `a blank ssid is unclassified rather than guessed`() {
        assertEquals(NetworkKind.UNKNOWN, NetworkKind.classify(""))
        assertEquals(NetworkKind.UNKNOWN, NetworkKind.classify("   "))
    }

    @Test
    fun `classification is case insensitive`() {
        assertEquals(NetworkKind.CARRIER, NetworkKind.classify("XFINITYWIFI"))
        assertEquals(NetworkKind.VENUE, NetworkKind.classify("STARBUCKS WIFI"))
    }

    @Test
    fun `kinds that cannot give you internet are marked unusable`() {
        assertFalse(NetworkKind.DEVICE.usefulForBrowsing)
        assertFalse(NetworkKind.PERSONAL_HOTSPOT.usefulForBrowsing)
        assertTrue(NetworkKind.MUNICIPAL.usefulForBrowsing)
        assertTrue(NetworkKind.VENUE.usefulForBrowsing)
    }
}
