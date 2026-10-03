package com.shilapi.xcertplay.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Android 8.0/8.1 hotspot code parses BSSIDs as text because `android.net.MacAddress` is API 28. */
class MacAddressTextTest {
    @Test fun formatsSixOctetsTheWayMacAddressDoes() {
        assertEquals(
            "aa:bb:cc:dd:ee:ff",
            MacAddressText.format(byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte())),
        )
        assertEquals("02:00:00:00:00:00", MacAddressText.format(ByteArray(MacAddressText.OCTETS) { if (it == 0) 2 else 0 }))
    }

    @Test fun parsesColonSeparatedText() {
        val bytes = MacAddressText.parse("AA:bB:0c:DD:ee:ff")
        assertArrayEquals(
            byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0x0c.toByte(), 0xdd.toByte(), 0xee.toByte(), 0xff.toByte()),
            bytes,
        )
        assertEquals("aa:bb:0c:dd:ee:ff", MacAddressText.format(bytes))
    }

    @Test fun roundTripsTheBssidTheFrameworkReports() {
        // WifiConfiguration.BSSID carries this shape on Android 8.0 local-only hotspots.
        val text = "10:20:30:40:50:60"
        assertEquals(text, MacAddressText.format(MacAddressText.parse(text)))
    }

    @Test fun rejectsAnythingThatIsNotSixOctets() {
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("") }
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("aa:bb:cc:dd:ee") }
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("aa:bb:cc:dd:ee:ff:00") }
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("aa-bb-cc-dd-ee-ff") }
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("gg:bb:cc:dd:ee:ff") }
        assertThrows(IllegalArgumentException::class.java) { MacAddressText.parse("aa:bb:cc:dd:ee:1ff") }
    }
}
