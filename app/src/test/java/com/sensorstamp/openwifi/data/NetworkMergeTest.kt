package com.sensorstamp.openwifi.data

import com.sensorstamp.openwifi.data.NetworkRepository.Companion.merge
import com.sensorstamp.openwifi.data.NetworkRepository.Companion.newNetwork
import com.sensorstamp.openwifi.scan.NetworkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge is what the database actually ends up holding, so it gets tested
 * directly rather than through Room.
 */
class NetworkMergeTest {

    @Test
    fun `first sighting seeds every running aggregate from itself`() {
        val entity = newNetwork(observation(rssi = -55, latitude = 51.5, longitude = -0.1))

        assertEquals(-55, entity.bestRssi)
        assertEquals(-55, entity.worstRssi)
        assertEquals(1, entity.sightingCount)
        assertEquals(51.5, entity.boundsNorth, 1e-9)
        assertEquals(51.5, entity.boundsSouth, 1e-9)
        assertEquals(0f, entity.coverageRadiusM, 1e-6f)
        assertFalse(entity.isLikelyMobile)
        // With one sighting the weighted estimate is just that sighting.
        assertEquals(51.5, entity.estimatedLatitude, 1e-9)
        assertEquals(-0.1, entity.estimatedLongitude, 1e-9)
    }

    @Test
    fun `a stronger sighting takes over the stored best fix`() {
        val first = newNetwork(observation(rssi = -80, latitude = 51.5, longitude = -0.1))
        val merged = merge(
            first,
            observation(rssi = -45, latitude = 51.6, longitude = -0.2, observedAt = 2_000L),
        )

        assertEquals(-45, merged.bestRssi)
        assertEquals(51.6, merged.latitude, 1e-9)
        assertEquals(-0.2, merged.longitude, 1e-9)
        assertEquals(2_000L, merged.bestSeenAt)
    }

    @Test
    fun `a weaker sighting leaves the best fix alone but still counts`() {
        val first = newNetwork(observation(rssi = -45, latitude = 51.5, longitude = -0.1))
        val merged = merge(first, observation(rssi = -85, latitude = 51.6, longitude = -0.2))

        assertEquals(-45, merged.bestRssi)
        assertEquals(51.5, merged.latitude, 1e-9)
        assertEquals(2, merged.sightingCount)
        assertEquals(-85, merged.worstRssi)
    }

    @Test
    fun `worst rssi tracks the weakest sighting ever seen`() {
        var entity = newNetwork(observation(rssi = -60))
        entity = merge(entity, observation(rssi = -90))
        entity = merge(entity, observation(rssi = -50))

        assertEquals(-50, entity.bestRssi)
        assertEquals(-90, entity.worstRssi)
    }

    @Test
    fun `the weighted estimate is pulled toward the stronger sighting`() {
        // Same access point heard weakly at one end of a street and strongly at
        // the other; the estimate should sit near the strong end.
        var entity = newNetwork(observation(rssi = -85, latitude = 51.500, longitude = -0.100))
        entity = merge(entity, observation(rssi = -45, latitude = 51.510, longitude = -0.100))

        assertTrue(
            "estimate ${entity.estimatedLatitude} should be past the midpoint 51.505",
            entity.estimatedLatitude > 51.505,
        )
        assertTrue(entity.estimatedLatitude < 51.510)
    }

    @Test
    fun `bounds expand to cover every place the network was heard`() {
        var entity = newNetwork(observation(latitude = 51.500, longitude = -0.100))
        entity = merge(entity, observation(latitude = 51.505, longitude = -0.090))
        entity = merge(entity, observation(latitude = 51.495, longitude = -0.110))

        assertEquals(51.505, entity.boundsNorth, 1e-9)
        assertEquals(51.495, entity.boundsSouth, 1e-9)
        assertEquals(-0.090, entity.boundsEast, 1e-9)
        assertEquals(-0.110, entity.boundsWest, 1e-9)
        assertTrue("coverage should be non-zero", entity.coverageRadiusM > 0f)
    }

    @Test
    fun `an access point heard kilometres apart is flagged as mobile`() {
        var entity = newNetwork(observation(latitude = 51.500, longitude = -0.100))
        entity = merge(entity, observation(latitude = 51.520, longitude = -0.100))
        entity = merge(entity, observation(latitude = 51.540, longitude = -0.100))

        assertTrue(entity.isLikelyMobile)
    }

    @Test
    fun `a stationary access point is never flagged as mobile`() {
        var entity = newNetwork(observation(latitude = 51.5000, longitude = -0.1000))
        repeat(10) {
            entity = merge(entity, observation(latitude = 51.5003, longitude = -0.1003))
        }

        assertFalse(entity.isLikelyMobile)
    }

    @Test
    fun `session count only advances when the session id changes`() {
        var entity = newNetwork(observation(sessionId = 1L))
        entity = merge(entity, observation(sessionId = 1L))
        assertEquals(1, entity.sessionCount)

        entity = merge(entity, observation(sessionId = 2L))
        assertEquals(2, entity.sessionCount)

        entity = merge(entity, observation(sessionId = 2L))
        assertEquals(2, entity.sessionCount)
    }

    @Test
    fun `a hidden network that later broadcasts its name is reclassified`() {
        val hidden = newNetwork(observation(ssid = "", isHidden = true))
        assertTrue(hidden.isHidden)
        assertEquals(NetworkKind.UNKNOWN.name, hidden.networkKind)

        val named = merge(hidden, observation(ssid = "Starbucks WiFi", isHidden = false))
        assertFalse(named.isHidden)
        assertEquals("Starbucks WiFi", named.ssid)
        assertEquals(NetworkKind.VENUE.name, named.networkKind)
    }

    @Test
    fun `a blank ssid never overwrites a name we already know`() {
        val known = newNetwork(observation(ssid = "Library Public"))
        val merged = merge(known, observation(ssid = ""))

        assertEquals("Library Public", merged.ssid)
    }

    @Test
    fun `radio details that arrive on a later scan are filled in`() {
        val first = newNetwork(observation(wifiStandard = 0, centerFreq0 = 0, supportsFtm = false))
        val merged = merge(
            first,
            observation(wifiStandard = 6, centerFreq0 = 5210, supportsFtm = true),
        )

        assertEquals(6, merged.wifiStandard)
        assertEquals(5210, merged.centerFreq0)
        assertTrue(merged.supportsFtm)
    }

    @Test
    fun `a later scan missing radio details does not erase what we had`() {
        val first = newNetwork(observation(wifiStandard = 6, centerFreq0 = 5210, supportsFtm = true))
        val merged = merge(
            first,
            observation(wifiStandard = 0, centerFreq0 = 0, supportsFtm = false),
        )

        assertEquals(6, merged.wifiStandard)
        assertEquals(5210, merged.centerFreq0)
        assertTrue(merged.supportsFtm)
    }

    @Test
    fun `vendor comes from the OUI and survives merging`() {
        val entity = newNetwork(observation(bssid = "24:a4:3c:11:22:33"))
        assertEquals("Ubiquiti", entity.vendor)
        assertEquals("Ubiquiti", merge(entity, observation(bssid = "24:a4:3c:11:22:33")).vendor)
    }

    @Test
    fun `a locally administered bssid reports no vendor`() {
        assertNull(newNetwork(observation(bssid = "02:11:22:33:44:55")).vendor)
    }
}
