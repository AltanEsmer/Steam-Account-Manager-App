package com.steamaccountmanager.app.ui.browser

import com.steamaccountmanager.app.persistDetectorConsentFailClosed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoConsentGateTest {

    @Test
    fun failedPersistenceKeepsConsentGateClosed() {
        assertFalse(detectorConsentWasPersisted { false })
    }

    @Test
    fun successfulPersistenceAllowsConsentGateToOpen() {
        assertTrue(detectorConsentWasPersisted { true })
    }

    @Test
    fun failedPersistenceRollsBackCachedConsent() {
        var rollbackCount = 0

        assertFalse(
            persistDetectorConsentFailClosed(
                persist = { false },
                rollbackInMemory = { rollbackCount += 1 },
            ),
        )
        assertEquals(1, rollbackCount)
    }

    @Test
    fun successfulPersistenceDoesNotRollBackCachedConsent() {
        var rollbackCount = 0

        assertTrue(
            persistDetectorConsentFailClosed(
                persist = { true },
                rollbackInMemory = { rollbackCount += 1 },
            ),
        )
        assertEquals(0, rollbackCount)
    }
}
