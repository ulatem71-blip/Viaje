package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscardXPolicyTest {
    private val bounds = ScreenBounds(100, 100, 600, 500)

    private fun candidate(rect: ScreenBounds = ScreenBounds(470, 115, 600, 245)): XCandidate {
        return XCandidate("android.widget.Button", null, null, true, true, true, rect)
    }

    @Test
    fun recognizesOneXButRejectsTwoCandidates() {
        assertTrue(DiscardXRecognition.selectSingle(bounds, listOf(candidate())) != null)
        val second = candidate(ScreenBounds(450, 210, 580, 340))
        assertTrue(DiscardXRecognition.selectSingle(bounds, listOf(candidate(), second)) == null)
    }

    @Test
    fun rejectsWrongSizeAndXOutsideCard() {
        val small = candidate(ScreenBounds(550, 110, 600, 160))
        val outside = candidate(ScreenBounds(610, 110, 740, 240))
        assertFalse(DiscardXRecognition.isValid(bounds, small))
        assertFalse(DiscardXRecognition.isValid(bounds, outside))
    }

    @Test
    fun rejectsDisabledInvisibleAndLabelledX() {
        assertFalse(DiscardXRecognition.isValid(bounds, candidate().copy(enabled = false)))
        assertFalse(DiscardXRecognition.isValid(bounds, candidate().copy(visible = false)))
        assertFalse(DiscardXRecognition.isValid(bounds, candidate().copy(text = "Cerrar")))
    }

    @Test
    fun fingerprintExpiresAfterTenMinutes() {
        val ledger = ExpiringFingerprintLedger()
        ledger.add("offer-x", 1000L)
        val atThree = ExpiringFingerprintLedger.fromStored(ledger.storedEntries(), 181000L)
        assertTrue(atThree.contains("offer-x", 181000L))
        val atEleven = ExpiringFingerprintLedger.fromStored(ledger.storedEntries(), 661000L)
        assertFalse(atEleven.contains("offer-x", 661000L))
    }

    @Test
    fun consecutiveDiscardLimitStopsAtFifteenAndResetsAfterAcceptance() {
        val state = (1..15).fold(ConsecutiveDiscardState()) { current, _ -> current.afterDiscard() }
        assertTrue(state.mustDisable())
        assertFalse(state.afterAcceptance().mustDisable())
    }
}
