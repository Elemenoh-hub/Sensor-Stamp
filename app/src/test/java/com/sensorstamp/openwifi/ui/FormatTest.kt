package com.sensorstamp.openwifi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `intervals read the way a person would say them`() {
        assertEquals("5 sec", formatInterval(5))
        assertEquals("45 sec", formatInterval(45))
        assertEquals("1 min", formatInterval(60))
        assertEquals("5 min", formatInterval(300))
        assertEquals("1m 30s", formatInterval(90))
    }

    @Test
    fun `durations roll up into minutes and hours`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("42s", formatDuration(42_000))
        assertEquals("2m 05s", formatDuration(125_000))
        assertEquals("1h 01m", formatDuration(3_660_000))
    }

    @Test
    fun `relative times stay compact`() {
        val now = 1_000_000_000L
        assertEquals("just now", formatRelative(now - 1_000, now))
        assertEquals("30s ago", formatRelative(now - 30_000, now))
        assertEquals("5m ago", formatRelative(now - 300_000, now))
        assertEquals("2h ago", formatRelative(now - 7_200_000, now))
        assertEquals("3d ago", formatRelative(now - 259_200_000, now))
    }

    @Test
    fun `missing timestamps render as a dash rather than 1970`() {
        assertEquals("—", formatRelative(0, 1_000_000_000L))
        assertEquals("—", formatClock(0))
        assertEquals("—", formatDateTime(0))
    }

    @Test
    fun `coordinates carry hemisphere letters instead of minus signs`() {
        assertEquals("51.50735°N  0.12776°W", formatCoordinates(51.50735, -0.12776))
        assertEquals("33.86880°S  151.20930°E", formatCoordinates(-33.8688, 151.2093))
    }

    @Test
    fun `signal bars follow the standard RSSI thresholds`() {
        assertEquals(4, signalBars(-40))
        assertEquals(3, signalBars(-60))
        assertEquals(2, signalBars(-70))
        assertEquals(1, signalBars(-80))
        assertEquals(0, signalBars(-95))
    }
}
