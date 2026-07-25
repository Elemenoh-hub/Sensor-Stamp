package com.sensorstamp.openwifi.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations, kept as plain SQL strings rather than inline in the
 * [Migration] body so a test can execute exactly the statements that ship.
 * A migration that is wrong destroys a user's collected data on upgrade, and
 * Room only finds out at runtime on a real device.
 */
object Migrations {

    /**
     * v1 → v2 widens what we record about each access point: vendor and kind
     * classification, radio generation, the running weighted-position estimate,
     * and the coverage bounds. Everything is additive with a default, so an
     * existing collection survives untouched — the new columns simply start
     * filling in from the next sighting.
     */
    val V1_TO_V2: List<String> = buildList {
        listOf(
            "vendor TEXT DEFAULT NULL",
            "networkKind TEXT NOT NULL DEFAULT 'UNKNOWN'",
            "wifiStandard INTEGER NOT NULL DEFAULT 0",
            "supportsFtm INTEGER NOT NULL DEFAULT 0",
            "centerFreq0 INTEGER NOT NULL DEFAULT 0",
            "centerFreq1 INTEGER NOT NULL DEFAULT 0",
            "worstRssi INTEGER NOT NULL DEFAULT 0",
            "bestSeenAt INTEGER NOT NULL DEFAULT 0",
            "weightSum REAL NOT NULL DEFAULT 0",
            "weightedLatSum REAL NOT NULL DEFAULT 0",
            "weightedLonSum REAL NOT NULL DEFAULT 0",
            "boundsNorth REAL NOT NULL DEFAULT 0",
            "boundsSouth REAL NOT NULL DEFAULT 0",
            "boundsEast REAL NOT NULL DEFAULT 0",
            "boundsWest REAL NOT NULL DEFAULT 0",
            "coverageRadiusM REAL NOT NULL DEFAULT 0",
            "isLikelyMobile INTEGER NOT NULL DEFAULT 0",
            "sessionCount INTEGER NOT NULL DEFAULT 1",
            "lastSessionId INTEGER NOT NULL DEFAULT 0",
        ).forEach { add("ALTER TABLE networks ADD COLUMN $it") }

        listOf(
            "channel INTEGER NOT NULL DEFAULT 0",
            "band TEXT NOT NULL DEFAULT ''",
            "securityType TEXT NOT NULL DEFAULT ''",
            "estimatedDistanceM REAL NOT NULL DEFAULT 0",
            "scanTimestampMicros INTEGER NOT NULL DEFAULT 0",
        ).forEach { add("ALTER TABLE sightings ADD COLUMN $it") }

        // Seed the weighted-position estimate from the fix each network already
        // has, so the map has something sensible to draw before any new sighting
        // arrives. MAX(1, rssi + 100) squared mirrors RadioMath.positionWeight,
        // so a migrated row carries the weight a fresh sighting would have given it.
        add(
            """
            UPDATE networks SET
                weightSum = MAX(1, bestRssi + 100) * MAX(1, bestRssi + 100),
                weightedLatSum = latitude * MAX(1, bestRssi + 100) * MAX(1, bestRssi + 100),
                weightedLonSum = longitude * MAX(1, bestRssi + 100) * MAX(1, bestRssi + 100),
                boundsNorth = latitude,
                boundsSouth = latitude,
                boundsEast = longitude,
                boundsWest = longitude,
                worstRssi = bestRssi,
                bestSeenAt = lastSeenAt
            """.trimIndent()
        )

        add(
            "CREATE INDEX IF NOT EXISTS index_networks_latitude_longitude " +
                "ON networks (latitude, longitude)"
        )
        add(
            "CREATE INDEX IF NOT EXISTS index_sightings_sessionId " +
                "ON sightings (sessionId)"
        )
    }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            V1_TO_V2.forEach { db.execSQL(it) }
        }
    }
}
