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
- **Live map.** Every access point plotted on OpenStreetMap, colour-coded by signal,
  band, network type or recency, with density clustering at low zoom and a detail card
  on tap. Dark basemap when the system theme is dark.
- **Granular records.** Two tables: one row per access point, plus one row per
  individual sighting so the same AP seen from many points along a route gives you its
  real coverage footprint.
- **Classification, not just capture.** Each network gets a hardware vendor from its
  OUI and a guess at what it actually is — municipal, venue, carrier, transit,
  residential, personal hotspot or IoT device — so a printer's setup network never
  gets mistaken for somewhere you can sit down and work.
- **Guided permission setup.** First launch walks through every grant the app needs —
  location, background location, nearby-devices, notifications, battery optimisation,
  and the Wi-Fi/location system toggles — explaining why each one matters.
- **CSV export.** Both tables, through the system file picker. Your collected data
  never leaves the device unless you export it.

## What gets recorded

Per access point (`networks`):

| Field | Notes |
| --- | --- |
| `bssid` | AP radio MAC — the stable identity, and the dedup key |
| `ssid` | Broadcast name, blank for hidden networks |
| `capabilities` | Raw capability string, kept verbatim for later analysis |
| `securityType` | `OPEN`, `OWE`, or `OPEN_WPS` |
| `vendor` | Hardware maker from the OUI; null when unknown or locally administered |
| `networkKind` | `MUNICIPAL`, `VENUE`, `CARRIER`, `TRANSIT`, `RESIDENTIAL`, `PERSONAL_HOTSPOT`, `DEVICE`, `UNKNOWN` |
| `frequencyMhz`, `channel`, `band`, `channelWidthMhz` | Radio details |
| `centerFreq0`, `centerFreq1` | Segment centres, revealing bonded-channel layout |
| `wifiStandard` | 802.11 generation (Wi-Fi 4/5/6/6E/7), where the OS reports it |
| `supportsFtm` | Advertises 802.11mc fine timing measurement — precisely rangeable |
| `bestRssi`, `worstRssi` | Signal range this AP was heard across |
| `latitude`, `longitude`, `accuracyM`, `altitudeM` | Position at the strongest sighting |
| `weightSum`, `weightedLatSum`, `weightedLonSum` | Running signal-weighted position estimate |
| `boundsNorth/South/East/West` | Bounding box of everywhere it was audible |
| `coverageRadiusM` | Half-diagonal of those bounds — how far it reached |
| `isLikelyMobile` | Heard too far apart to be fixed: transit or pocket hotspot |
| `speedMps`, `bearingDeg`, `locationProvider` | Motion context for the fix |
| `venueHint`, `isPasspoint`, `isHidden` | Passpoint / hidden-network flags |
| `firstSeenAt`, `lastSeenAt`, `bestSeenAt` | Timestamps |
| `sightingCount`, `sessionCount`, `lastSessionId` | Roll-up counters |

The exported CSV also carries `est_latitude` / `est_longitude`, the signal-weighted
centroid resolved from the running sums. That is usually a better estimate of where
the radio physically sits than the single strongest fix, because it triangulates
across your whole approach rather than trusting one moment.

Per sighting (`sightings`): every observation with its own timestamp, RSSI, position,
fix age, session id, channel, band, security, a rough free-space distance estimate,
and the radio's own beacon-frame timestamp. This is the table to use for coverage
modelling — the network table only keeps one row per AP.

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

**The map needs the internet; your data does not.** The Map tab fetches
OpenStreetMap raster tiles, which is why the app requests `INTERNET`. That means the
OSM tile servers can see roughly which area you are looking at, and tiles are cached
on disk. Nothing about the networks you collect is ever sent anywhere — no telemetry,
no sync, no analytics. If you never open the Map tab, the app makes no network
requests at all. OpenStreetMap was chosen over Google Maps precisely so the APK works
with no API key to provision.

**Classification is a heuristic, not ground truth.** `networkKind` is pattern matching
over SSID text plus an OUI lookup. It gets the common cases right — `HP-Print-Guest` is
a printer, not a venue, and `xfinitywifi` is a carrier hotspot — but an unhelpfully
named network stays `UNKNOWN` rather than being guessed at. Treat it as a filter that
saves you time, not as a fact.

**Distance and position estimates are rough.** `estimatedDistanceM` inverts free-space
path loss assuming a 20 dBm access point, which indoors is routinely wrong by several
times over. It is stored because it is useful for comparing sightings *of the same AP*,
not because any single value is trustworthy. The weighted centroid is a better position
estimate than the strongest single fix, but it is still not a survey.

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
├── scan/            Collection service, security/kind classifiers, OUI table, radio math
└── ui/              Compose screens, theme, map overlay and styling
```

## Testing

79 JVM unit tests cover the parts that do not need a device:

- **Security classification** against real capability strings, including OWE, which is
  easy to misclassify as secured because it advertises as an RSN suite.
- **Network-kind heuristics**, especially the precedence cases — a printer whose name
  contains "guest" must not read as a café.
- **The OUI table**, checked structurally so a typo'd key that could never match is
  caught rather than silently doing nothing.
- **The merge logic** that decides what each row ends up holding: which sighting wins
  the best fix, how the weighted centroid moves, when bounds grow, when a network is
  flagged as mobile.
- **Radio and geometry maths** — distance monotonicity, weighting, great-circle
  distance against a known separation.
- **The v1 → v2 migration**, executed as the exact SQL that ships against a real SQLite
  engine and diffed column-by-column against Room's exported v2 schema. A wrong
  migration would crash on upgrade or silently destroy a user's collected data, and
  Room only checks it on a device — so this reproduces that check on the JVM.
- **CSV escaping** (SSIDs are attacker-controlled text that ends up in a spreadsheet).

```bash
./gradlew :app:testDebugUnitTest
```

Not covered: the scanning and location paths, and the map rendering. Those need real
radios and a real screen, and cannot be exercised on an emulator.
