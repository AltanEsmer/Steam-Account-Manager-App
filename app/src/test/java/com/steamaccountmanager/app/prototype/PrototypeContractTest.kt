package com.steamaccountmanager.app.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeContractTest {
    @Test
    fun `pending consent preserves every callback item verbatim`() {
        val permissions = listOf("nativeMessaging", "tabs", "tabs")
        val origins = listOf("*://*.steampowered.com/*", "https://example.invalid/path")
        val dataCollection = listOf("technicalAndInteraction")

        val pending = PrototypeConsent.pending(permissions, origins, dataCollection)

        assertEquals(permissions, pending.permissions)
        assertEquals(origins, pending.origins)
        assertEquals(dataCollection, pending.dataCollectionPermissions)
    }

    @Test
    fun `consent requires an explicit deny or allow decision`() {
        val pending = PrototypeConsent.pending(listOf("tabs"), emptyList(), emptyList())

        assertFalse(pending.isAccepted)
        assertEquals(ConsentDecision.DENIED, pending.deny().decision)
        assertEquals(ConsentDecision.ACCEPTED, pending.allow().decision)
        assertTrue(pending.allow().isAccepted)
    }

    @Test
    fun `diagnostics cannot echo arbitrary sensitive input`() {
        val secret = "steamLoginSecure=secret-token"

        val diagnostic = PrototypeDiagnostic.installFailure(IllegalStateException(secret))

        assertEquals("Extension installation failed. Retry or open the page externally.", diagnostic)
        assertFalse(diagnostic.contains(secret))
    }
}
