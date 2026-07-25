package com.sensorstamp.openwifi.data

import android.content.Context
import com.sensorstamp.openwifi.data.db.AppDatabase
import com.sensorstamp.openwifi.data.db.MapPoint
import com.sensorstamp.openwifi.data.db.NetworkDao
import com.sensorstamp.openwifi.data.db.NetworkEntity
import com.sensorstamp.openwifi.data.db.ScanTotals
import com.sensorstamp.openwifi.data.db.SightingEntity
import com.sensorstamp.openwifi.scan.NetworkKind
import com.sensorstamp.openwifi.scan.OuiVendors
import com.sensorstamp.openwifi.scan.RadioMath
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/** A single open access point as observed at one point in time and space. */
data class Observation(
    val bssid: String,
    val ssid: String,
    val capabilities: String,
    val securityType: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val band: String,
    val channelWidthMhz: Int,
    val centerFreq0: Int,
    val centerFreq1: Int,
    val wifiStandard: Int,
    val supportsFtm: Boolean,
    val isPasspoint: Boolean,
    val isHidden: Boolean,
    val venueHint: String?,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double,
    val speedMps: Float,
    val bearingDeg: Float,
    val locationProvider: String,
    val locationAgeMs: Long,
    val scanTimestampMicros: Long,
    val observedAt: Long,
    /** Groups every observation made during one press of Start. */
    val sessionId: Long,
)

class NetworkRepository(private val dao: NetworkDao) {

    fun observeRecent(limit: Int): Flow<List<NetworkEntity>> = dao.observeRecent(limit)
    fun observeAll(): Flow<List<NetworkEntity>> = dao.observeAll()
    fun search(query: String): Flow<List<NetworkEntity>> = dao.search(query)
    fun observeTotals(): Flow<ScanTotals> = dao.observeTotals()
    fun observeNewSince(since: Long): Flow<Int> = dao.observeNewSince(since)
    fun observeMapPoints(): Flow<List<MapPoint>> = dao.observeMapPoints()
    fun observeSightingsFor(bssid: String): Flow<List<SightingEntity>> =
        dao.observeSightingsFor(bssid)

    /**
     * Persist an observation. Returns true when this access point had never been
     * seen before, so the caller can count discoveries separately from re-sightings.
     */
    suspend fun record(obs: Observation, logSightingRow: Boolean = true): Boolean {
        val existing = dao.findByBssid(obs.bssid)
        val isNew = existing == null

        if (existing == null) {
            dao.insert(newNetwork(obs))
        } else {
            dao.update(merge(existing, obs))
        }

        if (logSightingRow) {
            dao.insertSighting(
                SightingEntity(
                    bssid = obs.bssid,
                    ssid = obs.ssid,
                    rssi = obs.rssi,
                    frequencyMhz = obs.frequencyMhz,
                    latitude = obs.latitude,
                    longitude = obs.longitude,
                    accuracyM = obs.accuracyM,
                    altitudeM = obs.altitudeM,
                    speedMps = obs.speedMps,
                    bearingDeg = obs.bearingDeg,
                    locationProvider = obs.locationProvider,
                    locationAgeMs = obs.locationAgeMs,
                    observedAt = obs.observedAt,
                    sessionId = obs.sessionId,
                    channel = obs.channel,
                    band = obs.band,
                    securityType = obs.securityType,
                    estimatedDistanceM = RadioMath.estimateDistanceMeters(
                        obs.rssi,
                        obs.frequencyMhz,
                    ),
                    scanTimestampMicros = obs.scanTimestampMicros,
                )
            )
        }
        return isNew
    }

    suspend fun clearAll() {
        dao.clearSightings()
        dao.clearNetworks()
    }

