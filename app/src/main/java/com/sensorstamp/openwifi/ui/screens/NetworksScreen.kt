package com.sensorstamp.openwifi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorstamp.openwifi.data.db.NetworkEntity
import com.sensorstamp.openwifi.scan.NetworkKind
import com.sensorstamp.openwifi.scan.RadioMath
import com.sensorstamp.openwifi.scan.WifiSecurity
import com.sensorstamp.openwifi.ui.MainViewModel
import com.sensorstamp.openwifi.ui.components.Pill
import com.sensorstamp.openwifi.ui.components.SignalBars
import com.sensorstamp.openwifi.ui.formatCoordinates
import com.sensorstamp.openwifi.ui.formatDateTime
import com.sensorstamp.openwifi.ui.formatRelative
import com.sensorstamp.openwifi.ui.theme.MonoStyle

@Composable
fun NetworksScreen(viewModel: MainViewModel) {
    val networks by viewModel.networks.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
            )
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Networks",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${totals.networks} access points · ${totals.sightings} sightings · " +
                    "${totals.uniqueSsids} distinct names",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search name or MAC address") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }

        Spacer(Modifier.height(12.dp))

        if (networks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = if (query.isBlank()) {
                        "No open networks logged yet."
                    } else {
                        "Nothing matches \"$query\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 48.dp, start = 32.dp, end = 32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(networks, key = { it.id }) { network ->
                    NetworkRow(network = network, now = now, expandable = true)
                }
            }
        }
    }
}

/**
 * One access point. Collapsed it shows the essentials; tapping reveals the full
 * record so the granular fields are visible without an extra screen.
 */
@Composable
fun NetworkRow(
    network: NetworkEntity,
    now: Long,
    expandable: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expandable) Modifier.clickable { expanded = !expanded } else Modifier
            ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = network.ssid.ifBlank { "(hidden network)" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = formatCoordinates(network.latitude, network.longitude),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.size(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    SignalBars(rssi = network.bestRssi)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRelative(network.lastSeenAt, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(
                    text = securityLabel(network.securityType),
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.secondary,
                )
                Pill(text = kindOf(network.networkKind).label)
                Pill(text = network.band)
                Pill(text = "${network.bestRssi} dBm")
                network.vendor?.let { Pill(text = it) }
                if (network.sightingCount > 1) {
                    Pill(text = "×${network.sightingCount}")
                }
                if (network.isLikelyMobile) {
                    Pill(
                        text = "Moves",
                        container = MaterialTheme.colorScheme.tertiaryContainer,
                        content = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    DetailRow("BSSID", network.bssid, mono = true)
                    network.vendor?.let { DetailRow("Vendor", it) }
                    DetailRow("Classified as", kindOf(network.networkKind).label)
                    DetailRow("Channel", "${network.channel} · ${network.frequencyMhz} MHz")
                    if (network.channelWidthMhz > 0) {
                        DetailRow("Width", "${network.channelWidthMhz} MHz")
                    }
                    if (network.wifiStandard != 0) {
                        DetailRow("Standard", RadioMath.wifiStandardLabel(network.wifiStandard))
                    }
                    if (network.supportsFtm) DetailRow("Ranging", "802.11mc capable")
                    DetailRow("Signal range", "${network.worstRssi} to ${network.bestRssi} dBm")
                    DetailRow(
                        "Best fix",
                        formatCoordinates(network.latitude, network.longitude),
                        mono = true,
                    )
                    DetailRow(
                        "Weighted estimate",
                        formatCoordinates(network.estimatedLatitude, network.estimatedLongitude),
                        mono = true,
                    )
                    DetailRow("Fix accuracy", "±${network.accuracyM.toInt()} m")
                    if (network.coverageRadiusM > 0f) {
                        DetailRow("Heard across", RadioMath.formatMeters(network.coverageRadiusM))
                    }
                    DetailRow(
                        "Est. distance at peak",
                        RadioMath.formatMeters(network.estimatedDistanceAtBestM),
                    )
                    if (network.altitudeM != 0.0) {
                        DetailRow("Altitude", "${network.altitudeM.toInt()} m")
                    }
                    DetailRow("Provider", network.locationProvider)
                    DetailRow("First seen", formatDateTime(network.firstSeenAt))
                    DetailRow("Last seen", formatDateTime(network.lastSeenAt))
                    DetailRow("Sessions", network.sessionCount.toString())
                    network.venueHint?.let { DetailRow("Venue", it) }
                    DetailRow("Capabilities", network.capabilities, mono = true)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(18.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = value,
            style = if (mono) MonoStyle else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private fun securityLabel(securityType: String): String =
    runCatching { WifiSecurity.valueOf(securityType).label }.getOrDefault("Open")

private fun kindOf(networkKind: String): NetworkKind =
    runCatching { NetworkKind.valueOf(networkKind) }.getOrDefault(NetworkKind.UNKNOWN)
