package com.sensorstamp.openwifi.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

fun formatClock(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else timeFormat.format(Date(epochMillis))

fun formatDateTime(epochMillis: Long): String =
    if (epochMillis <= 0) "—" else dateTimeFormat.format(Date(epochMillis))

/** "just now", "4m ago", "2h ago" — compact enough for a list row. */
fun formatRelative(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0) return "—"
    val seconds = (now - epochMillis) / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0s"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "%dm %02ds".format(minutes, seconds)
        else -> "${seconds}s"
    }
}

/** Scan cadence, said the way a person would say it. */
fun formatInterval(seconds: Int): String = when {
    seconds < 60 -> "$seconds sec"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

fun formatCoordinates(latitude: Double, longitude: Double): String {
    val ns = if (latitude >= 0) "N" else "S"
    val ew = if (longitude >= 0) "E" else "W"
    return "%.5f°%s  %.5f°%s".format(abs(latitude), ns, abs(longitude), ew)
}

/** RSSI mapped to 0..4 bars, using the same thresholds Android's own UI uses. */
fun signalBars(rssi: Int): Int = when {
    rssi >= -55 -> 4
    rssi >= -67 -> 3
    rssi >= -75 -> 2
    rssi >= -85 -> 1
    else -> 0
}
