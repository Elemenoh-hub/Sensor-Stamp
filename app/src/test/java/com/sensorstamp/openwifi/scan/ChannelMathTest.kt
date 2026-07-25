package com.sensorstamp.openwifi.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMathTest {

    @Test
    fun `maps 2_4 GHz frequencies to channel numbers`() {
        assertEquals(1, WifiSecurity.channelFor(2412))
        assertEquals(6, WifiSecurity.channelFor(2437))
        assertEquals(11, WifiSecurity.channelFor(2462))
    }

    @Test
    fun `channel 14 is the Japan-only special case`() {
        assertEquals(14, WifiSecurity.channelFor(2484))
    }

    @Test
    fun `maps 5 GHz frequencies to channel numbers`() {
        assertEquals(36, WifiSecurity.channelFor(5180))
        assertEquals(149, WifiSecurity.channelFor(5745))
    }

    @Test
    fun `maps 6 GHz frequencies to channel numbers`() {
        assertEquals(1, WifiSecurity.channelFor(5955))
        assertEquals(37, WifiSecurity.channelFor(6135))
    }

    @Test
    fun `unknown frequencies report channel zero rather than nonsense`() {
        assertEquals(0, WifiSecurity.channelFor(0))
        assertEquals(0, WifiSecurity.channelFor(1000))
    }

    @Test
    fun `band labels follow the frequency`() {
        assertEquals("2.4 GHz", WifiSecurity.bandFor(2437))
        assertEquals("5 GHz", WifiSecurity.bandFor(5180))
        assertEquals("6 GHz", WifiSecurity.bandFor(6135))
        assertEquals("60 GHz", WifiSecurity.bandFor(60480))
    }
}
