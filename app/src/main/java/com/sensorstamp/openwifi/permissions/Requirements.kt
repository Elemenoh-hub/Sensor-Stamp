package com.sensorstamp.openwifi.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * How the user grants a given requirement. Runtime permissions go through the
 * normal dialog; the rest need us to hand the user off to a system screen.
 */
enum class GrantStyle { RUNTIME_DIALOG, SYSTEM_SCREEN }

enum class Requirement(
    val title: String,
    val rationale: String,
    val grantStyle: GrantStyle,
    /**
     * True only for the things without which scanning cannot work at all. Everything
     * else is strongly recommended and surfaced as a warning, but never traps the
     * user on the setup screen — a toggle they flip later shouldn't lock them out.
     */
    val required: Boolean,
) {
    LOCATION(
        title = "Precise location",
        rationale = "Every network we find is stamped with where you were standing. " +
            "Without a precise fix, Android also refuses to hand out Wi-Fi scan results at all.",
        grantStyle = GrantStyle.RUNTIME_DIALOG,
        required = true,
    ),
    BACKGROUND_LOCATION(
        title = "Location while in background",
        rationale = "Lets collection keep running with your screen off and the app closed — " +
            "the whole point of mapping while you travel. Choose \"Allow all the time\".",
        grantStyle = GrantStyle.SYSTEM_SCREEN,
        required = false,
    ),
    NEARBY_WIFI(
        title = "Nearby Wi-Fi devices",
        rationale = "Android 13 and newer require this to return scan results.",
        grantStyle = GrantStyle.RUNTIME_DIALOG,
        required = true,
    ),
    NOTIFICATIONS(
        title = "Notifications",
        rationale = "The collector runs as a foreground service, which Android only allows " +
            "with a visible notification. It also shows your live find count.",
        grantStyle = GrantStyle.RUNTIME_DIALOG,
        required = false,
    ),
    BATTERY_UNRESTRICTED(
        title = "Unrestricted battery use",
        rationale = "Stops Android's battery optimiser from freezing the collector after " +
            "a few minutes in your pocket.",
        grantStyle = GrantStyle.SYSTEM_SCREEN,
        required = false,
    ),
    LOCATION_SERVICES_ON(
        title = "Location turned on",
        rationale = "The device-wide location toggle has to be on for any fix to arrive.",
        grantStyle = GrantStyle.SYSTEM_SCREEN,
        required = false,
    ),
    WIFI_ON(
        title = "Wi-Fi turned on",
        rationale = "The radio has to be on to scan. You do not need to be connected to anything.",
        grantStyle = GrantStyle.SYSTEM_SCREEN,
        required = false,
    );

    /** Whether this requirement applies at all on the current OS version. */
    val applies: Boolean
        get() = when (this) {
            NEARBY_WIFI, NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            else -> true
        }

    /**
     * How to actually ask, on this OS version. Android 11 removed the inline
     * dialog for background location and demands a trip to app settings, but on
     * Android 10 the dialog still works — so ask the nicer way where we can.
     */
    val effectiveGrantStyle: GrantStyle
        get() = if (this == BACKGROUND_LOCATION && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            GrantStyle.RUNTIME_DIALOG
        } else {
            grantStyle
        }

    /** The runtime permission string, when there is one. */
    val permission: String?
        get() = when (this) {
            LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            BACKGROUND_LOCATION -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
            NEARBY_WIFI ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.NEARBY_WIFI_DEVICES
                } else null
            NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null
            else -> null
        }

    fun isSatisfied(context: Context): Boolean = when (this) {
        LOCATION -> context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        // Below API 29 there is no separate background grant: foreground location covers it.
        BACKGROUND_LOCATION ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        NEARBY_WIFI -> permission?.let { context.hasPermission(it) } ?: true
        NOTIFICATIONS -> permission?.let { context.hasPermission(it) } ?: true

        BATTERY_UNRESTRICTED -> {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        LOCATION_SERVICES_ON -> {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.isLocationEnabled(lm)
        }

        WIFI_ON -> {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.isWifiEnabled
        }
    }

    /** The system screen to send the user to, for requirements we cannot ask for inline. */
    fun settingsIntent(context: Context): Intent = when (this) {
        BATTERY_UNRESTRICTED ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.fromParts("package", context.packageName, null))

        LOCATION_SERVICES_ON -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

        WIFI_ON ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Apps can no longer flip the radio themselves; this opens the
                // system Wi-Fi panel instead.
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }

        else -> appDetailsIntent(context)
    }

    companion object {
        fun appDetailsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))

        val onboardingOrder: List<Requirement>
            get() = listOf(
                LOCATION_SERVICES_ON,
                WIFI_ON,
                LOCATION,
                BACKGROUND_LOCATION,
                NEARBY_WIFI,
                NOTIFICATIONS,
                BATTERY_UNRESTRICTED,
            ).filter { it.applies }

        /** The subset that must be satisfied before collection can legally start. */
        fun blockers(context: Context): List<Requirement> =
            onboardingOrder.filter { it.required && !it.isSatisfied(context) }
    }
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
