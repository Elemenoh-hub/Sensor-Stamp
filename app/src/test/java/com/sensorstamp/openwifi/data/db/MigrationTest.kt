package com.sensorstamp.openwifi.data.db

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the shipped v1 → v2 migration against a real SQLite engine and checks the
 * result matches the schema Room expects at version 2.
 *
 * Room only validates a migration when the app opens the database on a device,
 * and a mismatch there is a hard crash — or worse, a destructive fallback that
 * silently deletes everything the user collected. Room's own migration testing
 * needs an emulator, so this reconstructs the same check on the JVM: build the
 * v1 tables from Room's exported schema, apply the exact SQL that ships, and
 * compare columns against the exported v2 schema.
 */
class MigrationTest {

    private val schemaDir = File("schemas/com.sensorstamp.openwifi.data.db.AppDatabase")

    @Test
    fun `migration produces exactly the schema Room expects at version 2`() {
        val v1 = readSchema(1)
        val v2 = readSchema(2)

        connect().use { connection ->
            createTables(connection, v1)
            seedV1Row(connection)

            Migrations.V1_TO_V2.forEach { statement ->
                connection.createStatement().use { it.executeUpdate(statement) }
            }

            v2.forEach { (table, expectedColumns) ->
                val actual = columnsOf(connection, table)

                assertEquals(
                    "column set differs for '$table'",
                    expectedColumns.keys.sorted(),
                    actual.keys.sorted(),
                )

                expectedColumns.forEach { (column, expected) ->
                    val found = actual.getValue(column)
                    assertEquals(
                        "'$table.$column' affinity",
                        expected.affinity,
                        found.affinity,
                    )
                    assertEquals(
                        "'$table.$column' nullability",
                        expected.notNull,
                        found.notNull,
                    )
                }
            }
        }
    }

    @Test
    fun `existing rows survive the migration with their data intact`() {
        connect().use { connection ->
            createTables(connection, readSchema(1))
            seedV1Row(connection)

            Migrations.V1_TO_V2.forEach { statement ->
                connection.createStatement().use { it.executeUpdate(statement) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    "SELECT bssid, ssid, bestRssi, latitude, sightingCount FROM networks"
                )
                assertTrue("the pre-existing row should still be there", rows.next())
                assertEquals("aa:bb:cc:dd:ee:ff", rows.getString("bssid"))
                assertEquals("Old Network", rows.getString("ssid"))
                assertEquals(-55, rows.getInt("bestRssi"))
                assertEquals(51.5, rows.getDouble("latitude"), 1e-9)
                assertEquals(7, rows.getInt("sightingCount"))
            }
        }
    }

    @Test
    fun `migrated rows get a usable weighted position seeded from their best fix`() {
        connect().use { connection ->
            createTables(connection, readSchema(1))
            seedV1Row(connection)

            Migrations.V1_TO_V2.forEach { statement ->
                connection.createStatement().use { it.executeUpdate(statement) }
            }

            connection.createStatement().use { statement ->
                val rows = statement.executeQuery(
                    """
                    SELECT weightSum, weightedLatSum, weightedLonSum, worstRssi, networkKind
                    FROM networks
                    """
                )
                assertTrue(rows.next())

                // -55 dBm → MAX(1, 45)^2 = 2025, matching RadioMath.positionWeight.
                val weight = rows.getDouble("weightSum")
                assertEquals(2025.0, weight, 1e-6)

                // The seeded centroid must resolve back to the original fix,
                // otherwise the map would draw migrated networks in the sea.
                assertEquals(51.5, rows.getDouble("weightedLatSum") / weight, 1e-9)
                assertEquals(-0.1, rows.getDouble("weightedLonSum") / weight, 1e-9)

                assertEquals(-55, rows.getInt("worstRssi"))
                assertEquals("UNKNOWN", rows.getString("networkKind"))
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun connect(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    private data class ColumnSpec(val affinity: String, val notNull: Boolean)

    /** Table name → column name → spec, read from Room's exported schema JSON. */
    private fun readSchema(version: Int): Map<String, Map<String, ColumnSpec>> {
        val file = File(schemaDir, "$version.json")
        assertTrue(
            "missing exported schema ${file.path}; run assembleDebug first",
            file.exists(),
        )
        val entities = JsonParser.parseString(file.readText())
            .asJsonObject.getAsJsonObject("database")
            .getAsJsonArray("entities")

        return entities.associate { element ->
            val entity = element.asJsonObject
            val columns = entity.getAsJsonArray("fields").associate { fieldElement ->
                val field = fieldElement.asJsonObject
                field.get("columnName").asString to ColumnSpec(
                    affinity = field.get("affinity").asString.uppercase(),
                    notNull = field.get("notNull").asBoolean,
                )
            }
            entity.get("tableName").asString to columns
        }
    }

    private fun createTables(connection: Connection, schema: Map<String, Map<String, ColumnSpec>>) {
        val entities = JsonParser.parseString(File(schemaDir, "1.json").readText())
            .asJsonObject.getAsJsonObject("database")
            .getAsJsonArray("entities")
        entities.forEach { element ->
            val entity = element.asJsonObject
            val table = entity.get("tableName").asString
            val createSql = entity.get("createSql").asString.replace("\${TABLE_NAME}", table)
            connection.createStatement().use { it.executeUpdate(createSql) }
        }
        assertTrue(schema.isNotEmpty())
    }

    /** A representative v1 row, so the migration is exercised against real data. */
    private fun seedV1Row(connection: Connection) {
        connection.createStatement().use {
            it.executeUpdate(
                """
                INSERT INTO networks (
                    bssid, ssid, capabilities, securityType, frequencyMhz, channel, band,
                    channelWidthMhz, bestRssi, latitude, longitude, accuracyM, altitudeM,
                    speedMps, bearingDeg, locationProvider, venueHint, isPasspoint,
                    isHidden, firstSeenAt, lastSeenAt, sightingCount
                ) VALUES (
                    'aa:bb:cc:dd:ee:ff', 'Old Network', '[ESS]', 'OPEN', 2437, 6, '2.4 GHz',
                    20, -55, 51.5, -0.1, 8.0, 0.0,
                    0.0, 0.0, 'fused', NULL, 0,
                    0, 1700000000000, 1700000600000, 7
                )
                """.trimIndent()
            )
        }
        connection.createStatement().use {
            it.executeUpdate(
                """
                INSERT INTO sightings (
                    bssid, ssid, rssi, frequencyMhz, latitude, longitude, accuracyM,
                    altitudeM, speedMps, bearingDeg, locationProvider, locationAgeMs,
                    observedAt, sessionId
                ) VALUES (
                    'aa:bb:cc:dd:ee:ff', 'Old Network', -55, 2437, 51.5, -0.1, 8.0,
                    0.0, 0.0, 0.0, 'fused', 100,
                    1700000000000, 1700000000000
                )
                """.trimIndent()
            )
        }
    }

    private fun columnsOf(connection: Connection, table: String): Map<String, ColumnSpec> =
        buildMap {
            connection.createStatement().use { statement ->
                val info = statement.executeQuery("PRAGMA table_info($table)")
                while (info.next()) {
                    val declared = info.getString("type").uppercase()
                    put(
                        info.getString("name"),
                        ColumnSpec(
                            affinity = declared,
                            // Room treats the INTEGER PRIMARY KEY as NOT NULL even
                            // though SQLite reports it as nullable.
                            notNull = info.getInt("notnull") == 1 || info.getInt("pk") == 1,
                        ),
                    )
                }
            }
        }
}
