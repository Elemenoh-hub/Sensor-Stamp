package com.sensorstamp.openwifi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
        WHERE ssid LIKE '%' || :query || '%'
           OR bssid LIKE '%' || :query || '%'
           OR vendor LIKE '%' || :query || '%'
        ORDER BY lastSeenAt DESC
        """
    )
    fun search(query: String): Flow<List<NetworkEntity>>

    /**
     * Map projection. Prefers the signal-weighted centroid over the single best
     * fix, since that is the better estimate of where the radio actually sits.
     */
    @Query(
        """
        SELECT
            id,
            bssid,
            ssid,
            CASE WHEN weightSum > 0 THEN weightedLatSum / weightSum ELSE latitude END AS latitude,
            CASE WHEN weightSum > 0 THEN weightedLonSum / weightSum ELSE longitude END AS longitude,
            bestRssi,
            band,
            networkKind,
            securityType,
            lastSeenAt,
            sightingCount,
            coverageRadiusM,
            isLikelyMobile,
            vendor
        FROM networks
        WHERE latitude != 0.0 OR longitude != 0.0
        """
    )
    fun observeMapPoints(): Flow<List<MapPoint>>

    @Query("SELECT * FROM networks WHERE bssid = :bssid LIMIT 1")
    suspend fun findByBssid(bssid: String): NetworkEntity?

    @Query("SELECT * FROM networks WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<NetworkEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(network: NetworkEntity): Long

    @Update
    suspend fun update(network: NetworkEntity)

    @Insert
    suspend fun insertSighting(sighting: SightingEntity)

    @Query("SELECT * FROM sightings ORDER BY observedAt ASC")
    suspend fun allSightings(): List<SightingEntity>

    /** Every sighting of one access point, oldest first — the approach trail. */
    @Query("SELECT * FROM sightings WHERE bssid = :bssid ORDER BY observedAt ASC")
    fun observeSightingsFor(bssid: String): Flow<List<SightingEntity>>

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
