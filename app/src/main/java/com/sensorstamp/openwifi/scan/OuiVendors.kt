package com.sensorstamp.openwifi.scan

/**
 * Maps the first three octets of a BSSID (the IEEE OUI) to the hardware maker.
 *
 * Knowing the vendor is a strong hint about what kind of network you found: an
 * Aruba, Ruckus or Cisco radio is almost always a business, campus or municipal
 * deployment, while a TP-Link or Sagemcom radio is somebody's home router that
 * happens to be open. That distinction matters when the end product is "where
 * can I sit down and reliably get online".
 *
 * This is a curated subset covering the vendors that actually turn up in a scan,
 * not the full 30k-entry IEEE registry.
 */
object OuiVendors {

    /**
     * Access points serving several SSIDs from one radio derive the extra BSSIDs
     * by setting the locally-administered bit, which makes the OUI meaningless.
     * Better to report "unknown" than to guess wrong.
     */
    fun isLocallyAdministered(bssid: String): Boolean {
        val firstOctet = normalise(bssid).take(2).toIntOrNull(16) ?: return false
        return (firstOctet and 0x02) != 0
    }

    /** Vendor name for a BSSID, or null when unknown or locally administered. */
    fun lookup(bssid: String): String? {
        val normalised = normalise(bssid)
        if (normalised.length < 6) return null
        if (isLocallyAdministered(normalised)) return null
        return TABLE[normalised.take(6)]
    }

    private fun normalise(bssid: String) = bssid.filter { it.isLetterOrDigit() }.uppercase()

    /** Exposed for tests, which assert every key is a well-formed OUI. */
    internal val table: Map<String, String> get() = TABLE

