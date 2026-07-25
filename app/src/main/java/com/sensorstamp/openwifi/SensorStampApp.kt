package com.sensorstamp.openwifi

import android.app.Application
import com.sensorstamp.openwifi.data.NetworkRepository
import com.sensorstamp.openwifi.data.SettingsStore

class SensorStampApp : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val repository: NetworkRepository by lazy { NetworkRepository.from(this) }
}
