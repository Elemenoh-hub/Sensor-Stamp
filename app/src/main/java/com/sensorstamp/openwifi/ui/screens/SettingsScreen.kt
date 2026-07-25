package com.sensorstamp.openwifi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorstamp.openwifi.permissions.Requirement
import com.sensorstamp.openwifi.ui.ExportKind
import com.sensorstamp.openwifi.ui.MainViewModel
import com.sensorstamp.openwifi.ui.components.SectionHeader
import com.sensorstamp.openwifi.ui.formatInterval

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()

    var pendingExport by remember { mutableStateOf(ExportKind.NETWORKS) }
    var confirmClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.export(pendingExport, it) } }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshRequirements() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                start = 20.dp,
                end = 20.dp,
                bottom = 32.dp,
            )
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(22.dp))
        SectionHeader("Collection")
        Spacer(Modifier.height(10.dp))

        SettingsCard {
            SliderRow(
                title = "Scan interval",
                value = formatInterval(settings.scanIntervalSeconds),
                sliderValue = settings.scanIntervalSeconds.toFloat(),
                range = com.sensorstamp.openwifi.data.Settings.MIN_INTERVAL_SECONDS.toFloat()..
                    com.sensorstamp.openwifi.data.Settings.MAX_INTERVAL_SECONDS.toFloat(),
                onChange = { viewModel.setInterval(it.toInt()) },
            )
            Divider()
            SliderRow(
                title = "Minimum fix quality",
                value = if (settings.maxAccuracyMeters <= 0f) {
                    "Log everything"
                } else {
                    "±${settings.maxAccuracyMeters.toInt()} m or better"
                },
                sliderValue = settings.maxAccuracyMeters,
                range = 0f..200f,
                onChange = { viewModel.setMaxAccuracy(it) },
                caption = "Discards sightings taken with a vague location fix.",
            )
            Divider()
            SwitchRow(
                title = "Log new networks only",
                caption = "Records each access point once per session instead of every scan. " +
                    "Smaller database, less detail about coverage.",
                checked = settings.onlyLogNewNetworks,
                onChange = viewModel::setOnlyLogNew,
            )
            Divider()
            SwitchRow(
                title = "Include Enhanced Open (OWE)",
                caption = "Networks that need no password but encrypt each client's traffic.",
                checked = settings.includeOwe,
                onChange = viewModel::setIncludeOwe,
            )
            Divider()
            SwitchRow(
                title = "Include hidden networks",
                caption = "Access points that broadcast no name.",
                checked = settings.includeHidden,
                onChange = viewModel::setIncludeHidden,
            )
            Divider()
            SwitchRow(
                title = "Resume after restart",
                caption = "Starts collecting again after the phone reboots, if it was running.",
                checked = settings.autoStartOnBoot,
                onChange = viewModel::setAutoStartOnBoot,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Data")
        Spacer(Modifier.height(10.dp))

        SettingsCard {
            ActionRow(
                title = "Export access points",
                caption = "${totals.networks} rows · one per network, with its best fix",
                onClick = {
                    pendingExport = ExportKind.NETWORKS
                    exportLauncher.launch(ExportKind.NETWORKS.fileName)
                },
            )
            Divider()
            ActionRow(
                title = "Export every sighting",
                caption = "${totals.sightings} rows · the full trail for coverage analysis",
                onClick = {
                    pendingExport = ExportKind.SIGHTINGS
                    exportLauncher.launch(ExportKind.SIGHTINGS.fileName)
                },
            )
            Divider()
            ActionRow(
                title = "Clear the database",
                caption = "Deletes every network and sighting on this device",
                destructive = true,
                onClick = { confirmClear = true },
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Permissions")
        Spacer(Modifier.height(10.dp))

        SettingsCard {
            Requirement.onboardingOrder.forEachIndexed { index, requirement ->
                if (index > 0) Divider()
                PermissionRow(
                    requirement = requirement,
                    satisfied = requirements[requirement] == true,
                    onClick = { settingsLauncher.launch(requirement.settingsIntent(context)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "Sensor Stamp keeps every record on this device. Only you decide when " +
                "to export it. Wi-Fi data is collected passively — the app never connects " +
                "to any of the networks it finds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the database?") },
            text = {
                Text(
                    "This permanently deletes ${totals.networks} access points and " +
                        "${totals.sightings} sightings. Export first if you want to keep them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    confirmClear = false
                }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
            shape = MaterialTheme.shapes.large,
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    caption: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    caption: String? = null,
) {
    var draft by remember(sliderValue) { mutableStateOf(sliderValue) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft) },
            valueRange = range,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    caption: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    requirement: Requirement,
    satisfied: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (satisfied) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = if (satisfied) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = requirement.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (satisfied) "Granted" else "Not granted",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
