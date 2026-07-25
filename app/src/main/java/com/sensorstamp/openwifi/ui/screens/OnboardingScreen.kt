package com.sensorstamp.openwifi.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sensorstamp.openwifi.permissions.GrantStyle
import com.sensorstamp.openwifi.permissions.Requirement
import com.sensorstamp.openwifi.ui.MainViewModel

@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val requirements by viewModel.requirements.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshRequirements() }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshRequirements() }

    val ordered = Requirement.onboardingOrder
    val satisfiedCount = ordered.count { requirements[it] == true }
    val blockers = ordered.filter { it.required && requirements[it] != true }
    val readyToStart = blockers.isEmpty()

    fun grant(requirement: Requirement) {
        when (requirement.effectiveGrantStyle) {
            GrantStyle.RUNTIME_DIALOG -> {
                val permissions = when (requirement) {
                    Requirement.LOCATION -> arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    else -> listOfNotNull(requirement.permission).toTypedArray()
                }
                if (permissions.isEmpty()) {
                    viewModel.refreshRequirements()
                } else {
                    permissionLauncher.launch(permissions)
                }
            }
            GrantStyle.SYSTEM_SCREEN ->
                settingsLauncher.launch(requirement.settingsIntent(context))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 140.dp,
                    start = 20.dp,
                    end = 20.dp,
                )
        ) {
            AppMark()
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Map the open Wi-Fi\naround you",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Sensor Stamp watches for open, password-free networks while you " +
                    "travel and records exactly where each one was reachable.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            SetupProgress(satisfied = satisfiedCount, total = ordered.size)

            Spacer(Modifier.height(20.dp))

            ordered.forEach { requirement ->
                RequirementCard(
                    requirement = requirement,
                    satisfied = requirements[requirement] == true,
                    onGrant = { grant(requirement) },
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Everything stays on this device. Nothing is uploaded anywhere — " +
                    "export the database yourself when you want it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Sticky bottom action so the primary path is always one tap away.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 3.dp,
        ) {
            Column(
                Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding() + 16.dp,
                )
            ) {
                Button(
                    onClick = { viewModel.completeOnboarding() },
                    enabled = readyToStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        text = if (readyToStart) "Continue" else "Grant the essentials first",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                AnimatedVisibility(visible = readyToStart && satisfiedCount < ordered.size) {
                    TextButton(
                        onClick = { viewModel.completeOnboarding() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Some optional steps are still off — collection may pause in the background",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        Modifier
            .size(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Radar,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun SetupProgress(satisfied: Int, total: Int) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Setup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$satisfied of $total ready",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else satisfied.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun RequirementCard(
    requirement: Requirement,
    satisfied: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (satisfied) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (satisfied) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (satisfied) Icons.Filled.CheckCircle else requirement.icon,
                    contentDescription = null,
                    tint = if (satisfied) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.size(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = requirement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!requirement.required) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "· recommended",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = requirement.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!satisfied) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onGrant,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 18.dp,
                            vertical = 8.dp,
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(
                            text = when (requirement.effectiveGrantStyle) {
                                GrantStyle.RUNTIME_DIALOG -> "Allow"
                                GrantStyle.SYSTEM_SCREEN -> "Open settings"
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private val Requirement.icon: ImageVector
    get() = when (this) {
        Requirement.LOCATION -> Icons.Filled.MyLocation
        Requirement.BACKGROUND_LOCATION -> Icons.Filled.LocationOn
        Requirement.NEARBY_WIFI -> Icons.Filled.Radar
        Requirement.NOTIFICATIONS -> Icons.Filled.Notifications
        Requirement.BATTERY_UNRESTRICTED -> Icons.Filled.BatteryChargingFull
        Requirement.LOCATION_SERVICES_ON -> Icons.Filled.LocationOn
        Requirement.WIFI_ON -> Icons.Filled.Wifi
    }
