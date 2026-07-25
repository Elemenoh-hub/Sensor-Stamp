package com.sensorstamp.openwifi

import android.app.Application
import com.sensorstamp.openwifi.data.NetworkRepository
import com.sensorstamp.openwifi.data.SettingsStore
import org.osmdroid.config.Configuration
import java.io.File

class SensorStampApp : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: NetworkRepository by lazy { NetworkRepository.from(this) }

    override fun onCreate() {
        super.onCreate()
        configureMapTiles()
    }

    /**
     * osmdroid defaults to a shared external-storage cache and an empty
     * User-Agent. The OpenStreetMap tile policy requires a real identifying
     * agent, and app-private storage means the cache disappears with the app.
     */
    private fun configureMapTiles() {
        Configuration.getInstance().apply {
            load(this@SensorStampApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "SensorStamp/${BuildConfig.VERSION_NAME} (${packageName})"
            osmdroidBasePath = File(cacheDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(osmdroidBasePath, "tiles").apply { mkdirs() }
            // Keep the on-disk tile cache modest; this is a data collector that
            // happens to draw a map, not a navigation app.
            tileFileSystemCacheMaxBytes = 96L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 64L * 1024 * 1024
        }
    }
}
