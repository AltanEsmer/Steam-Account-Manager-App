package com.steamaccountmanager.app.ui.browser

import com.steamaccountmanager.app.persistDetectorConsentFailClosed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoConsentGateTest {

    @Test
    fun failedPersistenceKeepsConsentGateClosed() {
        assertFalse(tryPersistDetectorConsent { false })
    }

    @Test
    fun successfulPersistenceAllowsConsentGateToOpen() {
        assertTrue(tryPersistDetectorConsent { true })
    }

    @Test
    fun failedPersistenceRollsBackCachedConsent() {
        var rollbackCount = 0

        assertFalse(
            persistDetectorConsentFailClosed(
                persist = { false },
                rollbackInMemory = { rollbackCount += 1 },
                onRollbackFailure = {},
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
                onRollbackFailure = {},
            ),
        )
        assertEquals(0, rollbackCount)
    }

    @Test
    fun throwingPersistenceRollsBackAndDoesNotAuthorize() {
        var rolledBack = false

        assertFalse(
            persistDetectorConsentFailClosed(
                persist = { throw IllegalStateException("synthetic persistence failure") },
                rollbackInMemory = { rolledBack = true },
                onRollbackFailure = {},
            ),
        )
        assertTrue(rolledBack)
    }

    @Test
    fun throwingRollbackInvokesFailClosedFallbackAndDoesNotAuthorize() {
        var fallbackInvoked = false

        assertFalse(
            persistDetectorConsentFailClosed(
                persist = { false },
                rollbackInMemory = { throw IllegalStateException("synthetic rollback failure") },
                onRollbackFailure = { fallbackInvoked = true },
            ),
        )
        assertTrue(fallbackInvoked)
    }
}