    /** Flat CSV of every catalogued access point — one row per AP. */
    suspend fun exportNetworksCsv(out: OutputStream) {
        val rows = dao.allNetworks()
        out.bufferedWriter().use { w ->
            w.appendLine(
                "bssid,ssid,vendor,network_kind,security,capabilities," +
                    "best_latitude,best_longitude,est_latitude,est_longitude," +
                    "accuracy_m,altitude_m,best_rssi_dbm,worst_rssi_dbm," +
                    "frequency_mhz,channel,band,channel_width_mhz,center_freq0,center_freq1," +
                    "wifi_standard,supports_ftm,coverage_radius_m,likely_mobile," +
                    "bounds_north,bounds_south,bounds_east,bounds_west," +
                    "hidden,passpoint,venue,first_seen_utc,last_seen_utc,best_seen_utc," +
                    "sightings,sessions"
            )
            rows.forEach { n ->
                w.appendLine(
                    listOf(
                        n.bssid, n.ssid, n.vendor.orEmpty(), n.networkKind, n.securityType,
                        n.capabilities,
                        n.latitude, n.longitude, n.estimatedLatitude, n.estimatedLongitude,
                        n.accuracyM, n.altitudeM, n.bestRssi, n.worstRssi,
                        n.frequencyMhz, n.channel, n.band, n.channelWidthMhz,
                        n.centerFreq0, n.centerFreq1,
                        RadioMath.wifiStandardLabel(n.wifiStandard), n.supportsFtm,
                        n.coverageRadiusM, n.isLikelyMobile,
                        n.boundsNorth, n.boundsSouth, n.boundsEast, n.boundsWest,
                        n.isHidden, n.isPasspoint, n.venueHint.orEmpty(),
                        Csv.iso(n.firstSeenAt), Csv.iso(n.lastSeenAt), Csv.iso(n.bestSeenAt),
                        n.sightingCount, n.sessionCount,
                    ).joinToString(",") { Csv.escape(it.toString()) }
                )
            }
        }
    }

    /** Every individual observation — the granular trail for coverage analysis. */
    suspend fun exportSightingsCsv(out: OutputStream) {
        val rows = dao.allSightings()
        out.bufferedWriter().use { w ->
            w.appendLine(
                "observed_utc,bssid,ssid,security,rssi_dbm,est_distance_m," +
                    "frequency_mhz,channel,band,latitude,longitude," +
                    "accuracy_m,altitude_m,speed_mps,bearing_deg,provider," +
                    "fix_age_ms,scan_timestamp_us,session_id"
            )
            rows.forEach { s ->
                w.appendLine(
                    listOf(
                        Csv.iso(s.observedAt), s.bssid, s.ssid, s.securityType, s.rssi,
                        s.estimatedDistanceM, s.frequencyMhz, s.channel, s.band,
                        s.latitude, s.longitude, s.accuracyM, s.altitudeM, s.speedMps,
                        s.bearingDeg, s.locationProvider, s.locationAgeMs,
                        s.scanTimestampMicros, s.sessionId,
                    ).joinToString(",") { Csv.escape(it.toString()) }
                )
            }
        }
    }

