package com.sensorstamp.openwifi.ui.map

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.ui.graphics.toArgb
import com.sensorstamp.openwifi.data.db.MapPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Draws every collected access point directly onto the map canvas.
 *
 * Deliberately not built from osmdroid `Marker` objects: a few thousand markers
 * each with their own drawable and hit-test is enough to make panning stutter,
 * whereas one overlay drawing primitive circles stays smooth. The trade-off is
 * that hit-testing and clustering are ours to implement, which is what the rest
 * of this class is.
 */
class NetworkOverlay(
    private val density: Float,
    private val onSelect: (MapPoint?) -> Unit,
) : Overlay() {

    var points: List<MapPoint> = emptyList()
    var colorMode: ColorMode = ColorMode.SIGNAL
    var selectedBssid: String? = null
    var now: Long = System.currentTimeMillis()
    var darkBasemap: Boolean = false

    /** Below this zoom, nearby points are merged into count bubbles. */
    private val clusterZoomCeiling = 16.0

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val clusterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 11f * density
    }

    /** Screen positions from the last draw, reused for hit-testing on tap. */
    private var lastDrawn: List<Pair<Point, MapPoint>> = emptyList()
    private val reusablePoint = Point()

    override fun draw(canvas: Canvas, projection: Projection) {
        if (points.isEmpty()) {
            lastDrawn = emptyList()
            return
        }

        val zoom = projection.zoomLevel
        val bounds = projection.boundingBox
        // Small margin so marks just off-screen still animate in smoothly.
        val latMargin = bounds.latitudeSpan * 0.1
        val lonMargin = bounds.longitudeSpanWithDateLine * 0.1

        // When the view straddles the antimeridian, west is numerically greater
        // than east and the range check inverts — draw everything rather than
        // silently dropping every point.
        val crossesDateLine = bounds.lonWest > bounds.lonEast

        val visible = points.filter {
            val inLatitude =
                it.latitude in (bounds.latSouth - latMargin)..(bounds.latNorth + latMargin)
            val inLongitude = crossesDateLine ||
                it.longitude in (bounds.lonWest - lonMargin)..(bounds.lonEast + lonMargin)
            inLatitude && inLongitude
        }
        if (visible.isEmpty()) {
            lastDrawn = emptyList()
            return
        }

        val projected = ArrayList<Pair<Point, MapPoint>>(visible.size)
        visible.forEach { point ->
            val screen = projection.toPixels(GeoPoint(point.latitude, point.longitude), null)
            projected += screen to point
        }
        lastDrawn = projected

        if (zoom < clusterZoomCeiling) {
            drawClustered(canvas, projected)
        } else {
            projected.forEach { (screen, point) -> drawPoint(canvas, screen, point) }
        }

        // The selection always draws last so it is never hidden behind a neighbour.
        selectedBssid?.let { selected ->
            projected.firstOrNull { it.second.bssid == selected }
                ?.let { (screen, point) -> drawSelection(canvas, screen, point) }
        }
    }

    private fun drawClustered(canvas: Canvas, projected: List<Pair<Point, MapPoint>>) {
        val cellPx = (44 * density).roundToInt().coerceAtLeast(1)
        val cells = HashMap<Long, MutableList<Pair<Point, MapPoint>>>()

        projected.forEach { entry ->
            val (screen, _) = entry
            // Points just off-screen have negative pixel coordinates, so the cell
            // indices are biased positive before packing to keep the key unique.
            val xCell = (screen.x / cellPx).toLong() + CELL_BIAS
            val yCell = (screen.y / cellPx).toLong() + CELL_BIAS
            cells.getOrPut(xCell * CELL_STRIDE + yCell) { mutableListOf() }.add(entry)
        }

        cells.values.forEach { members ->
            if (members.size == 1) {
                val (screen, point) = members.first()
                drawPoint(canvas, screen, point)
            } else {
                drawCluster(canvas, members)
            }
        }
    }

    private fun drawCluster(canvas: Canvas, members: List<Pair<Point, MapPoint>>) {
        val centreX = members.sumOf { it.first.x } / members.size
        val centreY = members.sumOf { it.first.y } / members.size

        // Bubble grows with count, but logarithmically — a cluster of 500 should
        // read as "many", not swallow the screen.
        val radius = (11f + 5f * kotlin.math.ln(members.size.toFloat())) * density

        // Colour the bubble by its strongest member so a cluster still carries a
        // hint of what is inside it.
        val representative = members.maxByOrNull { it.second.bestRssi }!!.second
        val color = colorFor(representative).toArgb()

        haloPaint.color = withAlpha(color, 0.28f)
        canvas.drawCircle(centreX.toFloat(), centreY.toFloat(), radius * 1.45f, haloPaint)

        fillPaint.color = color
        canvas.drawCircle(centreX.toFloat(), centreY.toFloat(), radius, fillPaint)

        strokePaint.color = if (darkBasemap) AndroidColor.WHITE else AndroidColor.BLACK
        strokePaint.alpha = 60
        canvas.drawCircle(centreX.toFloat(), centreY.toFloat(), radius, strokePaint)

        clusterTextPaint.color = contrastingTextColor(color)
        val label = if (members.size > 999) "999+" else members.size.toString()
        canvas.drawText(
            label,
            centreX.toFloat(),
            centreY + clusterTextPaint.textSize / 3f,
            clusterTextPaint,
        )
    }

    private fun drawPoint(canvas: Canvas, screen: Point, point: MapPoint) {
        val color = colorFor(point).toArgb()
        // Repeatedly-seen access points read as more substantial, which is a fair
        // proxy for confidence in the position.
        val radius = (4.5f + (point.sightingCount.coerceAtMost(24) / 24f) * 3.5f) * density

        haloPaint.color = withAlpha(color, 0.22f)
        canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius * 2.1f, haloPaint)

        fillPaint.color = color
        canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius, fillPaint)

        // Access points heard from implausibly far apart are drawn hollow, so a
        // bus or a pocket hotspot is visibly not a fixed location.
        if (point.isLikelyMobile) {
            strokePaint.color = if (darkBasemap) AndroidColor.WHITE else AndroidColor.BLACK
            strokePaint.alpha = 200
            canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius + 2f * density, strokePaint)
        }
    }

    private fun drawSelection(canvas: Canvas, screen: Point, point: MapPoint) {
        val color = colorFor(point).toArgb()
        val radius = 13f * density

        haloPaint.color = withAlpha(color, 0.30f)
        canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius * 2f, haloPaint)

        fillPaint.color = color
        canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius * 0.55f, fillPaint)

        strokePaint.color = if (darkBasemap) AndroidColor.WHITE else AndroidColor.BLACK
        strokePaint.alpha = 255
        strokePaint.strokeWidth = 2.5f * density
        canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius, strokePaint)
        strokePaint.strokeWidth = 1.5f * density
    }

    private fun colorFor(point: MapPoint) = when (colorMode) {
        ColorMode.SIGNAL -> MapStyle.signalColor(point.bestRssi)
        ColorMode.BAND -> MapStyle.bandColor(point.band)
        ColorMode.KIND -> MapStyle.kindColor(point.networkKind)
        ColorMode.RECENCY -> MapStyle.recencyColor(point.lastSeenAt, now)
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val touchRadius = 28f * density
        var best: MapPoint? = null
        var bestDistance = Float.MAX_VALUE

        lastDrawn.forEach { (screen, point) ->
            val distance = hypot(event.x - screen.x.toFloat(), event.y - screen.y.toFloat())
            if (distance < touchRadius && distance < bestDistance) {
                bestDistance = distance
                best = point
            }
        }

        // A tap on empty map dismisses the current selection rather than doing
        // nothing, which is what people expect from a map.
        return if (best != null) {
            onSelect(best)
            true
        } else if (selectedBssid != null) {
            onSelect(null)
            true
        } else {
            false
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        AndroidColor.argb(
            (alpha * 255).roundToInt().coerceIn(0, 255),
            AndroidColor.red(color),
            AndroidColor.green(color),
            AndroidColor.blue(color),
        )

    private companion object {
        /** Keeps off-screen (negative) cell indices positive before key packing. */
        const val CELL_BIAS = 1_000_000L
        const val CELL_STRIDE = 4_000_000L
    }

    /** Black or white text, whichever stays readable on the given bubble colour. */
    private fun contrastingTextColor(color: Int): Int {
        val luminance = (0.299 * AndroidColor.red(color) +
            0.587 * AndroidColor.green(color) +
            0.114 * AndroidColor.blue(color)) / 255.0
        return if (luminance > 0.6) AndroidColor.BLACK else AndroidColor.WHITE
    }
}
