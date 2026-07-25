package com.sensorstamp.openwifi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sensorstamp.openwifi.SensorStampApp
import com.sensorstamp.openwifi.data.Settings
import com.sensorstamp.openwifi.data.db.NetworkEntity
import com.sensorstamp.openwifi.data.db.ScanTotals
import com.sensorstamp.openwifi.permissions.Requirement
import com.sensorstamp.openwifi.scan.ScanState
import com.sensorstamp.openwifi.scan.ScanStatus
import com.sensorstamp.openwifi.scan.WifiScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportKind(val fileName: String, val label: String) {
    NETWORKS("open-networks.csv", "Access points"),
    SIGHTINGS("open-network-sightings.csv", "Every sighting"),
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as SensorStampApp
    private val repository = application.repository
    private val settingsStore = application.settingsStore

    val status: StateFlow<ScanStatus> = ScanState.status

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val totals: StateFlow<ScanTotals> = repository.observeTotals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanTotals(0, 0, 0))

    val recent: StateFlow<List<NetworkEntity>> = repository.observeRecent(RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val networks: StateFlow<List<NetworkEntity>> = _query
        .flatMapLatest { q ->
            if (q.isBlank()) repository.observeAll() else repository.search(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live grant state for every requirement that applies to this device. */
    private val _requirements = MutableStateFlow(emptyMap<Requirement, Boolean>())
    val requirements: StateFlow<Map<Requirement, Boolean>> = _requirements.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        refreshRequirements()
    }

    fun refreshRequirements() {
        _requirements.value = Requirement.onboardingOrder.associateWith {
            it.isSatisfied(application)
        }
    }

    val blockers: List<Requirement>
        get() = _requirements.value.filterValues { !it }.keys.filter { it.required }

    fun toggleCollection() {
        if (status.value.running) {
            WifiScanService.stop(application)
        } else {
            refreshRequirements()
            val missing = blockers
            if (missing.isNotEmpty()) {
                _toast.value = "Grant \"${missing.first().title}\" before starting"
                return
            }
            WifiScanService.start(application)
        }
    }

    fun setInterval(seconds: Int) = viewModelScope.launch { settingsStore.setInterval(seconds) }
    fun setOnlyLogNew(value: Boolean) = viewModelScope.launch { settingsStore.setOnlyLogNew(value) }
    fun setMaxAccuracy(meters: Float) = viewModelScope.launch { settingsStore.setMaxAccuracy(meters) }
    fun setIncludeOwe(value: Boolean) = viewModelScope.launch { settingsStore.setIncludeOwe(value) }
    fun setIncludeHidden(value: Boolean) = viewModelScope.launch { settingsStore.setIncludeHidden(value) }
    fun setAutoStartOnBoot(value: Boolean) = viewModelScope.launch { settingsStore.setAutoStartOnBoot(value) }
    fun completeOnboarding() = viewModelScope.launch { settingsStore.setOnboardingComplete(true) }

    fun setQuery(value: String) { _query.value = value }

    fun clearToast() { _toast.value = null }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAll()
        _toast.value = "Database cleared"
    }

    fun export(kind: ExportKind, uri: Uri) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = application.contentResolver
                resolver.openOutputStream(uri)?.use { out ->
                    when (kind) {
                        ExportKind.NETWORKS -> repository.exportNetworksCsv(out)
                        ExportKind.SIGHTINGS -> repository.exportSightingsCsv(out)
                    }
                } ?: error("Could not open the destination file")
            }
        }
        _toast.value = result.fold(
            onSuccess = { "Exported ${kind.label.lowercase()}" },
            onFailure = { "Export failed: ${it.message}" },
        )
    }

    companion object {
        const val RECENT_LIMIT = 25
    }
}
