package com.sensorstamp.openwifi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per distinct access point (keyed by BSSID). Holds the best-quality
 * fix we have seen for it plus roll-up counters.
 */
@Entity(
    tableName = "networks",
    indices = [Index(value = ["bssid"], unique = true), Index(value = ["lastSeenAt"])]
)
data class NetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** MAC address of the AP radio — the stable identity of a physical access point. */
    val bssid: String,
    /** Broadcast network name. Blank for hidden networks. */
    val ssid: String,
    /** Raw capability string straight from the scan, kept verbatim for later analysis. */
    val capabilities: String,
    /** Our classification: OPEN / OWE / OPEN_WPS etc. See [com.sensorstamp.openwifi.scan.WifiSecurity]. */
    val securityType: String,

    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val channelWidthMhz: Int,

    /** Strongest RSSI observed, and where we were standing when we saw it. */
    val bestRssi: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double,
    val speedMps: Float,
    val bearingDeg: Float,
    val locationProvider: String,

    val venueHint: String? = null,
    val isPasspoint: Boolean = false,
    val isHidden: Boolean = false,

    val firstSeenAt: Long,
    val lastSeenAt: Long,
    @ColumnInfo(defaultValue = "1") val sightingCount: Int = 1,
)

/**
 * One row per observation. This is the granular trail: the same AP seen from
 * many points along a route lets you work out its real coverage footprint.
 */
@Entity(
    tableName = "sightings",
    indices = [Index(value = ["bssid"]), Index(value = ["observedAt"])]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double,
    val speedMps: Float,
    val bearingDeg: Float,
    val locationProvider: String,
    /** Age of the location fix at the moment of the scan, in ms. Low = trustworthy. */
    val locationAgeMs: Long,
    val observedAt: Long,
    val sessionId: Long,
)

/** Aggregate counters for the dashboard, computed in SQL rather than in memory. */
data class ScanTotals(
    val networks: Int,
    val sightings: Int,
    val uniqueSsids: Int,
)
