package com.sensorstamp.openwifi.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sensorstamp.openwifi.data.db.MapPoint
import com.sensorstamp.openwifi.scan.NetworkKind
import com.sensorstamp.openwifi.scan.RadioMath
import com.sensorstamp.openwifi.ui.MainViewModel
import com.sensorstamp.openwifi.ui.components.Pill
import com.sensorstamp.openwifi.ui.components.SignalBars
import com.sensorstamp.openwifi.ui.formatCoordinates
import com.sensorstamp.openwifi.ui.formatRelative
import com.sensorstamp.openwifi.ui.map.ColorMode
import com.sensorstamp.openwifi.ui.map.MapStyle
import com.sensorstamp.openwifi.ui.map.NetworkOverlay
import com.sensorstamp.openwifi.ui.theme.MonoStyle
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val darkTheme = isSystemInDarkTheme()

    val allPoints by viewModel.mapPoints.collectAsStateWithLifecycle()
    val colorMode by viewModel.colorMode.collectAsStateWithLifecycle()
    val hideUnusable by viewModel.hideUnusableOnMap.collectAsStateWithLifecycle()
    val selected by viewModel.selectedMapPoint.collectAsStateWithLifecycle()

    val points = remember(allPoints, hideUnusable) {
        if (hideUnusable) allPoints.filter { it.isUsefulForBrowsing } else allPoints
    }

    val now = remember(points) { System.currentTimeMillis() }

    val myLocation = remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    val overlay = remember {
        NetworkOverlay(density = density) { viewModel.selectMapPoint(it) }
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasFramedData by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> createMapView(ctx, overlay, myLocation, viewModel) },
            update = { map ->
                mapView = map
                overlay.points = points
                overlay.colorMode = colorMode
                overlay.selectedBssid = selected?.bssid
                overlay.now = now
                overlay.darkBasemap = darkTheme
                map.overlayManager.tilesOverlay.setColorFilter(
                    if (darkTheme) MapStyle.darkTileFilter() else null
                )
                map.invalidate()
            },
            onRelease = { map ->
                // Remember where the user was so switching tabs does not reset
                // the camera back to the whole dataset.
                viewModel.rememberCamera(
                    map.mapCenter.latitude,
                    map.mapCenter.longitude,
                    map.zoomLevelDouble,
                )
                map.onDetach()
            },
        )

        // Frame the collected data the first time there is something to frame.
        LaunchedEffect(points.isNotEmpty(), mapView) {
            val map = mapView ?: return@LaunchedEffect
            if (hasFramedData || points.isEmpty()) return@LaunchedEffect
            val saved = viewModel.savedCamera
            if (saved != null) {
                map.controller.setZoom(saved.third)
                map.controller.setCenter(GeoPoint(saved.first, saved.second))
            } else {
                map.zoomToBoundingBox(boundsOf(points), false, 96)
            }
            hasFramedData = true
        }

        MapLifecycle(mapView)

        TopControls(
            colorMode = colorMode,
            onColorMode = viewModel::setColorMode,
            hideUnusable = hideUnusable,
            onToggleHideUnusable = viewModel::setHideUnusableOnMap,
            shown = points.size,
            total = allPoints.size,
        )

        MapButtons(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (selected != null) 220.dp else 28.dp),
            onRecenter = {
                myLocation.value?.myLocation?.let { fix ->
                    mapView?.controller?.animateTo(fix)
                    mapView?.controller?.setZoom(17.5)
                }
            },
            onFitAll = {
                if (points.isNotEmpty()) {
                    mapView?.zoomToBoundingBox(boundsOf(points), true, 96)
                }
            },
        )

        if (allPoints.isEmpty()) {
            EmptyMapNotice()
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            selected?.let { point ->
                SelectedNetworkCard(
                    point = point,
                    now = now,
                    onDismiss = { viewModel.selectMapPoint(null) },
                )
            }
        }
    }
}

private val MapPoint.isUsefulForBrowsing: Boolean
    get() = runCatching { NetworkKind.valueOf(networkKind) }
        .getOrDefault(NetworkKind.UNKNOWN)
        .usefulForBrowsing

