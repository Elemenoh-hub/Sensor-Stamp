package com.sensorstamp.openwifi.scan

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Radio and geometry helpers used to derive the fields we store per network. */
object RadioMath {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Assumed effective radiated power of a typical access point, in dBm. */
    private const val ASSUMED_TX_DBM = 20.0

    /**
     * Very rough free-space distance estimate from signal strength.
     *
     * This inverts the Friis free-space path loss equation assuming a typical
     * 20 dBm access point. Treat it as an order-of-magnitude hint and nothing
     * more: indoors, walls and bodies routinely make it wrong by 3-5x, and it
     * has no way to know an AP's real transmit power. It is stored because it is
     * a useful *relative* signal when comparing sightings of the same AP, not
     * because any individual value is trustworthy.
     */
    fun estimateDistanceMeters(rssi: Int, frequencyMhz: Int): Float {
        if (frequencyMhz <= 0 || rssi >= 0) return 0f
        val pathLossDb = ASSUMED_TX_DBM - rssi
        val exponent = (pathLossDb - 20.0 * log10(frequencyMhz.toDouble()) - 32.44) / 20.0
        val kilometres = 10.0.pow(exponent)
        return (kilometres * 1000.0).coerceIn(0.5, 5_000.0).toFloat()
    }

    /**
     * Weight for the running position centroid. Stronger sightings were taken
     * closer to the access point, so they should dominate the estimate; squaring
     * the linearised RSSI gives a ~25:1 spread between a very strong and a very
     * weak observation.
     */
    fun positionWeight(rssi: Int): Double {
        val linear = max(1, rssi + 100).toDouble()
        return linear * linear
    }

    /** Great-circle distance in metres. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Radius in metres of the area an access point was heard across, taken as
     * half the diagonal of its sighting bounds. Zero for a network only ever
     * seen from one spot.
     */
    fun coverageRadiusMeters(
        north: Double,
        south: Double,
        east: Double,
        west: Double,
    ): Float {
        if (north <= south && east <= west) return 0f
        return (distanceMeters(south, west, north, east) / 2.0).toFloat()
    }

    /**
     * True when an access point has been heard from places far enough apart that
     * it cannot be a fixed installation — on-board transit Wi-Fi, or a hotspot
     * in somebody's pocket. Those are worth flagging separately, since plotting
     * them as static points pollutes a coverage map.
     *
     * The threshold is deliberately generous: a large campus deployment can
     * legitimately reach a few hundred metres, so only clearly impossible spans
     * are called mobile.
     */
    fun looksMobile(coverageRadiusM: Float, sightingCount: Int): Boolean =
        sightingCount >= 3 && coverageRadiusM > 400f

    /** 802.11 generation label for [android.net.wifi.ScanResult.getWifiStandard] values. */
    fun wifiStandardLabel(standard: Int): String = when (standard) {
        1 -> "802.11 a/b/g"
        4 -> "Wi-Fi 4 (n)"
        5 -> "Wi-Fi 5 (ac)"
        6 -> "Wi-Fi 6 (ax)"
        7 -> "Wi-Fi 6E"
        8 -> "Wi-Fi 7 (be)"
        11 -> "802.11ad"
        else -> "Unknown"
    }

    /** Nicely rounded metre/kilometre text for distances and radii. */
    fun formatMeters(meters: Float): String = when {
        meters <= 0f -> "—"
        meters < 1_000f -> "${meters.toInt()} m"
        else -> "%.1f km".format(meters / 1000f)
    }

    /** True when two positions are the same spot to within a metre or so. */
    fun isSamePlace(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean =
        abs(lat1 - lat2) < 1e-5 && abs(lon1 - lon2) < 1e-5
}
