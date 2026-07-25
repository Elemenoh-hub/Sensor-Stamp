package com.sensorstamp.openwifi.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioMathTest {

    @Test
    fun `a weaker signal implies a greater distance`() {
        val near = RadioMath.estimateDistanceMeters(-40, 2437)
        val mid = RadioMath.estimateDistanceMeters(-65, 2437)
        val far = RadioMath.estimateDistanceMeters(-85, 2437)

        assertTrue("$near should be under $mid", near < mid)
        assertTrue("$mid should be under $far", mid < far)
    }

    @Test
    fun `5 GHz attenuates faster than 2_4 GHz at the same signal level`() {
        val at2400 = RadioMath.estimateDistanceMeters(-60, 2437)
        val at5000 = RadioMath.estimateDistanceMeters(-60, 5180)

        assertTrue("higher frequency should imply a shorter distance", at5000 < at2400)
    }

    @Test
    fun `nonsense inputs produce zero rather than NaN or infinity`() {
        assertEquals(0f, RadioMath.estimateDistanceMeters(-60, 0), 1e-6f)
        assertEquals(0f, RadioMath.estimateDistanceMeters(0, 2437), 1e-6f)
    }

    @Test
    fun `distance estimates stay inside a sane envelope`() {
        val absurdlyWeak = RadioMath.estimateDistanceMeters(-120, 2437)
        assertTrue("clamped upper bound", absurdlyWeak <= 5_000f)
        assertTrue("clamped lower bound", RadioMath.estimateDistanceMeters(-1, 2437) >= 0.5f)
    }

    @Test
    fun `stronger sightings carry more weight in the position estimate`() {
        assertTrue(RadioMath.positionWeight(-40) > RadioMath.positionWeight(-70))
        assertTrue(RadioMath.positionWeight(-70) > RadioMath.positionWeight(-95))
    }

    @Test
    fun `weights never go to zero or negative for unusable signals`() {
        assertTrue(RadioMath.positionWeight(-100) >= 1.0)
        assertTrue(RadioMath.positionWeight(-127) >= 1.0)
    }

    @Test
    fun `great-circle distance matches a known separation`() {
        // One degree of latitude is about 111 km anywhere on the globe.
        val metres = RadioMath.distanceMeters(51.0, 0.0, 52.0, 0.0)
        assertEquals(111_195.0, metres, 500.0)
    }

    @Test
    fun `distance between identical points is zero`() {
        assertEquals(0.0, RadioMath.distanceMeters(51.5, -0.1, 51.5, -0.1), 1e-6)
    }

    @Test
    fun `a network seen from one spot has no coverage radius`() {
        assertEquals(0f, RadioMath.coverageRadiusMeters(51.5, 51.5, -0.1, -0.1), 1e-6f)
    }

    @Test
    fun `coverage radius grows with the sighting bounds`() {
        val small = RadioMath.coverageRadiusMeters(51.5010, 51.5000, -0.1000, -0.1010)
        val large = RadioMath.coverageRadiusMeters(51.5100, 51.5000, -0.1000, -0.1100)

        assertTrue(small > 0f)
        assertTrue(large > small)
    }

    @Test
    fun `mobile detection needs both a wide spread and enough sightings`() {
        // A wide spread from only two sightings could be one bad GPS fix.
        assertFalse(RadioMath.looksMobile(coverageRadiusM = 5_000f, sightingCount = 2))
        assertFalse(RadioMath.looksMobile(coverageRadiusM = 50f, sightingCount = 40))
        assertTrue(RadioMath.looksMobile(coverageRadiusM = 5_000f, sightingCount = 4))
    }

    @Test
    fun `wifi standard labels cover the generations we can see`() {
        assertEquals("Wi-Fi 5 (ac)", RadioMath.wifiStandardLabel(5))
        assertEquals("Wi-Fi 6 (ax)", RadioMath.wifiStandardLabel(6))
        assertEquals("Wi-Fi 7 (be)", RadioMath.wifiStandardLabel(8))
        assertEquals("Unknown", RadioMath.wifiStandardLabel(0))
        assertEquals("Unknown", RadioMath.wifiStandardLabel(99))
    }

    @Test
    fun `distances render in metres then kilometres`() {
        assertEquals("—", RadioMath.formatMeters(0f))
        assertEquals("250 m", RadioMath.formatMeters(250f))
        assertEquals("1.5 km", RadioMath.formatMeters(1500f))
    }
}
