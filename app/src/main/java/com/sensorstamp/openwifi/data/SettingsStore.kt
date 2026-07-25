package com.sensorstamp.openwifi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** How often we ask the radio for a fresh scan, in seconds. */
    val scanIntervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    /** Skip logging a repeat sighting of an AP we have already catalogued. */
    val onlyLogNewNetworks: Boolean = false,
    /** Discard sightings whose location fix is vaguer than this, in metres. 0 = keep everything. */
    val maxAccuracyMeters: Float = 0f,
    /** Include Enhanced Open (OWE) networks, which need no password but are encrypted. */
    val includeOwe: Boolean = true,
    /** Include networks that broadcast no SSID. */
    val includeHidden: Boolean = false,
    /** Restart collection automatically after a reboot. */
    val autoStartOnBoot: Boolean = false,
    /** True once collection has been started at least once — used to resume after reboot. */
    val wasRunning: Boolean = false,
    /** True once the user has completed the permission walkthrough. */
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 30
        const val MIN_INTERVAL_SECONDS = 5
        const val MAX_INTERVAL_SECONDS = 600
    }
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val interval = intPreferencesKey("scan_interval_seconds")
        val onlyNew = booleanPreferencesKey("only_log_new")
        val maxAccuracy = floatPreferencesKey("max_accuracy_m")
        val includeOwe = booleanPreferencesKey("include_owe")
        val includeHidden = booleanPreferencesKey("include_hidden")
        val autoStart = booleanPreferencesKey("auto_start_on_boot")
        val wasRunning = booleanPreferencesKey("was_running")
        val onboarding = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            scanIntervalSeconds = p[Keys.interval] ?: Settings.DEFAULT_INTERVAL_SECONDS,
            onlyLogNewNetworks = p[Keys.onlyNew] ?: false,
            maxAccuracyMeters = p[Keys.maxAccuracy] ?: 0f,
            includeOwe = p[Keys.includeOwe] ?: true,
            includeHidden = p[Keys.includeHidden] ?: false,
            autoStartOnBoot = p[Keys.autoStart] ?: false,
            wasRunning = p[Keys.wasRunning] ?: false,
            onboardingComplete = p[Keys.onboarding] ?: false,
        )
    }

    suspend fun setInterval(seconds: Int) = edit {
        it[Keys.interval] = seconds.coerceIn(
            Settings.MIN_INTERVAL_SECONDS,
            Settings.MAX_INTERVAL_SECONDS,
        )
    }

    suspend fun setOnlyLogNew(value: Boolean) = edit { it[Keys.onlyNew] = value }
    suspend fun setMaxAccuracy(meters: Float) = edit { it[Keys.maxAccuracy] = meters }
    suspend fun setIncludeOwe(value: Boolean) = edit { it[Keys.includeOwe] = value }
    suspend fun setIncludeHidden(value: Boolean) = edit { it[Keys.includeHidden] = value }
    suspend fun setAutoStartOnBoot(value: Boolean) = edit { it[Keys.autoStart] = value }
    suspend fun setWasRunning(value: Boolean) = edit { it[Keys.wasRunning] = value }
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboarding] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
