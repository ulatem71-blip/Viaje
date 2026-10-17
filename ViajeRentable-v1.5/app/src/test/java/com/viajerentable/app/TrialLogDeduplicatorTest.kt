package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialLogDeduplicatorTest {
    @Test
    fun sameFareButtonAndDecisionAreSuppressedForFifteenSeconds() {
        val deduplicator = TrialLogDeduplicator()

        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA", 1000L))
        assertFalse(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA", 16000L))
        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA", 16001L))
    }

    @Test
    fun differentButtonOrDecisionHasIndependentKey() {
        val deduplicator = TrialLogDeduplicator()

        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA", 1000L))
        assertTrue(deduplicator.shouldRecord("1803.33", "Me interesa", "ACEPTARIA", 1001L))
        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "RECHAZARIA", 1002L))
        assertTrue(deduplicator.shouldRecord("2000.00", "Aceptar", "ACEPTARIA", 1003L))
    }

    @Test
    fun injectedClockControlsExpiry() {
        var now = 500L
        val deduplicator = TrialLogDeduplicator(clock = { now })

        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA"))
        assertFalse(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA"))
        now += TrialLogDeduplicator.DEFAULT_WINDOW_MS + 1L
        assertTrue(deduplicator.shouldRecord("1803.33", "Aceptar", "ACEPTARIA"))
    }
}
