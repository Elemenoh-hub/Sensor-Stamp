package com.sensorstamp.openwifi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SSIDs are attacker-controlled strings that end up in an exported spreadsheet,
 * so the escaping needs to hold for commas, quotes and newlines.
 */
class CsvTest {

    @Test
    fun `plain values are left alone`() {
        assertEquals("CoffeeShop", Csv.escape("CoffeeShop"))
        assertEquals("-73", Csv.escape("-73"))
    }

    @Test
    fun `values containing a comma are quoted`() {
        assertEquals("\"Bob's Bar, Downtown\"", Csv.escape("Bob's Bar, Downtown"))
    }

    @Test
    fun `embedded quotes are doubled`() {
        assertEquals("\"The \"\"Free\"\" WiFi\"", Csv.escape("The \"Free\" WiFi"))
    }

    @Test
    fun `newlines are quoted so they cannot break the row`() {
        assertEquals("\"line1\nline2\"", Csv.escape("line1\nline2"))
        assertEquals("\"line1\r\nline2\"", Csv.escape("line1\r\nline2"))
    }

    @Test
    fun `timestamps export as UTC ISO-8601`() {
        assertEquals("1970-01-01T00:00:00Z", Csv.iso(0L))
        assertEquals("2024-01-15T12:30:45Z", Csv.iso(1_705_321_845_000L))
    }
}
