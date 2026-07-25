package com.sensorstamp.openwifi.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of what the collector is doing right now, for the UI to mirror. */
data class ScanStatus(
    val running: Boolean = false,
    val sessionStartedAt: Long = 0L,
    val lastScanAt: Long = 0L,
    val scansCompleted: Int = 0,
    val sessionNewNetworks: Int = 0,
    val sessionSightings: Int = 0,
    val lastFindSsid: String? = null,
    val lastFindAt: Long = 0L,
    val visibleAccessPoints: Int = 0,
    /**
     * Android rate-limits foreground apps to a handful of scans per two minutes.
     * When we hit that ceiling we fall back to the system's own scan results, so
     * collection continues — the UI just says so honestly.
     */
    val throttled: Boolean = false,
    val hasLocationFix: Boolean = false,
    val lastAccuracyM: Float = 0f,
    val message: String? = null,
)

/**
 * Process-wide status shared between the collection service and the UI. The
 * service is the only writer.
 */
object ScanState {
    private val _status = MutableStateFlow(ScanStatus())
    val status: StateFlow<ScanStatus> = _status.asStateFlow()

    internal fun update(block: (ScanStatus) -> ScanStatus) = _status.update(block)

    internal fun reset(running: Boolean, startedAt: Long) {
        _status.value = ScanStatus(running = running, sessionStartedAt = startedAt)
    }
}
