package com.sensorstamp.openwifi.data

import android.content.Context
import com.sensorstamp.openwifi.data.db.AppDatabase
import com.sensorstamp.openwifi.data.db.NetworkDao
import com.sensorstamp.openwifi.data.db.NetworkEntity
import com.sensorstamp.openwifi.data.db.ScanTotals
import com.sensorstamp.openwifi.data.db.SightingEntity
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

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

    /**
     * Persist an observation. Returns true when this access point had never been
     * seen before, so the caller can count discoveries separately from re-sightings.
     */
    suspend fun record(obs: Observation, logSightingRow: Boolean = true): Boolean {
        val existing = dao.findByBssid(obs.bssid)
        val isNew = existing == null

        if (isNew) {
            dao.insert(
                NetworkEntity(
                    bssid = obs.bssid,
                    ssid = obs.ssid,
                    capabilities = obs.capabilities,
                    securityType = obs.securityType,
                    frequencyMhz = obs.frequencyMhz,
                    channel = obs.channel,
                    band = obs.band,
                    channelWidthMhz = obs.channelWidthMhz,
                    bestRssi = obs.rssi,
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
                    sightingCount = 1,
                )
            )
        } else {
            dao.recordSighting(
                bssid = obs.bssid,
                ssid = obs.ssid,
                rssi = obs.rssi,
                lat = obs.latitude,
                lon = obs.longitude,
                accuracy = obs.accuracyM,
                altitude = obs.altitudeM,
                speed = obs.speedMps,
                bearing = obs.bearingDeg,
                provider = obs.locationProvider,
                observedAt = obs.observedAt,
            )
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
                "bssid,ssid,security,capabilities,latitude,longitude,accuracy_m,altitude_m," +
                    "best_rssi_dbm,frequency_mhz,channel,band,channel_width_mhz,hidden,passpoint," +
                    "venue,first_seen_utc,last_seen_utc,sightings"
            )
            rows.forEach { n ->
                w.appendLine(
                    listOf(
                        n.bssid, n.ssid, n.securityType, n.capabilities,
                        n.latitude, n.longitude, n.accuracyM, n.altitudeM,
                        n.bestRssi, n.frequencyMhz, n.channel, n.band, n.channelWidthMhz,
                        n.isHidden, n.isPasspoint, n.venueHint.orEmpty(),
                        Csv.iso(n.firstSeenAt), Csv.iso(n.lastSeenAt), n.sightingCount,
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
                "observed_utc,bssid,ssid,rssi_dbm,frequency_mhz,latitude,longitude," +
                    "accuracy_m,altitude_m,speed_mps,bearing_deg,provider,fix_age_ms,session_id"
            )
            rows.forEach { s ->
                w.appendLine(
                    listOf(
                        Csv.iso(s.observedAt), s.bssid, s.ssid, s.rssi, s.frequencyMhz,
                        s.latitude, s.longitude, s.accuracyM, s.altitudeM, s.speedMps,
                        s.bearingDeg, s.locationProvider, s.locationAgeMs, s.sessionId,
                    ).joinToString(",") { Csv.escape(it.toString()) }
                )
            }
        }
    }

    companion object {
        fun from(context: Context): NetworkRepository =
            NetworkRepository(AppDatabase.get(context).networkDao())
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
