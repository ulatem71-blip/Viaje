package com.viajerentable.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemPowerStoreTest {
    @Test
    fun systemIsOnByDefault() {
        assertTrue(SystemPowerPolicy.defaultIsOn)
        assertTrue(SystemPowerPolicy.shouldProcess(SystemPowerPolicy.defaultIsOn))
    }

    @Test
    fun disabledSystemDoesNotProcessAccessibilityEvents() {
        assertFalse(SystemPowerPolicy.shouldProcess(false))
    }

    @Test
    fun enabledSystemProcessesAccessibilityEvents() {
        assertTrue(SystemPowerPolicy.shouldProcess(true))
    }
}
