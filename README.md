# Sensor Stamp

An Android app that maps open, password-free Wi-Fi as you travel. Press start, put
the phone in your pocket, and every open access point you pass is written to a local
database along with exactly where you were when you saw it.

Built to gather the coverage data behind a "where can I get online for free" product.

## What it does

- **One-tap collection.** A single start/stop control. Everything else has a sensible
  default.
- **Runs in the background.** A `location`-typed foreground service with a wake lock,
  so collection survives a locked screen and a pocket.
- **Adjustable cadence.** Scan interval from 5 seconds to 10 minutes, via slider or
  one-tap presets, with an honest note about what each costs you in battery.
- **Granular records.** Two tables: one row per access point, plus one row per
  individual sighting so the same AP seen from many points along a route gives you its
  real coverage footprint.
- **Guided permission setup.** First launch walks through every grant the app needs —
  location, background location, nearby-devices, notifications, battery optimisation,
  and the Wi-Fi/location system toggles — explaining why each one matters.
- **CSV export.** Both tables, through the system file picker. Nothing leaves the
  device unless you export it.

## What gets recorded

Per access point (`networks`):

| Field | Notes |
| --- | --- |
| `bssid` | AP radio MAC — the stable identity, and the dedup key |
| `ssid` | Broadcast name, blank for hidden networks |
| `capabilities` | Raw capability string, kept verbatim for later analysis |
| `securityType` | `OPEN`, `OWE`, or `OPEN_WPS` |
| `frequencyMhz`, `channel`, `band`, `channelWidthMhz` | Radio details |
| `bestRssi` | Strongest signal ever seen, with the fix from that moment |
| `latitude`, `longitude`, `accuracyM`, `altitudeM` | Position at strongest signal |
| `speedMps`, `bearingDeg`, `locationProvider` | Motion context for the fix |
| `venueHint`, `isPasspoint`, `isHidden` | Passpoint / hidden-network flags |
| `firstSeenAt`, `lastSeenAt`, `sightingCount` | Roll-up counters |

Per sighting (`sightings`): every observation with its own timestamp, RSSI, position,
fix age and session id. This is the table to use for coverage modelling — the network
table only keeps the single best fix.

## Building

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug        # installable debug APK
./gradlew :app:testDebugUnitTest    # unit tests
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. Install with
`adb install -r app-debug.apk`, or copy it to the phone and open it (you will need to
allow installs from that source).

### Signed release builds

Release builds are signed only if you supply your own keystore. Create one, then drop
a `keystore.properties` in the repository root:

```properties
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

```bash
keytool -genkeypair -v -keystore release.keystore -alias sensorstamp \
        -keyalg RSA -keysize 2048 -validity 10000
./gradlew :app:assembleRelease
```

Both the keystore and `keystore.properties` are gitignored. Keep the keystore safe —
without the same key you cannot ship an update over an installed build.

## Things worth knowing

**Android throttles Wi-Fi scans.** Since Android 9, a foreground app gets roughly four
`startScan()` calls per two minutes. Setting a 5-second interval does not defeat that.
What the app does instead is listen for `SCAN_RESULTS_AVAILABLE` from *any* scan on the
device — including the system's own — so short intervals still increase your sample
rate, just not linearly. The UI says "rate-limited by Android" when it hits the ceiling
rather than pretending otherwise. Developer options has a "Wi-Fi scan throttling" toggle
if you want the raw cadence on your own device.

**Sightings need a position.** Scans that land before the first GPS fix are skipped
rather than stored with a null or stale location. The status line tells you when it is
waiting on a fix.

**Aggressive OEM battery managers.** The battery-optimisation exemption in setup covers
stock Android. Some manufacturers (Xiaomi, Huawei, Oppo, Samsung to a lesser degree)
layer extra process killers on top that need their own settings screen. If collection
stops on a long trip, that is the first place to look.

**"Open" means no credential required.** Three states are logged: fully open networks,
Enhanced Open / OWE (no password, but encrypted per-client), and open networks
advertising WPS. Networks behind a captive portal look open at the radio layer and are
recorded as such — the app never associates, so it cannot tell you whether a portal
demands a room number.

## Legal note

Passively logging broadcast beacons is how every Wi-Fi map (WiGLE, Google, Apple) is
built, and is generally lawful in most jurisdictions. Connecting to a network without
authorisation is not, and this app never connects. The data is yours and stays on your
device; if you publish or share it, consider that AP locations can reveal where people
live.

## Layout

```
app/src/main/java/com/sensorstamp/openwifi/
├── data/            Room entities, DAO, repository, settings (DataStore)
├── permissions/     Requirement model — what to ask for and how
├── scan/            Foreground collection service, security classifier, boot receiver
└── ui/              Compose screens, theme, formatting helpers
```

## Testing

28 JVM unit tests cover the parts that do not need a device: security classification
against real capability strings, channel/band arithmetic, display formatting, and CSV
escaping (SSIDs are attacker-controlled text that ends up in a spreadsheet).

```bash
./gradlew :app:testDebugUnitTest
```

The scanning and location paths need real radios and are not covered — they cannot be
exercised on an emulator.
