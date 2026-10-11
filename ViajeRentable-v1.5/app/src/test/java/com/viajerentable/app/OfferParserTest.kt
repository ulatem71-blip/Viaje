package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OfferParserTest {
    @Test fun parsesCapturedUberOfferFormat() {
        val parsed = OfferParser.parse("UberX | Exclusivo | CRC1,404.33 | 4.93 (1547) | A 7 min (1.4 km) | C. Lisboa | Viaje: 10 min (2.7 km) | SAN RAFAEL | Aceptar")
        assertNotNull(parsed)
        assertEquals(1404.33, parsed!!.fare, 0.01)
        assertEquals(1.4, parsed.pickupKm, 0.01)
        assertEquals(7.0, parsed.pickupMinutes, 0.01)
        assertEquals(2.7, parsed.tripKm, 0.01)
        assertEquals(10.0, parsed.tripMinutes, 0.01)
    }

    @Test fun rejectsIncompleteText() {
        assertNull(OfferParser.parse("Uber Driver | Busca lugares"))
        assertNull(OfferParser.parse("CRC 150 | 1-2 min"))
    }
}
