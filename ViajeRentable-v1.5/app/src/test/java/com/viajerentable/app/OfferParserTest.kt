package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {
    @Test fun parsesDetailedUberOfferFormat() {
        val parsed = OfferParser.parse("UberX | Exclusivo | CRC1,404.33 | 4.93 (1547) | A 7 min (1.4 km) | C. Lisboa | Viaje: 10 min (2.7 km) | SAN RAFAEL - LA UNION | Aceptar")
        assertNotNull(parsed)
        assertEquals(1404.33, parsed!!.fare, 0.01)
        assertEquals(1.4, parsed.pickupKm!!, 0.01)
        assertEquals(7.0, parsed.pickupMinutes!!, 0.01)
        assertEquals(2.7, parsed.tripKm!!, 0.01)
        assertEquals(10.0, parsed.tripMinutes!!, 0.01)
        assertEquals("C. Lisboa", parsed.pickupPlace)
        assertEquals("SAN RAFAEL - LA UNION", parsed.destinationPlace)
        assertTrue(parsed.hasRouteDetails)
    }

    @Test fun detectsCompactUberOfferWithFareOnly() {
        val parsed = OfferParser.parse("UberX | CRC3,206.05 | Identidad digital verificada o Tarjeta | Me interesa")
        assertNotNull(parsed)
        assertEquals(3206.05, parsed!!.fare, 0.01)
        assertFalse(parsed.hasRouteDetails)
        assertNull(parsed.pickupKm)
        assertNull(parsed.destinationPlace)
    }

    @Test fun rejectsIncompleteTextWithoutFare() {
        assertNull(OfferParser.parse("Uber Driver | Busca lugares"))
        assertNull(OfferParser.parse("1-2 min"))
    }
}
