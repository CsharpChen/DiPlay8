package com.shilapi.xcertplay.network

/**
 * Colon-separated MAC address text handling for the Wi-Fi hotspot paths.
 *
 * `android.net.MacAddress` only exists from API 28 (Android 9), while the local hotspot and the
 * manual hotspot must also work on Android 8.0/8.1, so those paths parse and format the text
 * themselves instead of loading a class their platform does not have.
 */
internal object MacAddressText {
    /** A MAC address is always six octets. */
    const val OCTETS = 6

    /** Formats six octets the way `android.net.MacAddress.toString()` does: lowercase and colon-separated. */
    fun format(bytes: ByteArray): String =
        bytes.joinToString(":") { "%02x".format(it.toInt() and 0xff) }

    /**
     * Parses "aa:bb:cc:dd:ee:ff".
     *
     * @throws IllegalArgumentException when the text is not exactly six colon-separated octets,
     * which is how `android.net.MacAddress.fromString` rejected input.
     */
    fun parse(value: String): ByteArray {
        val parts = value.split(':')
        require(parts.size == OCTETS) { "'$value' is not a MAC address" }
        return ByteArray(OCTETS) { index ->
            val octet = parts[index].toIntOrNull(16)
            require(octet != null && octet in 0..0xff) { "'$value' is not a MAC address" }
            octet.toByte()
        }
    }
}
