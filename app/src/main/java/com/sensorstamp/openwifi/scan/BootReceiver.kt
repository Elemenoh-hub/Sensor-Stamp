package com.sensorstamp.openwifi.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sensorstamp.openwifi.data.SettingsStore
import com.sensorstamp.openwifi.permissions.Requirement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Resumes collection after a reboot or an app update, but only if the user had
 * it running and opted in — a mapping run that survives a reboot is useful, one
 * that starts itself unasked is not.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = SettingsStore(appContext).settings.first()
                val ready = Requirement.blockers(appContext).isEmpty()
                if (settings.autoStartOnBoot && settings.wasRunning && ready) {
                    WifiScanService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