private fun boundsOf(points: List<MapPoint>): BoundingBox {
    val north = points.maxOf { it.latitude }
    val south = points.minOf { it.latitude }
    val east = points.maxOf { it.longitude }
    val west = points.minOf { it.longitude }
    // A single point has zero span, which osmdroid cannot zoom to; pad it out.
    val padLat = ((north - south) * 0.15).coerceAtLeast(0.0015)
    val padLon = ((east - west) * 0.15).coerceAtLeast(0.0015)
    return BoundingBox(north + padLat, east + padLon, south - padLat, west - padLon)
}

private fun createMapView(
    context: Context,
    overlay: NetworkOverlay,
    myLocation: androidx.compose.runtime.MutableState<MyLocationNewOverlay?>,
    viewModel: MainViewModel,
): MapView = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    setMultiTouchControls(true)
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    isTilesScaledToDpi = true
    setUseDataConnection(true)
    minZoomLevel = 3.0
    maxZoomLevel = 20.0

    val saved = viewModel.savedCamera
    controller.setZoom(saved?.third ?: 4.0)
    controller.setCenter(GeoPoint(saved?.first ?: 20.0, saved?.second ?: 0.0))

    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this).apply {
        enableMyLocation()
    }
    myLocation.value = locationOverlay

    overlays.add(overlay)
    overlays.add(locationOverlay)
}

/** osmdroid's MapView needs explicit resume/pause to start and stop tile fetching. */
@Composable
private fun MapLifecycle(mapView: MapView?) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun TopControls(
    colorMode: ColorMode,
    onColorMode: (ColorMode) -> Unit,
    hideUnusable: Boolean,
    onToggleHideUnusable: (Boolean) -> Unit,
    shown: Int,
    total: Int,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp,
            )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorMode.entries.forEach { mode ->
                MapChip(
                    text = mode.label,
                    selected = colorMode == mode,
                    onClick = { onColorMode(mode) },
                )
            }
            MapChip(
                text = if (hideUnusable) "Browsable only" else "All types",
                selected = hideUnusable,
                icon = Icons.Filled.FilterAlt,
                onClick = { onToggleHideUnusable(!hideUnusable) },
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapStyle.legend(colorMode).forEach { (label, color) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (shown != total) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Showing $shown of $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
    }
}

@Composable
private fun MapChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        shadowElevation = 2.dp,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun MapButtons(
    modifier: Modifier,
    onRecenter: () -> Unit,
    onFitAll: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MapIconButton(Icons.Filled.ZoomOutMap, "Fit all networks", onFitAll)
        MapIconButton(Icons.Filled.MyLocation, "Centre on me", onRecenter)
    }
}

@Composable
private fun MapIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun EmptyMapNotice() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 4.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Nothing to map yet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Start collecting and open networks will drop onto the map\n" +
                        "as you pass them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectedNetworkCard(
    point: MapPoint,
    now: Long,
    onDismiss: () -> Unit,
) {
    val kind = runCatching { NetworkKind.valueOf(point.networkKind) }
        .getOrDefault(NetworkKind.UNKNOWN)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = point.ssid.ifBlank { "(hidden network)" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatCoordinates(point.latitude, point.longitude),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    SignalBars(rssi = point.bestRssi)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${point.bestRssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(
                    text = kind.label,
                    container = MapStyle.kindColor(point.networkKind).copy(alpha = 0.18f),
                    content = MaterialTheme.colorScheme.onSurface,
                )
                Pill(text = point.band)
                point.vendor?.let { Pill(text = it) }
                if (point.sightingCount > 1) Pill(text = "${point.sightingCount} sightings")
                if (point.coverageRadiusM > 0f) {
                    Pill(text = "~${RadioMath.formatMeters(point.coverageRadiusM)} spread")
                }
                if (point.isLikelyMobile) {
                    Pill(
                        text = "Moves",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = point.bssid,
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "seen ${formatRelative(point.lastSeenAt, now)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!kind.usefulForBrowsing) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (kind) {
                        NetworkKind.DEVICE ->
                            "Looks like a printer, TV or similar — open, but almost " +
                                "certainly not an internet connection."
                        NetworkKind.PERSONAL_HOTSPOT ->
                            "Looks like somebody's phone hotspot — unlikely to still " +
                                "be here tomorrow."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}
