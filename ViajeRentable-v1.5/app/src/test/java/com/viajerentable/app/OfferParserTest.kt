package com.viajerentable.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {
    @Test
    fun detailedOffer() {
        val offer = OfferParser.parse(
            "UberX | Exclusivo | CRC1,404.33 | 4.93 (1547) | " +
                "A 7 min (1.4 km) | C. Lisboa | Viaje: 10 min (2.7 km) | " +
                "SAN RAFAEL - LA UNION | Aceptar"
        )

        assertNotNull(offer)
        assertEquals(1404.33, offer!!.fare, 0.01)
        assertEquals(1.4, offer.pickupKm!!, 0.01)
        assertEquals(7.0, offer.pickupMinutes!!, 0.01)
        assertEquals(2.7, offer.tripKm!!, 0.01)
        assertEquals(10.0, offer.tripMinutes!!, 0.01)
        assertEquals("C. Lisboa", offer.pickupPlace)
        assertEquals("SAN RAFAEL - LA UNION", offer.destinationPlace)
        assertTrue(offer.hasRouteDetails)
    }

    @Test
    fun compactOffer() {
        val offer = OfferParser.parse(
            "UberX | CRC3,206.05 | Identidad digital verificada o Tarjeta | Me interesa"
        )

        assertNotNull(offer)
        assertEquals(3206.05, offer!!.fare, 0.01)
        assertFalse(offer.hasRouteDetails)
        assertNull(offer.pickupKm)
    }

    @Test
    fun hours() {
        assertEquals(
            65.0,
            OfferParser.parse(
                "UberX | CRC9,500.00 | A 7 min (1.4 km) | " +
                    "Viaje: 1 h 5 min (45 km) | Aceptar"
            )!!.tripMinutes!!,
            0.001
        )
        assertEquals(
            60.0,
            OfferParser.parse(
                "UberX | CRC9,500.00 | A 7 min (1.4 km) | Viaje: 1 h (30 km) | Aceptar"
            )!!.tripMinutes!!,
            0.001
        )
        assertEquals(
            65.0,
            OfferParser.parse(
                "UberX | CRC9,500.00 | A 7 min (1.4 km) | " +
                    "Viaje: 1 hora 5 minutos (45 km) | Aceptar"
            )!!.tripMinutes!!,
            0.001
        )
    }

    @Test
    fun metersAndFarePunctuation() {
        val offer = OfferParser.parse(
            "UberX | CRC1,404.33 | A 2 min (300 m) | " +
                "Viaje: 10 min (2.7 km) | Aceptar"
        )
        assertNotNull(offer)
        assertEquals(0.3, offer!!.pickupKm!!, 0.001)
        assertTrue(offer.hasRouteDetails)
        assertEquals(
            1404.33,
            OfferParser.parse(
                "UberX | CRC1,404.33. | A 7 min (1.4 km) | " +
                    "Viaje: 10 min (2.7 km) | Aceptar"
            )!!.fare,
            0.001
        )
    }

    @Test
    fun dotThousandsAndLimits() {
        assertEquals(
            1404.0,
            OfferParser.parse(
                "UberX | ₡1.404 | A 7 min (1.4 km) | Viaje: 10 min (2.7 km) | Aceptar"
            )!!.fare,
            0.001
        )
        assertEquals(1404.0, OfferParser.parseNumber("1.404")!!, 0.001)
        assertNull(OfferParser.parse("Ganancias de hoy | ₡15,230 | 8 viajes | Cuenta"))
        assertNull(
            OfferParser.parse(
                "UberX | CRC100 | A 2 min (300 m) | Viaje: 10 min (2.7 km) | Aceptar"
            )
        )
        assertNull(
            OfferParser.parse(
                "UberX | CRC300000 | A 2 min (300 m) | Viaje: 10 min (2.7 km) | Aceptar"
            )
        )
    }

    @Test
    fun rejectsMissingFare() {
        assertNull(OfferParser.parse("Uber Driver | Busca lugares"))
        assertNull(OfferParser.parse("1-2 min"))
    }

    @Test
    fun parsesCompactAndDetailedCardsWithoutMixingTheirData() {
        val offers = OfferParser.parseAll(
            "UberX | CRC800.00 | Me interesa | UberX | Exclusivo | CRC3,308.35 | " +
                "4.93 (1547) | A 8 min (3.0 km) | C. Lisboa | " +
                "Viaje: 34 min (9.1 km) | SAN RAFAEL | Aceptar"
        )

        assertEquals(2, offers.size)
        assertEquals(800.0, offers[0].fare, 0.001)
        assertFalse(offers[0].hasRouteDetails)
        assertEquals(3308.35, offers[1].fare, 0.001)
        assertTrue(offers[1].hasRouteDetails)
        assertEquals(3.0, offers[1].pickupKm!!, 0.001)
        assertEquals(8.0, offers[1].pickupMinutes!!, 0.001)
        assertEquals(9.1, offers[1].tripKm!!, 0.001)
        assertEquals(34.0, offers[1].tripMinutes!!, 0.001)
        assertFalse(offers.any { it.fare == 800.0 && it.hasRouteDetails })
    }

    @Test
    fun parsesDetailedCardFirstAndCompactCardSecond() {
        val offers = OfferParser.parseAll(
            "UberX | Exclusivo | CRC3,308.35 | 4.93 (1547) | " +
                "A 8 min (3.0 km) | C. Lisboa | Viaje: 34 min (9.1 km) | " +
                "SAN RAFAEL | Aceptar | UberX | CRC800.00 | Me interesa"
        )

        assertEquals(2, offers.size)
        assertEquals(3308.35, offers[0].fare, 0.001)
        assertTrue(offers[0].hasRouteDetails)
        assertEquals(3.0, offers[0].pickupKm!!, 0.001)
        assertEquals(8.0, offers[0].pickupMinutes!!, 0.001)
        assertEquals(9.1, offers[0].tripKm!!, 0.001)
        assertEquals(34.0, offers[0].tripMinutes!!, 0.001)
        assertEquals(800.0, offers[1].fare, 0.001)
        assertFalse(offers[1].hasRouteDetails)
    }

    @Test
    fun parseReturnsFirstCardAndSingleCardMatchesParseAll() {
        val singleCard =
            "UberX | Exclusivo | CRC3,308.35 | 4.93 (1547) | " +
                "A 8 min (3.0 km) | C. Lisboa | Viaje: 34 min (9.1 km) | " +
                "SAN RAFAEL | Aceptar"
        val all = OfferParser.parseAll(singleCard)
        val parsed = OfferParser.parse(singleCard)

        assertEquals(1, all.size)
        assertEquals(all.first(), parsed)
    }

    @Test
    fun textWithoutButtonProducesNoOffers() {
        val offers = OfferParser.parseAll(
            "UberX | CRC800.00 | Me interesa | UberX | CRC3,308.35"
        )

        assertTrue(offers.isEmpty())
    }
}
