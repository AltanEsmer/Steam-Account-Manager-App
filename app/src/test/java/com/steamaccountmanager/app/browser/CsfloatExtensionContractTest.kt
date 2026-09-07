package com.steamaccountmanager.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsfloatExtensionContractTest {
    @Test
    fun productionContractPinsTheReviewedOfficialPackage() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi",
            CsfloatExtensionContract.XPI_URL,
        )
        assertEquals("{194d0dc6-7ada-41c6-88b8-95d7636fe43c}", CsfloatExtensionContract.ID)
        assertEquals("5.17.0", CsfloatExtensionContract.VERSION)
        assertEquals(2, CsfloatExtensionContract.SIGNED_STATE)
        assertEquals(
            "70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D",
            CsfloatExtensionContract.SHA256,
        )
        assertTrue(CsfloatExtensionContract.isExpected(CsfloatExtensionContract.ID, "5.17.0", 2))
        assertFalse(CsfloatExtensionContract.isExpected(CsfloatExtensionContract.ID, "5.17.1", 2))
    }
}