    private val TABLE: Map<String, String> = buildMap {
        fun add(vendor: String, vararg ouis: String) = ouis.forEach { put(it, vendor) }

        // --- Enterprise, campus and municipal access points --------------------
        add(
            "Cisco",
            "00000C", "000142", "000143", "0001C7", "000216", "00170E", "001B0D",
            "002155", "0025B4", "04FE7F", "1CDEA7", "2C3F38", "3C0E23", "4C710C",
            "588D09", "6400F1", "7C0ECE", "84B802", "A0EC80", "BC1665", "E8B7C4",
        )
        add(
            "Aruba Networks",
            "000B86", "001A1E", "24DEC6", "6CF37F", "781FDB", "84D47E", "9C1C12",
            "ACA31E", "B4B015", "D8C7C8", "F05C19",
        )
        add(
            "Ruckus Wireless",
            "001392", "0C7523", "204C03", "24793F", "581626", "6CAAB3", "882593",
            "C0C520", "F0B052",
        )
        add(
            "Ubiquiti",
            "0418D6", "24A43C", "44D9E7", "687251", "70A741", "74ACB9", "78458C",
            "802AA8", "94A67E", "B4FBE4", "DC9FDB", "E063DA", "F09FC2", "FCECDA",
        )
        add("Cisco Meraki", "00180A", "AC17C8", "E0CBBC", "E8267C")
        add("Extreme Networks", "000476", "000496", "5C0E8B", "B4C799", "D8842F")
        add("Mist Systems", "5C5B35", "D420B0")
        add("CommScope", "001615", "0022A4", "3CB74B", "5C5715")
        add("Juniper Networks", "000585", "2C6BF5", "3C6104", "F4CC55")

        // --- Consumer routers and ISP gateways ---------------------------------
        add(
            "TP-Link",
            "001D0F", "002719", "14CC20", "1C61B4", "300505", "50C7BF", "5C6337",
            "6CE873", "7CB59B", "984827", "A42BB0", "AC84C6", "B0487A", "C006C3",
            "C46E1F", "D8478F", "E848B8", "EC086B", "F0A731",
        )
        add(
            "Netgear",
            "000FB5", "001B2F", "0024B2", "008EF2", "20E52A", "28C68E", "2C3033",
            "3C3786", "44A56E", "6CB0CE", "841B5E", "9C3DCF", "A00460", "B03956",
            "C03F0E", "CC40D0", "E091F5",
        )
        add(
            "D-Link",
            "00055D", "001346", "002191", "1CBDB9", "340804", "3C1E04", "5CD998",
            "78321B", "849CA6", "9094E4", "B8A386", "C8BE19", "CCB255", "F07D68",
        )
        add(
            "ASUS",
            "000C6E", "001BFC", "002618", "049226", "08606E", "1C872C", "2C56DC",
            "381428", "50465D", "704D7B", "88D7F6", "AC220B", "BCEE7B", "D850E6",
            "F832E4",
        )
        add(
            "Linksys",
            "00045A", "000C41", "00125A", "0018F8", "001D7E", "002369", "141BBD",
            "20AA4B", "48F8B3", "58EF68", "687F74", "C0C1C0",
        )
        add("Belkin", "001CDF", "08863B", "94103E", "B4750E", "C05627", "EC1A59")
        add(
            "Huawei",
            "00259E", "045FA7", "1064E2", "20F3A3", "283CE4", "4CB16C", "5CF96A",
            "70723C", "781DBA", "84A8E4", "9C28EF", "AC853D", "C81451", "E0247F",
            "F49FF3",
        )
        add(
            "ZTE",
            "0015EB", "0019C6", "344B50", "4CAC0A", "6CA75F", "981333", "9CD24B",
            "C87B5B", "D8320E", "F46DE2",
        )
        add(
            "Xiaomi",
            "009EC8", "0C1DAF", "142590", "286C07", "3480B3", "50647A", "643E8C",
            "78028F", "8CBEBE", "9C99A0", "A08869", "C46AB7", "F0B429", "FC64BA",
        )
        add(
            "Sagemcom",
            "1CB0AC", "2C3AFD", "48F80D", "5CDC96", "7C0FF9", "84A423", "9C9726",
            "C41D71", "E4CC53", "F494B7",
        )
        add(
            "Technicolor",
            "00147F", "001A2A", "1C1D67", "44E9DD", "5C353B", "685D43", "7C034C",
            "8CFDF0", "A0E70B", "CC03FA", "F81190",
        )
        add(
            "AVM FRITZ!Box",
            "00040E", "001C4A", "3810D5", "5C4979", "9CC7A6", "C80E14", "E0286D",
            "F0B014",
        )
        add(
            "Arris",
            "000039", "0015CE", "00248C", "1071F9", "3C7A8A", "4479B5", "6C6293",
            "94CCB9", "A84E3F", "BC6478", "D404FF", "F81547",
        )
        add(
            "Zyxel",
            "001349", "0019CB", "404A03", "5CE28C", "902B34", "B0B2DC", "CC5D4E",
            "D8ECE5",
        )
        add(
            "MikroTik",
            "00093D", "18FD74", "2CC81B", "48A98A", "6C3B6B", "742F68", "B869F4",
            "CC2DE0", "D401C3", "E48D8C",
        )

        // --- Phones and mobile hotspots ----------------------------------------
        // An open SSID from one of these is somebody's personal hotspot, not
        // public infrastructure.
        add(
            "Apple",
            "001451", "0023DF", "0C3021", "28E02C", "3C0754", "4C8D79", "60FEC5",
            "7CD1C3", "88665A", "A45E60", "B8098A", "D0034B", "E425E7", "F0DBF8",
        )
        add(
            "Samsung",
            "0000F0", "0012FB", "0021D1", "08245F", "1C232C", "34AA8B", "48446E",
            "5CF6DC", "78471D", "8425DB", "94350A", "A00798", "B479A7", "CC07AB",
            "E8508B",
        )
        add(
            "Google",
            "001A11", "3C5AB4", "544E90", "6466B3", "7823AE", "94EB2C", "A47733",
            "D8EB46", "F4F5D8", "F88FCA",
        )

        // --- Streaming sticks, printers and IoT --------------------------------
        // These broadcast open SSIDs but are useless for browsing.
        add("Roku", "008041", "0C1420", "8CAE4C", "B0A737", "CC6DA0", "D83134")
        add(
            "HP",
            "0001E6", "001321", "002655", "3464A9", "6CC217", "945749", "B00CD1",
            "E4E749", "F430B9",
        )
        add("Canon", "001E8F", "0025B3", "2C9EFC", "888721", "C0F392")
        add(
            "Amazon",
            "087190", "3C5CC4", "4CEFC0", "68370E", "747548", "88710E", "A002DC",
            "F0272D", "FCA183",
        )
        add("Tesla", "4CFCAA", "98ED5C", "CC8826")
    }
}
