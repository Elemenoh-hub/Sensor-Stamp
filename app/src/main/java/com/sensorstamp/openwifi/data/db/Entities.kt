package com.sensorstamp.openwifi.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sensorstamp.openwifi.scan.RadioMath

/**
 * One row per distinct access point (keyed by BSSID). Holds the best-quality
 * fix we have seen for it, a running weighted position estimate, and roll-up
 * counters describing where and how often it was heard.
 */
@Entity(
    tableName = "networks",
    indices = [
        Index(value = ["bssid"], unique = true),
        Index(value = ["lastSeenAt"]),
        Index(value = ["latitude", "longitude"]),
    ]
)
data class NetworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** MAC address of the AP radio — the stable identity of a physical access point. */
    val bssid: String,
    /** Broadcast network name. Blank for hidden networks. */
    val ssid: String,
    /** Raw capability string straight from the scan, kept verbatim for later analysis. */
    val capabilities: String,
    /** Our classification: OPEN / OWE / OPEN_WPS. See [com.sensorstamp.openwifi.scan.WifiSecurity]. */
    val securityType: String,

    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val channelWidthMhz: Int,

    /** Strongest RSSI observed, and where we were standing when we heard it. */
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

    // ---- Added in schema v2 ----------------------------------------------

    /** Hardware maker from the OUI, or null when unknown / locally administered. */
    @ColumnInfo(defaultValue = "NULL") val vendor: String? = null,

    /** Best guess at what this network is for. See [com.sensorstamp.openwifi.scan.NetworkKind]. */
    @ColumnInfo(defaultValue = "'UNKNOWN'") val networkKind: String = "UNKNOWN",

    /** Raw [android.net.wifi.ScanResult.getWifiStandard] value; 0 when unreported. */
    @ColumnInfo(defaultValue = "0") val wifiStandard: Int = 0,

    /** Advertises 802.11mc fine timing measurement, so it can be range-measured precisely. */
    @ColumnInfo(defaultValue = "0") val supportsFtm: Boolean = false,

    /** Segment centre frequencies, which reveal bonded-channel layout. */
    @ColumnInfo(defaultValue = "0") val centerFreq0: Int = 0,
    @ColumnInfo(defaultValue = "0") val centerFreq1: Int = 0,

    /** Weakest sighting, giving the dynamic range this AP was heard across. */
    @ColumnInfo(defaultValue = "0") val worstRssi: Int = 0,

    /** When the strongest sighting was recorded. */
    @ColumnInfo(defaultValue = "0") val bestSeenAt: Long = 0,

    /**
     * Running signal-weighted position estimate. Stored as sums so each new
     * sighting folds in with a single UPDATE and no read-modify-write.
     * Divide the lat/lon sums by the weight sum to get the centroid.
     */
    @ColumnInfo(defaultValue = "0") val weightSum: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val weightedLatSum: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val weightedLonSum: Double = 0.0,

    /** Bounding box of every place this AP was audible from. */
    @ColumnInfo(defaultValue = "0") val boundsNorth: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val boundsSouth: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val boundsEast: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val boundsWest: Double = 0.0,

    /** Half-diagonal of the bounds, in metres — how far this AP reached. */
    @ColumnInfo(defaultValue = "0") val coverageRadiusM: Float = 0f,

    /** Heard from too far apart to be a fixed installation (transit or pocket hotspot). */
    @ColumnInfo(defaultValue = "0") val isLikelyMobile: Boolean = false,

    /** How many separate collection sessions have encountered this AP. */
    @ColumnInfo(defaultValue = "1") val sessionCount: Int = 1,
    @ColumnInfo(defaultValue = "0") val lastSessionId: Long = 0,
) {
    /**
     * Signal-weighted centroid of every sighting — a better guess at where the
     * access point physically sits than the single strongest fix, because it
     * triangulates across the whole approach. Falls back to the best fix for
     * networks seen only once.
     */
    val estimatedLatitude: Double
        get() = if (weightSum > 0) weightedLatSum / weightSum else latitude

    val estimatedLongitude: Double
        get() = if (weightSum > 0) weightedLonSum / weightSum else longitude

    /** Rough free-space distance we were from the AP at its strongest sighting. */
    val estimatedDistanceAtBestM: Float
        get() = RadioMath.estimateDistanceMeters(bestRssi, frequencyMhz)
}

/**
 * One row per observation. This is the granular trail: the same AP seen from
 * many points along a route is what lets you work out its real coverage.
 */
@Entity(
    tableName = "sightings",
    indices = [
        Index(value = ["bssid"]),
        Index(value = ["observedAt"]),
        Index(value = ["sessionId"]),
    ]
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

    // ---- Added in schema v2 ----------------------------------------------

    /** Denormalised so the sightings export stands alone without a join. */
    @ColumnInfo(defaultValue = "0") val channel: Int = 0,
    @ColumnInfo(defaultValue = "''") val band: String = "",
    @ColumnInfo(defaultValue = "''") val securityType: String = "",

    /** Rough free-space distance implied by this sighting's signal strength. */
    @ColumnInfo(defaultValue = "0") val estimatedDistanceM: Float = 0f,

    /**
     * The radio's own timestamp for the beacon frame, in microseconds since
     * boot. Two sightings sharing one value came from the same frame — which is
     * how we avoid re-logging cached results while Android is throttling scans.
     */
    @ColumnInfo(defaultValue = "0") val scanTimestampMicros: Long = 0,
)

/** Aggregate counters for the dashboard, computed in SQL rather than in memory. */
data class ScanTotals(
    val networks: Int,
    val sightings: Int,
    val uniqueSsids: Int,
)

/** Minimal projection for the map, which only needs geometry and a colour key. */
data class MapPoint(
    val id: Long,
    val bssid: String,
    val ssid: String,
    val latitude: Double,
    val longitude: Double,
    val bestRssi: Int,
    val band: String,
    val networkKind: String,
    val securityType: String,
    val lastSeenAt: Long,
    val sightingCount: Int,
    val coverageRadiusM: Float,
    val isLikelyMobile: Boolean,
    val vendor: String?,
)
