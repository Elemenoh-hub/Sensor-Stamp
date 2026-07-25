package com.sensorstamp.openwifi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {

    @Query("SELECT * FROM networks ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM networks ORDER BY lastSeenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NetworkEntity>>

    @Query(
        """
        SELECT * FROM networks
        WHERE ssid LIKE '%' || :query || '%' OR bssid LIKE '%' || :query || '%'
        ORDER BY lastSeenAt DESC
        """
    )
    fun search(query: String): Flow<List<NetworkEntity>>

    @Query("SELECT * FROM networks WHERE bssid = :bssid LIMIT 1")
    suspend fun findByBssid(bssid: String): NetworkEntity?

    @Query("SELECT bssid FROM networks")
    suspend fun allBssids(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(network: NetworkEntity): Long

    /**
     * Merge a fresh observation into an existing row. The stored fix is only
     * replaced when this sighting was stronger, since the strongest signal is
     * the best proxy we have for "closest to the access point".
     */
    @Query(
        """
        UPDATE networks SET
            lastSeenAt = :observedAt,
            sightingCount = sightingCount + 1,
            ssid = CASE WHEN :ssid != '' THEN :ssid ELSE ssid END,
            bestRssi = CASE WHEN :rssi > bestRssi THEN :rssi ELSE bestRssi END,
            latitude = CASE WHEN :rssi > bestRssi THEN :lat ELSE latitude END,
            longitude = CASE WHEN :rssi > bestRssi THEN :lon ELSE longitude END,
            accuracyM = CASE WHEN :rssi > bestRssi THEN :accuracy ELSE accuracyM END,
            altitudeM = CASE WHEN :rssi > bestRssi THEN :altitude ELSE altitudeM END,
            speedMps = CASE WHEN :rssi > bestRssi THEN :speed ELSE speedMps END,
            bearingDeg = CASE WHEN :rssi > bestRssi THEN :bearing ELSE bearingDeg END,
            locationProvider = CASE WHEN :rssi > bestRssi THEN :provider ELSE locationProvider END
        WHERE bssid = :bssid
        """
    )
    suspend fun recordSighting(
        bssid: String,
        ssid: String,
        rssi: Int,
        lat: Double,
        lon: Double,
        accuracy: Float,
        altitude: Double,
        speed: Float,
        bearing: Float,
        provider: String,
        observedAt: Long,
    )

    @Insert
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("SELECT * FROM sightings ORDER BY observedAt ASC")
    suspend fun allSightings(): List<SightingEntity>

    @Query("SELECT * FROM networks ORDER BY firstSeenAt ASC")
    suspend fun allNetworks(): List<NetworkEntity>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM networks) AS networks,
            (SELECT COUNT(*) FROM sightings) AS sightings,
            (SELECT COUNT(DISTINCT ssid) FROM networks WHERE ssid != '') AS uniqueSsids
        """
    )
    fun observeTotals(): Flow<ScanTotals>

    @Query("SELECT COUNT(*) FROM networks WHERE firstSeenAt >= :since")
    fun observeNewSince(since: Long): Flow<Int>

    @Query("DELETE FROM networks")
    suspend fun clearNetworks()

    @Query("DELETE FROM sightings")
    suspend fun clearSightings()
}
