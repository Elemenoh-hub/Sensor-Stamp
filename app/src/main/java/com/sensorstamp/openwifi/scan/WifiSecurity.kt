package com.sensorstamp.openwifi.scan

import android.net.wifi.ScanResult

/**
 * Classification of an access point's security, derived from the raw capability
 * string. We only care about the handful of states that mean "a stranger can
 * associate without a pre-shared key".
 */
enum class WifiSecurity(val label: String) {
    /** No encryption at all. Joinable by anyone; traffic is in the clear. */
    OPEN("Open"),

    /** Enhanced Open / OWE. No password, but the link is encrypted per-client. */
    OWE("Enhanced Open"),

    /** Open association but WPS is advertised — still joinable without credentials. */
    OPEN_WPS("Open + WPS"),

    /** Anything requiring a credential: WEP, WPA/2/3-PSK, 802.1X, SAE. */
    SECURED("Secured");

    val isJoinableWithoutKey: Boolean
        get() = this == OPEN || this == OWE || this == OPEN_WPS

    companion object {
        private val SECURED_MARKERS = listOf("WEP", "WPA", "RSN", "PSK", "EAP", "SAE", "IEEE8021X")

        fun classify(result: ScanResult): WifiSecurity = classify(result.capabilities)

        /**
         * Kept separate from the [ScanResult] overload so the classification rules
         * can be exercised without an Android runtime.
         */
        fun classify(capabilities: String?): WifiSecurity {
            val caps = capabilities.orEmpty().uppercase()

            // OWE is advertised as an RSN suite, so it has to be checked before
            // the generic "contains RSN means secured" rule below.
            if (caps.contains("OWE")) return OWE

            if (SECURED_MARKERS.any { caps.contains(it) }) return SECURED

            // Nothing demanding a credential was advertised. WPS-only access
            // points are still walk-up joinable, just flagged separately.
            return if (caps.contains("WPS")) OPEN_WPS else OPEN
        }

        /** Human-friendly channel number from the centre frequency. */
        fun channelFor(frequencyMhz: Int): Int = when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2412) / 5 + 1
            frequencyMhz in 5160..5885 -> (frequencyMhz - 5000) / 5
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5955) / 5 + 1
            frequencyMhz in 58320..70200 -> (frequencyMhz - 56160) / 2160
            else -> 0
        }

        fun bandFor(frequencyMhz: Int): String = when {
            frequencyMhz < 2500 -> "2.4 GHz"
            frequencyMhz < 5900 -> "5 GHz"
            frequencyMhz < 7200 -> "6 GHz"
            else -> "60 GHz"
        }

        /**
         * Channel width in MHz. Compared against the raw constant values so a
         * newer width reported by a future radio still maps cleanly instead of
         * needing an API-level guard.
         */
        fun channelWidthMhz(result: ScanResult): Int = when (result.channelWidth) {
            0 -> 20
            1 -> 40
            2 -> 80
            3, 4 -> 160
            5 -> 320
            else -> 0
        }

        /** SSID text, tolerating the API-33 rename and hidden networks. */
        @Suppress("DEPRECATION")
        fun ssidOf(result: ScanResult): String = (result.SSID ?: "").trim()
    }
}
