package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferClickerPolicyTest {
    private val offer = ParsedOffer(
        fare = 1803.33,
        pickupKm = 1.5,
        pickupMinutes = 6.0,
        tripKm = 4.9,
        tripMinutes = 15.0
    )

    private fun target(
        type: String = "Aceptar",
        enabled: Boolean = true,
        visible: Boolean = true
    ): CardTarget {
        return CardTarget(
            fare = 1803.33,
            cardText = "CRC1,803.33 | A 6 min (1.5 km) | Viaje: 15 min (4.9 km) | $type",
            buttonType = type,
            buttonBoundsInScreen = ScreenBounds(100, 200, 300, 260),
            xBoundsInScreen = ScreenBounds(350, 100, 390, 140),
            isEnabled = enabled,
            isVisibleToUser = visible
        )
    }

    private fun evaluate(
        enabled: Boolean = true,
        card: CardTarget? = target(),
        decision: Decision = Decision.ACEPTARIA,
        parsed: ParsedOffer? = offer,
        maxPickupKm: Double = 2.0,
        ambiguous: Boolean = false,
        fareMatches: Boolean = true
    ): AcceptanceGateResult {
        return AcceptanceDecisionGate.evaluate(
            AcceptanceGateInput(
                enabled,
                card,
                decision,
                parsed,
                maxPickupKm,
                ambiguous,
                fareMatches
            )
        )
    }

    @Test
    fun permitsOnlyWhenEveryPreClickConditionIsSatisfied() {
        assertTrue(evaluate().allowed)
    }

    @Test
    fun rejectsWhenSwitchIsOff() {
        assertFalse(evaluate(enabled = false).allowed)
    }

    @Test
    fun rejectsMeInteresaAndNeverAllowsTheCloseButtonAsPrimaryTarget() {
        assertFalse(evaluate(card = target(type = "Me interesa")).allowed)
        assertFalse(evaluate(card = target(type = "X")).allowed)
    }

    @Test
    fun requiresDemoDecisionAcceptAndCompleteRoute() {
        assertFalse(evaluate(decision = Decision.RECHAZARIA).allowed)
        assertFalse(evaluate(decision = Decision.SIN_DATOS).allowed)
        assertFalse(evaluate(parsed = ParsedOffer(1803.33)).allowed)
    }

    @Test
    fun enforcesConfiguredPickupDistance() {
        assertTrue(evaluate(maxPickupKm = 2.0).allowed)
        assertFalse(evaluate(maxPickupKm = 1.49).allowed)
    }

    @Test
    fun rejectsAmbiguousOrFareMismatchedCards() {
        assertFalse(evaluate(ambiguous = true).allowed)
        assertFalse(evaluate(fareMatches = false).allowed)
        assertFalse(evaluate(card = target().copy(fare = 1804.0)).allowed)
    }

    @Test
    fun requiresButtonEnabledAndVisible() {
        assertFalse(evaluate(card = target(enabled = false)).allowed)
        assertFalse(evaluate(card = target(visible = false)).allowed)
    }

    @Test
    fun twoConsecutiveFalseClickResultsRequireShutdown() {
        val first = AutoAcceptanceSafetyState().afterClick(false)
        assertFalse(first.mustDisable())
        val second = first.afterClick(false)
        assertTrue(second.mustDisable())
        assertFalse(second.afterClick(true).mustDisable())
    }

    @Test
    fun threeTariffDiscrepanciesRequireShutdown() {
        val first = AutoAcceptanceSafetyState().afterDiscrepancy()
        val second = first.afterDiscrepancy()
        assertFalse(second.mustDisable())
        assertTrue(second.afterDiscrepancy().mustDisable())
    }

    @Test
    fun serviceRestartAndLeavingUberForegroundRequireShutdown() {
        val state = AutoAcceptanceSafetyState()
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.SERVICE_RESTART,
                state
            )
        )
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.UBER_LEFT_FOREGROUND,
                state
            )
        )
        assertFalse(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                false,
                AutoAcceptanceShutdownTrigger.SERVICE_RESTART,
                state
            )
        )
    }

    @Test
    fun masterSwitchAndStopAllAlwaysRequestAutomaticAcceptanceShutdown() {
        val state = AutoAcceptanceSafetyState()
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.MASTER_SWITCH_OFF,
                state
            )
        )
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.STOP_ALL,
                state
            )
        )
        assertFalse(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                false,
                AutoAcceptanceShutdownTrigger.STOP_ALL,
                state
            )
        )
    }

    @Test
    fun clickAndDiscrepancyTriggersOnlyShutdownAtTheirThresholds() {
        val clickState = AutoAcceptanceSafetyState().afterClick(false)
        assertFalse(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.CLICK_FAILURE,
                clickState
            )
        )
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.CLICK_FAILURE,
                clickState.afterClick(false)
            )
        )
        val discrepancyState = AutoAcceptanceSafetyState().afterDiscrepancy().afterDiscrepancy()
        assertFalse(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.TARIFF_DISCREPANCY,
                discrepancyState
            )
        )
        assertTrue(
            AutoAcceptanceShutdownPolicy.shouldDisable(
                true,
                AutoAcceptanceShutdownTrigger.TARIFF_DISCREPANCY,
                discrepancyState.afterDiscrepancy()
            )
        )
    }
}
