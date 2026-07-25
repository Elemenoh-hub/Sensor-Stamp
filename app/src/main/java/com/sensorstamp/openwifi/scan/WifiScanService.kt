package com.sensorstamp.openwifi.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sensorstamp.openwifi.R
import com.sensorstamp.openwifi.data.NetworkRepository
import com.sensorstamp.openwifi.data.Observation
import com.sensorstamp.openwifi.data.Settings
import com.sensorstamp.openwifi.data.SettingsStore
import com.sensorstamp.openwifi.permissions.Requirement
import com.sensorstamp.openwifi.permissions.hasPermission
import com.sensorstamp.openwifi.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Long-running collector. Wakes on a fixed cadence, asks the Wi-Fi radio for a
 * scan, and writes every open access point it can see to the database along with
 * the device's position at that instant.
 *
 * Runs as a `location` foreground service so Android lets it keep working with
 * the screen off while the user is moving.
 */
class WifiScanService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val writeLock = Mutex()

    private lateinit var wifiManager: WifiManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: NetworkRepository
    private lateinit var settingsStore: SettingsStore
    private lateinit var fusedLocation: FusedLocationProviderClient

    private var wakeLock: PowerManager.WakeLock? = null
    private var scanJob: Job? = null
    private var sessionId: Long = 0L
    private var settings: Settings = Settings()

    /** Most recent fix plus the elapsed-realtime clock reading when it arrived. */
    @Volatile private var lastLocation: Location? = null

    /** BSSIDs already written this session, so re-sightings can be counted cheaply. */
    private val seenThisSession = mutableSetOf<String>()

    /**
     * Last beacon-frame timestamp logged per BSSID. While Android throttles our
     * scans it keeps handing back the same cached results, and without this the
     * sightings table fills with duplicate rows that all describe one frame.
     */
    private val lastFrameTimestamp = mutableMapOf<String, Long>()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { fix ->
                lastLocation = fix
                ScanState.update {
                    it.copy(hasLocationFix = true, lastAccuracyM = fix.accuracy)
                }
            }
        }
    }

    /**
     * Fires whenever *any* app's scan completes, including the system's own
     * background scans. Piggy-backing on those keeps data flowing even when our
     * own [WifiManager.startScan] calls are being throttled.
     */
    private val scanResultsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { collectResults(freshResults = true) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        repository = NetworkRepository.from(this)
        settingsStore = SettingsStore(this)
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCollecting(userInitiated = true)
                return START_NOT_STICKY
            }
            else -> startCollecting()
        }
        // Ask the system to bring us back if it has to kill us mid-trip.
        return START_STICKY
    }

    private fun startCollecting() {
        if (scanJob?.isActive == true) return

        val blockers = Requirement.blockers(this)
        if (blockers.any { it == Requirement.LOCATION }) {
            // Starting a location-typed foreground service without the permission
            // throws on Android 14+, so bail out cleanly instead.
            ScanState.update { it.copy(running = false, message = "Location permission is required") }
            stopSelf()
            return
        }

        sessionId = System.currentTimeMillis()
        seenThisSession.clear()
        lastFrameTimestamp.clear()
        ScanState.reset(running = true, startedAt = sessionId)

        startForegroundNotification()
        acquireWakeLock()

        scanJob = scope.launch {
            settings = settingsStore.settings.first()
            settingsStore.setWasRunning(true)

            registerScanReceiver()
            requestLocationUpdates()
            primeLastKnownLocation()

            var tick = 0
            while (isActive) {
                val interval = settings.scanIntervalSeconds
                runCatching { performScan() }
                    .onFailure { ScanState.update { s -> s.copy(message = it.message) } }

                // Pick up interval changes made in Settings without a restart.
                if (tick++ % 2 == 0) {
                    settings = settingsStore.settings.first()
                }
                delay(interval * 1000L)
            }
        }
    }

    private suspend fun performScan() {
        if (!wifiManager.isWifiEnabled) {
            ScanState.update { it.copy(message = "Wi-Fi is off — turn it on to keep collecting") }
            updateNotification()
            return
        }

        @Suppress("DEPRECATION")
        val accepted = wifiManager.startScan()

        ScanState.update {
            it.copy(
                lastScanAt = System.currentTimeMillis(),
                scansCompleted = it.scansCompleted + 1,
                throttled = !accepted,
                message = if (accepted) null else THROTTLE_MESSAGE,
            )
        }

        // Read whatever the radio already has. If our startScan was accepted the
        // receiver will fire again shortly with fresher results.
        collectResults(freshResults = false)
        updateNotification()
    }

    @SuppressLint("MissingPermission")
    private suspend fun collectResults(freshResults: Boolean) {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return

        val results: List<ScanResult> = runCatching { wifiManager.scanResults }
            .getOrDefault(emptyList())
        if (results.isEmpty()) return

        val fix = lastLocation
        ScanState.update { it.copy(visibleAccessPoints = results.size) }

        // Without a position an observation is not worth much — the whole product
        // is "where is the open Wi-Fi", not "how much of it exists".
        if (fix == null) {
            ScanState.update { it.copy(hasLocationFix = false, message = "Waiting for a location fix…") }
            return
        }

        val maxAccuracy = settings.maxAccuracyMeters
        if (maxAccuracy > 0f && fix.accuracy > maxAccuracy) {
            ScanState.update {
                it.copy(message = "Fix too vague (${fix.accuracy.toInt()} m) — skipping this scan")
            }
            return
        }

        val now = System.currentTimeMillis()
        val fixAgeMs = fixAgeMillis(fix)

        writeLock.withLock {
            var newCount = 0
            var sightingCount = 0
            var lastSsid: String? = null

            for (result in results) {
                val security = WifiSecurity.classify(result)
                if (!security.isJoinableWithoutKey) continue
                if (security == WifiSecurity.OWE && !settings.includeOwe) continue

                val ssid = WifiSecurity.ssidOf(result)
                val hidden = ssid.isEmpty()
                if (hidden && !settings.includeHidden) continue

                val bssid = result.BSSID?.lowercase() ?: continue

                // The radio stamps each beacon frame. An unchanged stamp means
                // this is the same frame we already recorded, replayed out of the
                // scan cache — logging it again would invent a sighting that
                // never happened.
                val frameTimestamp = result.timestamp
                if (frameTimestamp > 0 && lastFrameTimestamp[bssid] == frameTimestamp) continue
                lastFrameTimestamp[bssid] = frameTimestamp

                val alreadySeenThisSession = !seenThisSession.add(bssid)
                if (settings.onlyLogNewNetworks && alreadySeenThisSession) continue

                val observation = Observation(
                    bssid = bssid,
                    ssid = ssid,
                    capabilities = result.capabilities.orEmpty(),
                    securityType = security.name,
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    channel = WifiSecurity.channelFor(result.frequency),
                    band = WifiSecurity.bandFor(result.frequency),
                    channelWidthMhz = WifiSecurity.channelWidthMhz(result),
                    centerFreq0 = result.centerFreq0,
                    centerFreq1 = result.centerFreq1,
                    wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        result.wifiStandard
                    } else {
                        0
                    },
                    supportsFtm = runCatching { result.is80211mcResponder }.getOrDefault(false),
                    isPasspoint = runCatching { result.isPasspointNetwork }.getOrDefault(false),
                    isHidden = hidden,
                    venueHint = result.operatorFriendlyName?.toString()?.takeIf { it.isNotBlank() },
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyM = fix.accuracy,
                    altitudeM = if (fix.hasAltitude()) fix.altitude else 0.0,
                    speedMps = if (fix.hasSpeed()) fix.speed else 0f,
                    bearingDeg = if (fix.hasBearing()) fix.bearing else 0f,
                    locationProvider = fix.provider ?: "fused",
                    locationAgeMs = fixAgeMs,
                    scanTimestampMicros = frameTimestamp,
                    observedAt = now,
                    sessionId = sessionId,
                )

                val isNew = repository.record(
                    obs = observation,
                    logSightingRow = !settings.onlyLogNewNetworks,
                )
                sightingCount++
                if (isNew) {
                    newCount++
                    lastSsid = if (hidden) "(hidden network)" else ssid
                }
            }

            if (sightingCount > 0) {
                ScanState.update {
                    it.copy(
                        sessionNewNetworks = it.sessionNewNetworks + newCount,
                        sessionSightings = it.sessionSightings + sightingCount,
                        lastFindSsid = lastSsid ?: it.lastFindSsid,
                        lastFindAt = if (lastSsid != null) now else it.lastFindAt,
                        hasLocationFix = true,
                        message = if (freshResults) null else it.message,
                    )
                }
                if (newCount > 0) updateNotification()
            }
        }
    }

    /** Age of a fix in wall-clock milliseconds, using the monotonic clock. */
    private fun fixAgeMillis(fix: Location): Long {
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        return ((nowNanos - fix.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return

        // Keep the fix fresher than the scan cadence so every scan gets a
        // position that actually reflects where we are now.
        val intervalMs = (settings.scanIntervalSeconds * 1000L).coerceIn(2_000L, 15_000L)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(false)
            .build()

        runCatching {
            fusedLocation.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }

    @SuppressLint("MissingPermission")
    private fun primeLastKnownLocation() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) return
        runCatching {
            fusedLocation.lastLocation.addOnSuccessListener { fix ->
                if (fix != null && lastLocation == null) {
                    lastLocation = fix
                    ScanState.update { it.copy(hasLocationFix = true, lastAccuracyM = fix.accuracy) }
                }
            }
        }
    }

    private fun registerScanReceiver() {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanResultsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(scanResultsReceiver, filter)
        }
    }

    private fun stopCollecting(userInitiated: Boolean) {
        scanJob?.cancel()
        scanJob = null
        runCatching { unregisterReceiver(scanResultsReceiver) }
        runCatching { fusedLocation.removeLocationUpdates(locationCallback) }
        releaseWakeLock()
        ScanState.update { it.copy(running = false, message = null) }

        if (userInitiated) {
            // Remember the off state so a reboot does not silently resume collection.
            CoroutineScope(Dispatchers.IO).launch { SettingsStore(applicationContext).setWasRunning(false) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        runCatching { unregisterReceiver(scanResultsReceiver) }
        runCatching { fusedLocation.removeLocationUpdates(locationCallback) }
        releaseWakeLock()
        ScanState.update { it.copy(running = false) }
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- wakelock

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
    }

    // ------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Collection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Sensor Stamp is mapping open Wi-Fi."
            setShowBadge(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundNotification() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun updateNotification() {
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val status = ScanState.status.value

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WifiScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val headline = when {
            status.sessionNewNetworks == 1 -> "1 open network found"
            status.sessionNewNetworks > 1 -> "${status.sessionNewNetworks} open networks found"
            else -> "Listening for open networks"
        }
        val detail = buildString {
            append("Scanning every ${settings.scanIntervalSeconds}s")
            status.lastFindSsid?.let { append(" · last: $it") }
            if (status.throttled) append(" · rate-limited by Android")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(headline)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.sensorstamp.openwifi.START"
        const val ACTION_STOP = "com.sensorstamp.openwifi.STOP"

        private const val CHANNEL_ID = "collection"
        private const val NOTIFICATION_ID = 4711
        private const val WAKE_LOCK_TAG = "SensorStamp::collection"
        private const val THROTTLE_MESSAGE =
            "Android is rate-limiting scans — still logging the system's own results"

        fun start(context: Context) {
            val intent = Intent(context, WifiScanService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WifiScanService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
