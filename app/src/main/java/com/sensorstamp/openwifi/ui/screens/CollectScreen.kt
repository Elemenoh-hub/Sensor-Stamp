package com.sensorstamp.openwifi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorstamp.openwifi.data.Settings
import com.sensorstamp.openwifi.permissions.Requirement
import com.sensorstamp.openwifi.ui.MainViewModel
import com.sensorstamp.openwifi.ui.components.Pill
import com.sensorstamp.openwifi.ui.components.PulseRings
import com.sensorstamp.openwifi.ui.components.SectionHeader
import com.sensorstamp.openwifi.ui.components.StatTile
import com.sensorstamp.openwifi.ui.formatClock
import com.sensorstamp.openwifi.ui.formatDuration
import com.sensorstamp.openwifi.ui.formatInterval
import kotlinx.coroutines.delay

/** Cadences offered as one-tap chips. Anything else is reachable with the slider. */
private val INTERVAL_PRESETS = listOf(5, 10, 30, 60, 300)

@Composable
fun CollectScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshRequirements() }

    // Drives the elapsed-time readout while a session is live.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.running) {
        while (status.running) {
            nowMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val warnings = Requirement.onboardingOrder.filter { !it.required && requirements[it] != true }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 28.dp,
            )
    ) {
        HeaderRow(running = status.running, visibleAccessPoints = status.visibleAccessPoints)

        Spacer(Modifier.height(18.dp))

        CollectButton(
            running = status.running,
            onToggle = { viewModel.toggleCollection() },
        )

        Spacer(Modifier.height(14.dp))

        StatusLine(
            running = status.running,
            message = status.message,
            lastScanAt = status.lastScanAt,
            intervalSeconds = settings.scanIntervalSeconds,
            hasFix = status.hasLocationFix,
            accuracyM = status.lastAccuracyM,
        )

        Spacer(Modifier.height(24.dp))

        SessionStats(
            running = status.running,
            elapsedMillis = if (status.running) nowMillis - status.sessionStartedAt else 0L,
            sessionNew = status.sessionNewNetworks,
            sessionSightings = status.sessionSightings,
            totalNetworks = totals.networks,
        )

        AnimatedVisibility(visible = warnings.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(16.dp))
                WarningCard(
                    warnings = warnings,
                    onFix = { settingsLauncher.launch(it.settingsIntent(context)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        IntervalCard(
            settings = settings,
            onIntervalChange = viewModel::setInterval,
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            text = "Latest finds",
            trailing = {
                Text(
                    text = "${totals.networks} total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Spacer(Modifier.height(10.dp))

        if (recent.isEmpty()) {
            EmptyFeed(running = status.running)
        } else {
            recent.take(6).forEach { network ->
                NetworkRow(network = network, now = nowMillis)
                Spacer(Modifier.height(8.dp))
            }
            if (recent.size > 6) {
                Text(
                    text = "Open the Networks tab to see all ${totals.networks}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderRow(running: Boolean, visibleAccessPoints: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Sensor Stamp",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Open Wi-Fi collector",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (running) {
            Pill(
                text = if (visibleAccessPoints > 0) "$visibleAccessPoints APs in range" else "Live",
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun CollectButton(running: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (running) 1f else 0.97f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "buttonScale",
    )
    val container by animateColorAsState(
        targetValue = if (running) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        label = "buttonColor",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(232.dp),
        contentAlignment = Alignment.Center,
    ) {
        PulseRings(active = running)

        Box(
            Modifier
                .size(168.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            container,
                            container.copy(alpha = 0.72f),
                        )
                    )
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (running) "Stop collecting" else "Start collecting",
                    modifier = Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (running) "STOP" else "START",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(
    running: Boolean,
    message: String?,
    lastScanAt: Long,
    intervalSeconds: Int,
    hasFix: Boolean,
    accuracyM: Float,
) {
    val primary = when {
        message != null -> message
        running && lastScanAt > 0 -> "Last scan ${formatClock(lastScanAt)}"
        running -> "Starting up…"
        else -> "Tap start to begin collecting"
    }

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = primary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message != null) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
        if (running) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(text = "every ${formatInterval(intervalSeconds)}")
                Pill(
                    text = if (hasFix) "fix ±${accuracyM.toInt()} m" else "no fix yet",
                    container = if (hasFix) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                    content = if (hasFix) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                )
            }
        }
    }
}

@Composable
private fun SessionStats(
    running: Boolean,
    elapsedMillis: Long,
    sessionNew: Int,
    sessionSightings: Int,
    totalNetworks: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(
            value = if (running) formatDuration(elapsedMillis) else "—",
            label = "This session",
            modifier = Modifier.weight(1f),
        )
        StatTile(
            value = sessionNew.toString(),
            label = "New networks",
            modifier = Modifier.weight(1f),
            accent = MaterialTheme.colorScheme.secondary,
        )
        StatTile(
            value = if (running) sessionSightings.toString() else totalNetworks.toString(),
            label = if (running) "Sightings" else "Catalogued",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WarningCard(
    warnings: List<Requirement>,
    onFix: (Requirement) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Collection may be interrupted",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(6.dp))
            warnings.forEach { warning ->
                Text(
                    text = "· ${warning.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFix(warning) }
                        .padding(vertical = 4.dp),
                )
            }
            Text(
                text = "Tap an item to fix it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun IntervalCard(
    settings: Settings,
    onIntervalChange: (Int) -> Unit,
) {
    // Local mirror so dragging feels immediate; committed on release.
    var draft by remember(settings.scanIntervalSeconds) {
        mutableStateOf(settings.scanIntervalSeconds.toFloat())
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        text = "Scan interval",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = batteryHint(draft.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatInterval(draft.toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = { onIntervalChange(draft.toInt()) },
                valueRange = Settings.MIN_INTERVAL_SECONDS.toFloat()..
                    Settings.MAX_INTERVAL_SECONDS.toFloat(),
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    thumbColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTERVAL_PRESETS.forEach { preset ->
                    val selected = settings.scanIntervalSeconds == preset
                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable {
                                draft = preset.toFloat()
                                onIntervalChange(preset)
                            },
                    ) {
                        Text(
                            text = formatInterval(preset),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Android rate-limits apps to about four Wi-Fi scans every two minutes. " +
                    "Below that, Sensor Stamp keeps logging from the system's own scans instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun batteryHint(seconds: Int): String = when {
    seconds <= 10 -> "Highest resolution · heaviest on battery"
    seconds <= 30 -> "Good for walking and city driving"
    seconds <= 120 -> "Balanced · fine for long trips"
    else -> "Lightest on battery · coarse coverage"
}

@Composable
private fun EmptyFeed(running: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (running) "Listening…" else "Nothing logged yet",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (running) {
                    "Open networks will appear here as you pass them."
                } else {
                    "Start collecting, then go for a walk or a drive."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
