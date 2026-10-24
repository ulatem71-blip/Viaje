package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscardDecisionGateTest {
    private val offer = ParsedOffer(1803.33, 1.5, 6.0, 4.9, 15.0)
    private val target = CardTarget(
        fare = 1803.33,
        cardText = "CRC1803.33 | Viaje: 15 min (4.9 km)",
        buttonType = "Aceptar",
        buttonBoundsInScreen = ScreenBounds(100, 400, 300, 460),
        xBoundsInScreen = ScreenBounds(470, 115, 600, 245),
        isEnabled = true,
        isVisibleToUser = true,
        cardBoundsInScreen = ScreenBounds(100, 100, 600, 500)
    )

    private fun evaluate(
        enabled: Boolean = true,
        decision: Decision = Decision.RECHAZARIA,
        parsed: ParsedOffer? = offer,
        ambiguous: Boolean = false,
        fareMatches: Boolean = true,
        xBounds: ScreenBounds? = target.xBoundsInScreen
    ): DiscardGateResult {
        return DiscardDecisionGate.evaluate(
            DiscardGateInput(enabled, target.copy(xBoundsInScreen = xBounds), decision, parsed, ambiguous, fareMatches)
        )
    }

    @Test
    fun permitsOnlyRejectableCompleteUnambiguousMatchingOffers() {
        assertTrue(evaluate().allowed)
        assertFalse(evaluate(enabled = false).allowed)
        assertFalse(evaluate(decision = Decision.ACEPTARIA).allowed)
        assertFalse(evaluate(decision = Decision.SIN_DATOS).allowed)
        assertFalse(evaluate(parsed = ParsedOffer(1803.33)).allowed)
        assertFalse(evaluate(ambiguous = true).allowed)
        assertFalse(evaluate(fareMatches = false).allowed)
        assertFalse(evaluate(xBounds = null).allowed)
    }
}
