package com.sensorstamp.openwifi.scan

/**
 * A guess at what an open network actually *is*, from its broadcast name.
 *
 * "Open" at the radio layer covers wildly different things: a city hotspot you
 * can sit down and work on, a printer's setup network, and somebody's phone
 * walking past all look identical in a scan. Separating them is the difference
 * between a useful coverage map and a map full of dead ends.
 *
 * This is a heuristic over SSID text. It is deliberately conservative — anything
 * it cannot place confidently stays [UNKNOWN] rather than being guessed at.
 */
enum class NetworkKind(val label: String, val usefulForBrowsing: Boolean) {
    /** Carrier-operated hotspot networks, usually needing a subscriber login. */
    CARRIER("Carrier hotspot", true),

    /** Municipal and public-sector Wi-Fi: councils, libraries, parks, transit hubs. */
    MUNICIPAL("Municipal / public", true),

    /** Cafés, shops, hotels and other venues offering guest access. */
    VENUE("Venue guest Wi-Fi", true),

    /** On-board Wi-Fi for trains, buses, planes and ferries. Moves, by definition. */
    TRANSIT("Transit Wi-Fi", true),

    /** A phone or dongle sharing its mobile data. Transient — here today, gone tomorrow. */
    PERSONAL_HOTSPOT("Personal hotspot", false),

    /** Printers, TVs, cameras, car infotainment. Open, but not an internet connection. */
    DEVICE("Device / IoT", false),

    /** Looks like a residential router left open. */
    RESIDENTIAL("Residential", true),

    /** Nothing in the name gave it away. */
    UNKNOWN("Unclassified", true);

    companion object {

        // Checked in order — the first list to match wins, so the most specific
        // and least ambiguous patterns come first.
        private val DEVICE_PATTERNS = listOf(
            "direct-", "hp-print", "hpsetup", "hp-setup", "canon_ij", "epson",
            "brother", "chromecast", "roku", "firetv", "amazon-", "printer",
            "scanner", "esp_", "esp-", "arduino", "raspberrypi", "gopro",
            "dji-", "insta360", "sonos", "nest-", "wyze", "ring-setup",
            "tesla", "my model 3", "my model y", "obd", "dashcam", "sl-",
            "-setup", "setup-", "config-ap",
        )

        private val PERSONAL_PATTERNS = listOf(
            "iphone", "ipad", "androidap", "android hotspot", "galaxy ",
            "pixel ", "'s phone", "s phone", "mifi", "-hotspot", "hotspot-",
            "myhotspot", "jetpack", "verizon-", "franklin", "moto ", "oneplus",
            "huawei mate", "redmi", "poco ",
        )

        private val TRANSIT_PATTERNS = listOf(
            "amtrak", "gogoinflight", "aainflight", "united_wifi", "unitedwifi",
            "delta wi-fi", "deltawifi", "southwestwifi", "alaskawifi", "jetblue",
            "_mta_wifi", "mta wi-fi", "tfl wifi", "gwr wifi", "lner", "avanti",
            "trainline", "via rail", "greyhound", "megabus", "flixbus",
            "onboard wifi", "wifi onboard", "inflight", "ferry", "cruise",
            "_wifi_train", "sncf", "tgv", "wifi in de trein", "icomera",
        )

        private val CARRIER_PATTERNS = listOf(
            "xfinitywifi", "xfinity", "attwifi", "att-wifi", "att wifi",
            "optimumwifi", "cablewifi", "spectrumwifi", "twcwifi", "coxwifi",
            "btwifi", "bt wi-fi", "bt-wifi", "sky wifi", "virgin media",
            "telstra air", "optus wifi", "fon_", "fon wifi", "sfr wifi",
            "orange wifi", "freewifi_secure", "free wifi_secure", "vodafone",
            "swisscom", "kpn fon", "ziggo", "eduroam",
        )

        private val MUNICIPAL_PATTERNS = listOf(
            "linknyc", "link nyc", "city of", "cityof", "city wifi", "city wi-fi",
            "council", "library", "publiclibrary", "public library", "municipal",
            "township", "county", "gov ", "govwifi", ".gov", "airport", "_airport",
            "metro wifi", "transit center", "park wifi", "parks", "campus",
            "university", "college", "school", "hospital", "nhs wifi", "clinic",
            "community centre", "community center", "rec center", "town of",
            "village of", "borough",
        )

        private val VENUE_PATTERNS = listOf(
            "guest", "_guest", "guestwifi", "visitor", "customer", "patron",
            "starbucks", "mcdonald", "mcdwifi", "costa", "pret", "nero",
            "wetherspoon", "greggs", "subway", "burgerking", "kfc", "panera",
            "dunkin", "peets", "tim hortons", "timhortons", "ikea", "target",
            "walmart", "bestbuy", "homedepot", "lowes", "sainsbury", "tesco",
            "asda", "morrisons", "waitrose", "marks & spencer", "m&s",
            "marriott", "hilton", "hyatt", "holiday inn", "ihg", "premierinn",
            "travelodge", "hostel", "hotel", "motel", "b&b", "airbnb",
            "cafe", "coffee", "restaurant", "bar wifi", "pub wifi", "brewery",
            "mall", "shopping", "retail", "store wifi", "lounge", "gym",
            "free wifi", "freewifi", "free_wifi", "wifi gratis", "wifi gratuit",
            "public wifi", "publicwifi", "wifi libre",
        )

        private val RESIDENTIAL_PATTERNS = listOf(
            "netgear", "linksys", "dlink", "d-link", "tp-link", "tplink",
            "asus", "belkin", "arris", "sagemcom", "technicolor", "fritz!box",
            "fritzbox", "virginmedia", "bthub", "bt hub", "sky ", "talktalk",
            "orange-", "livebox", "movistar", "vodafone-", "telekom", "speedport",
            "ziggo-", "kpn-", "upc", "home-", "-home", "wifi-", "wlan-",
            "dsl-", "router", "gateway", "myqwest", "centurylink", "frontier",
        )

        /**
         * Classify a broadcast name. [vendor] is used only as a tie-breaker for
         * names that gave nothing away.
         */
        fun classify(ssid: String, vendor: String? = null): NetworkKind {
            val name = ssid.trim().lowercase()
            if (name.isEmpty()) return UNKNOWN

            // Order matters: a printer called "HP-Print-Guest" is a device, not a
            // venue, so the narrow categories are tested before the broad ones.
            if (DEVICE_PATTERNS.any { name.contains(it) }) return DEVICE
            if (PERSONAL_PATTERNS.any { name.contains(it) }) return PERSONAL_HOTSPOT
            if (TRANSIT_PATTERNS.any { name.contains(it) }) return TRANSIT
            if (CARRIER_PATTERNS.any { name.contains(it) }) return CARRIER
            if (MUNICIPAL_PATTERNS.any { name.contains(it) }) return MUNICIPAL
            if (VENUE_PATTERNS.any { name.contains(it) }) return VENUE
            if (RESIDENTIAL_PATTERNS.any { name.contains(it) }) return RESIDENTIAL

            // Nothing in the name matched. Consumer-router silicon with a name we
            // do not recognise is most likely a home network.
            return when (vendor) {
                "TP-Link", "Netgear", "D-Link", "ASUS", "Linksys", "Belkin",
                "Sagemcom", "Technicolor", "AVM FRITZ!Box", "Arris", "Zyxel",
                -> RESIDENTIAL

                "Apple", "Samsung", "Google" -> PERSONAL_HOTSPOT
                "Roku", "HP", "Canon", "Amazon", "Tesla" -> DEVICE
                else -> UNKNOWN
            }
        }
    }
}
