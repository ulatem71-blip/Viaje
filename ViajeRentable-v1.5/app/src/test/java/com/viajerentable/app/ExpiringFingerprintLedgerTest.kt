package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiringFingerprintLedgerTest {
    @Test
    fun sameOfferIsBlockedAtThreeMinutesAndAllowedAtElevenMinutes() {
        val first = ExpiringFingerprintLedger()
        first.add("offer-a", 1_000L)

        val atThreeMinutes = ExpiringFingerprintLedger.fromStored(
            first.storedEntries(),
            1_000L + 3 * 60 * 1000L
        )
        assertTrue(atThreeMinutes.contains("offer-a", 1_000L + 3 * 60 * 1000L))

        val atElevenMinutes = ExpiringFingerprintLedger.fromStored(
            first.storedEntries(),
            1_000L + 11 * 60 * 1000L
        )
        assertFalse(atElevenMinutes.contains("offer-a", 1_000L + 11 * 60 * 1000L))
    }

    @Test
    fun storageExpiresOldEntriesAndKeepsAtMostFifty() {
        val ledger = ExpiringFingerprintLedger()
        for (index in 0 until 60) {
            ledger.add("offer-$index", index.toLong())
        }

        assertTrue(ledger.storedEntries().size <= 50)
        val expired = ExpiringFingerprintLedger.fromStored(
            ledger.storedEntries(),
            ExpiringFingerprintLedger.DEFAULT_TTL_MS + 100L
        )
        assertTrue(expired.storedEntries().isEmpty())
    }
}
