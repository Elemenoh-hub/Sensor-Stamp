package com.sensorstamp.openwifi.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import com.sensorstamp.openwifi.scan.NetworkKind
import com.sensorstamp.openwifi.ui.signalBars

/** What the dot colour on the map encodes. */
enum class ColorMode(val label: String) {
    SIGNAL("Signal"),
    BAND("Band"),
    KIND("Type"),
    RECENCY("Recency"),
}

/**
 * Palette for map marks. Deliberately separate from the Material theme: these
 * are data encodings that have to stay legible against both light and dark
 * basemap tiles, so they are fixed rather than theme-derived.
 */
object MapStyle {

    // Signal ramp — weak to strong.
    private val SIGNAL_RAMP = listOf(
        Color(0xFFE5484D), // 0 bars
        Color(0xFFF76B15), // 1
        Color(0xFFFFB454), // 2
        Color(0xFF6FD16F), // 3
        Color(0xFF00C7A8), // 4
    )

    private val BAND_COLORS = mapOf(
        "2.4 GHz" to Color(0xFFFFB454),
        "5 GHz" to Color(0xFF3D6BFF),
        "6 GHz" to Color(0xFFB37BFF),
        "60 GHz" to Color(0xFFFF5FA2),
    )

    private val KIND_COLORS = mapOf(
        NetworkKind.MUNICIPAL to Color(0xFF00C7A8),
        NetworkKind.VENUE to Color(0xFF3D6BFF),
        NetworkKind.CARRIER to Color(0xFFB37BFF),
        NetworkKind.TRANSIT to Color(0xFFFF5FA2),
        NetworkKind.RESIDENTIAL to Color(0xFFFFB454),
        NetworkKind.PERSONAL_HOTSPOT to Color(0xFF8A93A3),
        NetworkKind.DEVICE to Color(0xFF5A6270),
        NetworkKind.UNKNOWN to Color(0xFF9AA4B2),
    )

    private val RECENCY_RAMP = listOf(
        Color(0xFF00C7A8), // last hour
        Color(0xFF3D6BFF), // today
        Color(0xFFFFB454), // this week
        Color(0xFF8A93A3), // older
    )

    fun signalColor(rssi: Int): Color = SIGNAL_RAMP[signalBars(rssi).coerceIn(0, 4)]

    fun bandColor(band: String): Color = BAND_COLORS[band] ?: Color(0xFF9AA4B2)

    fun kindColor(kind: String): Color {
        val parsed = runCatching { NetworkKind.valueOf(kind) }.getOrDefault(NetworkKind.UNKNOWN)
        return KIND_COLORS[parsed] ?: Color(0xFF9AA4B2)
    }

    fun recencyColor(lastSeenAt: Long, now: Long): Color {
        val age = now - lastSeenAt
        return when {
            age < 3_600_000L -> RECENCY_RAMP[0]
            age < 86_400_000L -> RECENCY_RAMP[1]
            age < 604_800_000L -> RECENCY_RAMP[2]
            else -> RECENCY_RAMP[3]
        }
    }

    /** Legend entries for the active mode, in the order they should be shown. */
    fun legend(mode: ColorMode): List<Pair<String, Color>> = when (mode) {
        ColorMode.SIGNAL -> listOf(
            "Weak" to SIGNAL_RAMP[0],
            "Fair" to SIGNAL_RAMP[2],
            "Strong" to SIGNAL_RAMP[4],
        )
        ColorMode.BAND -> BAND_COLORS.entries.take(3).map { it.key to it.value }
        ColorMode.KIND -> listOf(
            NetworkKind.MUNICIPAL, NetworkKind.VENUE, NetworkKind.CARRIER,
            NetworkKind.TRANSIT, NetworkKind.RESIDENTIAL, NetworkKind.DEVICE,
        ).map { it.label to (KIND_COLORS[it] ?: Color.Gray) }
        ColorMode.RECENCY -> listOf(
            "Past hour" to RECENCY_RAMP[0],
            "Today" to RECENCY_RAMP[1],
            "This week" to RECENCY_RAMP[2],
            "Older" to RECENCY_RAMP[3],
        )
    }

    /**
     * Turns the standard OpenStreetMap raster into something that sits properly
     * under a dark UI: invert to darken land and water, then pull the saturation
     * back so the inverted colours do not fight the data marks drawn on top.
     */
    fun darkTileFilter(): ColorMatrixColorFilter {
        val invert = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        val desaturate = ColorMatrix().apply { setSaturation(0.45f) }
        // Slightly dim the result so the basemap recedes behind the points.
        val dim = ColorMatrix(
            floatArrayOf(
                0.86f, 0f, 0f, 0f, 0f,
                0f, 0.86f, 0f, 0f, 0f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        invert.postConcat(desaturate)
        invert.postConcat(dim)
        return ColorMatrixColorFilter(invert)
    }
}