    companion object {
        fun from(context: Context): NetworkRepository =
            NetworkRepository(AppDatabase.get(context).networkDao())

        /** First sighting of an access point: seed every running aggregate from it. */
        fun newNetwork(obs: Observation): NetworkEntity {
            val vendor = OuiVendors.lookup(obs.bssid)
            val weight = RadioMath.positionWeight(obs.rssi)
            return NetworkEntity(
                bssid = obs.bssid,
                ssid = obs.ssid,
                capabilities = obs.capabilities,
                securityType = obs.securityType,
                frequencyMhz = obs.frequencyMhz,
                channel = obs.channel,
                band = obs.band,
                channelWidthMhz = obs.channelWidthMhz,
                bestRssi = obs.rssi,
                worstRssi = obs.rssi,
                latitude = obs.latitude,
                longitude = obs.longitude,
                accuracyM = obs.accuracyM,
                altitudeM = obs.altitudeM,
                speedMps = obs.speedMps,
                bearingDeg = obs.bearingDeg,
                locationProvider = obs.locationProvider,
                venueHint = obs.venueHint,
                isPasspoint = obs.isPasspoint,
                isHidden = obs.isHidden,
                firstSeenAt = obs.observedAt,
                lastSeenAt = obs.observedAt,
                bestSeenAt = obs.observedAt,
                sightingCount = 1,
                vendor = vendor,
                networkKind = NetworkKind.classify(obs.ssid, vendor).name,
                wifiStandard = obs.wifiStandard,
                supportsFtm = obs.supportsFtm,
                centerFreq0 = obs.centerFreq0,
                centerFreq1 = obs.centerFreq1,
                weightSum = weight,
                weightedLatSum = obs.latitude * weight,
                weightedLonSum = obs.longitude * weight,
                boundsNorth = obs.latitude,
                boundsSouth = obs.latitude,
                boundsEast = obs.longitude,
                boundsWest = obs.longitude,
                coverageRadiusM = 0f,
                isLikelyMobile = false,
                sessionCount = 1,
                lastSessionId = obs.sessionId,
            )
        }

        /**
         * Fold a repeat sighting into the stored row.
         *
         * The single best fix is only replaced when this sighting was stronger,
         * since the strongest signal is the best proxy for "closest to the access
         * point". The weighted centroid, bounds and counters always advance.
         *
         * Pure by design: this is the heart of what the database ends up holding,
         * so it is worth being able to test directly.
         */
        fun merge(existing: NetworkEntity, obs: Observation): NetworkEntity {
            val stronger = obs.rssi > existing.bestRssi
            val weight = RadioMath.positionWeight(obs.rssi)

            val north = max(existing.boundsNorth, obs.latitude)
            val south = min(existing.boundsSouth, obs.latitude)
            val east = max(existing.boundsEast, obs.longitude)
            val west = min(existing.boundsWest, obs.longitude)
            val radius = RadioMath.coverageRadiusMeters(north, south, east, west)
            val sightings = existing.sightingCount + 1

            // A network first seen in an earlier session and met again now has
            // been encountered across two trips, which is a useful reliability
            // signal — a one-off sighting may just have been a passing hotspot.
            val newSession = obs.sessionId != existing.lastSessionId

            val vendor = existing.vendor ?: OuiVendors.lookup(obs.bssid)
            val ssid = obs.ssid.ifBlank { existing.ssid }

            return existing.copy(
                ssid = ssid,
                // A hidden network that later broadcasts its name stops being hidden.
                isHidden = existing.isHidden && obs.isHidden,
                capabilities = obs.capabilities.ifBlank { existing.capabilities },
                lastSeenAt = obs.observedAt,
                sightingCount = sightings,

                bestRssi = if (stronger) obs.rssi else existing.bestRssi,
                worstRssi = min(existing.worstRssi, obs.rssi),
                bestSeenAt = if (stronger) obs.observedAt else existing.bestSeenAt,
                latitude = if (stronger) obs.latitude else existing.latitude,
                longitude = if (stronger) obs.longitude else existing.longitude,
                accuracyM = if (stronger) obs.accuracyM else existing.accuracyM,
                altitudeM = if (stronger) obs.altitudeM else existing.altitudeM,
                speedMps = if (stronger) obs.speedMps else existing.speedMps,
                bearingDeg = if (stronger) obs.bearingDeg else existing.bearingDeg,
                locationProvider = if (stronger) obs.locationProvider else existing.locationProvider,

                weightSum = existing.weightSum + weight,
                weightedLatSum = existing.weightedLatSum + obs.latitude * weight,
                weightedLonSum = existing.weightedLonSum + obs.longitude * weight,

                boundsNorth = north,
                boundsSouth = south,
                boundsEast = east,
                boundsWest = west,
                coverageRadiusM = radius,
                isLikelyMobile = RadioMath.looksMobile(radius, sightings),

                // Radio details can arrive late — some scans report a standard or
                // centre frequency the first one omitted.
                wifiStandard = if (obs.wifiStandard != 0) obs.wifiStandard else existing.wifiStandard,
                supportsFtm = existing.supportsFtm || obs.supportsFtm,
                centerFreq0 = if (obs.centerFreq0 != 0) obs.centerFreq0 else existing.centerFreq0,
                centerFreq1 = if (obs.centerFreq1 != 0) obs.centerFreq1 else existing.centerFreq1,
                channelWidthMhz = if (obs.channelWidthMhz != 0) {
                    obs.channelWidthMhz
                } else {
                    existing.channelWidthMhz
                },
                venueHint = obs.venueHint ?: existing.venueHint,
                isPasspoint = existing.isPasspoint || obs.isPasspoint,

                vendor = vendor,
                // Re-classify once a name is known; an SSID that was blank on the
                // first sighting would otherwise be stuck as UNKNOWN forever.
                networkKind = if (existing.networkKind == NetworkKind.UNKNOWN.name ||
                    existing.ssid.isBlank()
                ) {
                    NetworkKind.classify(ssid, vendor).name
                } else {
                    existing.networkKind
                },

                sessionCount = if (newSession) existing.sessionCount + 1 else existing.sessionCount,
                lastSessionId = obs.sessionId,
            )
        }
    }
}

internal object Csv {
    private val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

    fun iso(epochMillis: Long): String = formatter.format(java.util.Date(epochMillis))

    fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
