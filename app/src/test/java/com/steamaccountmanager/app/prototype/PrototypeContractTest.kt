package com.steamaccountmanager.app.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeContractTest {
    @Test
    fun `consent identity must match the exact CSFloat package`() {
        assertTrue(isExpectedCsfloat(CSFLOAT_ID, CSFLOAT_VERSION))
        assertFalse(isExpectedCsfloat("unexpected@example.invalid", CSFLOAT_VERSION))
        assertFalse(isExpectedCsfloat(CSFLOAT_ID, "5.17.1"))
        assertFalse(isExpectedCsfloat(null, CSFLOAT_VERSION))
    }

    @Test
    fun `install prompt shows callback identity and every callback item verbatim`() {
        val prompt = installPrompt(
            name = "Callback name",
            id = CSFLOAT_ID,
            version = CSFLOAT_VERSION,
            permissions = listOf("nativeMessaging", "tabs", "tabs"),
            origins = listOf("*://*.steampowered.com/*", "https://example.invalid/path"),
            dataCollectionPermissions = listOf("technicalAndInteraction"),
        )

        assertTrue(prompt.contains("Extension: Callback name"))
        assertTrue(prompt.contains("ID: $CSFLOAT_ID"))
        assertTrue(prompt.contains("Version: $CSFLOAT_VERSION"))
        assertTrue(prompt.contains("Permissions (3):\n• nativeMessaging\n• tabs\n• tabs"))
        assertTrue(prompt.contains("Origins (2):\n• *://*.steampowered.com/*\n• https://example.invalid/path"))
        assertTrue(prompt.contains("Data collection (1):\n• technicalAndInteraction"))
    }

    @Test
    fun `diagnostics are fixed allow-listed codes`() {
        assertEquals(
            "GV-INSTALL-FAILED: Could not download or install CSFloat. Check the network and retry.",
            PrototypeDiagnostic.INSTALL_FAILED.message,
        )
        assertEquals(
            "GV-INSTALL-NO-RESULT: GeckoView returned no installed extension. Retry.",
            PrototypeDiagnostic.INSTALL_NO_RESULT.message,
        )
        assertEquals(
            "GV-PAGE-LOAD-FAILED: The public Steam listing did not load. Check the network and retry.",
            PrototypeDiagnostic.PAGE_LOAD_FAILED.message,
        )
        PrototypeDiagnostic.entries.forEach { diagnostic ->
            assertFalse(diagnostic.message.contains("steamLoginSecure=secret-token"))
        }
    }

    @Test
    fun `denial reports only expected extension engine state`() {
        assertEquals(DenialState.EXPECTED_ABSENT, denialState(emptyList()))
        assertEquals(DenialState.EXPECTED_DISABLED, denialState(listOf(false)))
        assertEquals(DenialState.EXPECTED_ENABLED, denialState(listOf(false, true)))
        assertEquals(
            "GV-INSTALL-DENIED-ABSENT: Consent denied; engine reports expected CSFloat ID absent. Retry is available.",
            denialMessage(DenialState.EXPECTED_ABSENT),
        )
        assertEquals(
            "GV-INSTALL-DENIED-DISABLED: Consent denied; engine reports expected CSFloat ID disabled. Retry is available.",
            denialMessage(DenialState.EXPECTED_DISABLED),
        )
        assertEquals(
            "GV-INSTALL-DENIED-ENABLED: Consent denied, but engine reports expected CSFloat ID enabled. Close the prototype.",
            denialMessage(DenialState.EXPECTED_ENABLED),
        )
    }
}
