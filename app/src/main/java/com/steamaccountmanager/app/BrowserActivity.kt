package com.steamaccountmanager.app

import android.os.Bundle
import android.os.Process
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.steamaccountmanager.app.browser.BrowserProcessController
import com.steamaccountmanager.app.browser.GeckoProfileIdentity
import com.steamaccountmanager.app.domain.model.SessionIdentifier
import com.steamaccountmanager.app.ui.browser.BrowserScreen
import com.steamaccountmanager.app.ui.browser.GeckoBrowserScreen
import com.steamaccountmanager.app.ui.theme.SteamAccountManagerTheme
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import java.io.File

/**
 * Hosts one browser session in the dedicated `:browser` process. Steam uses a
 * persistent Gecko profile; other websites retain the reversible WebView path.
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val accountId = intent.getStringExtra(BrowserProcessController.EXTRA_ACCOUNT_ID).orEmpty()
        val websiteId = intent.getStringExtra(BrowserProcessController.EXTRA_WEBSITE_ID).orEmpty()
        val startUrl = intent.getStringExtra(BrowserProcessController.EXTRA_START_URL).orEmpty()
        val allowedDomains =
            intent.getStringArrayListExtra(BrowserProcessController.EXTRA_ALLOWED_DOMAINS).orEmpty()
        val sessionId = SessionIdentifier(accountId, websiteId)
        val routingToken = intent.getStringExtra(BrowserProcessController.EXTRA_ROUTING_TOKEN)
        if (shutdownRequested || accountId.isBlank() || websiteId.isBlank() || startUrl.isBlank() ||
            allowedDomains.isEmpty() || !BrowserProcessController.isLaunchAuthorized(this, sessionId, routingToken)
        ) {
            Log.e(TAG, "BrowserActivity rejected an incomplete or stale browser-session request.")
            super.onCreate(savedInstanceState)
            finish()
            return
        }

        if (websiteId != STEAM_WEBSITE_ID) {
            val suffix = intent.getStringExtra(BrowserProcessController.EXTRA_DATA_DIR_SUFFIX)
            if (suffix.isNullOrBlank()) {
                Log.e(TAG, "BrowserActivity started without a WebView data directory suffix; finishing.")
                super.onCreate(savedInstanceState)
                finish()
                return
            }
            // This remains before super.onCreate and before any WebView construction.
            WebView.setDataDirectorySuffix(suffix)
            super.onCreate(savedInstanceState)
            showWebView(accountId, websiteId, startUrl, allowedDomains)
            return
        }

        val profileId = GeckoProfileIdentity.idFor(sessionId)
        val profile = GeckoProfileIdentity.pathFor(File(noBackupFilesDir, GECKO_PROFILE_ROOT), sessionId)
        if ((!profile.exists() && !profile.mkdirs()) || !profile.isDirectory) {
            Log.e(TAG, "Could not create the isolated Gecko profile directory.")
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        val existing = sharedRuntime
        if (existing != null && sharedProfileId != profileId) {
            Log.e(TAG, "Gecko profile mismatch; refusing to open a second browser session in this process.")
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        val getOrCreateRuntimeAfterConsent = {
            sharedRuntime ?: GeckoRuntime.create(
                applicationContext,
                GeckoRuntimeSettings.Builder().arguments(arrayOf("--profile", profile.absolutePath)).build(),
            ).also {
                sharedRuntime = it
                sharedProfileId = profileId
            }
        }

        val consentPreferences = getSharedPreferences(DETECTOR_CONSENT_PREFERENCES, MODE_PRIVATE)
        val showDetectorConsent = !consentPreferences.getBoolean(detectorConsentKey(profileId), false)
        val quarantinePreferences = getSharedPreferences(CSFLOAT_QUARANTINE_PREFERENCES, MODE_PRIVATE)
        val quarantineKey = csfloatQuarantineKey(profileId)
        val recoveryKey = csfloatRecoveryKey(profileId)
        val initialQuarantine = quarantinePreferences.getBoolean(quarantineKey, false)
        val quarantineRecoveryUrl = if (initialQuarantine) {
            quarantinePreferences.getString(recoveryKey, startUrl) ?: startUrl
        } else {
            startUrl
        }

        super.onCreate(savedInstanceState)
        setContent {
            SteamAccountManagerTheme {
                GeckoBrowserScreen(
                    getOrCreateRuntimeAfterConsent = getOrCreateRuntimeAfterConsent,
                    accountId = accountId,
                    websiteId = websiteId,
                    startUrl = startUrl,
                    allowedDomains = allowedDomains,
                    showDetectorConsent = showDetectorConsent,
                    initialCsfloatQuarantine = initialQuarantine,
                    quarantineRecoveryUrl = quarantineRecoveryUrl,
                    persistCsfloatQuarantine = { active, recoveryUrl ->
                        quarantinePreferences.edit()
                            .putBoolean(quarantineKey, active)
                            .putString(recoveryKey, recoveryUrl)
                            .commit()
                    },
                    persistDetectorConsent = {
                        val consentKey = detectorConsentKey(profileId)
                        persistDetectorConsentFailClosed(
                            persist = {
                                consentPreferences.edit().putBoolean(consentKey, true).commit()
                            },
                            rollbackInMemory = {
                                consentPreferences.edit().putBoolean(consentKey, false).apply()
                            },
                            onRollbackFailure = {
                                Log.e(TAG, "Consent cache rollback failed; terminating isolated browser process.")
                                Process.killProcess(Process.myPid())
                            },
                        )
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun showWebView(
        accountId: String,
        websiteId: String,
        startUrl: String,
        allowedDomains: List<String>,
    ) {
        setContent {
            SteamAccountManagerTheme {
                BrowserScreen(
                    accountId = accountId,
                    websiteId = websiteId,
                    startUrl = startUrl,
                    allowedDomains = allowedDomains,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        private const val TAG = "BrowserActivity"
        private const val STEAM_WEBSITE_ID = "steam"
        private const val GECKO_PROFILE_ROOT = "gecko-browser-profiles"
        internal const val DETECTOR_CONSENT_PREFERENCES = "gecko_detector_consent"
        internal const val CSFLOAT_QUARANTINE_PREFERENCES = "gecko_csfloat_quarantine"
        private const val DETECTOR_CONSENT_VERSION = "steam_profile_detector_consent_v2_"
        private const val CSFLOAT_QUARANTINE_VERSION = "csfloat_quarantine_v1_"
        private const val CSFLOAT_RECOVERY_VERSION = "csfloat_recovery_v1_"

        internal fun detectorConsentKey(profileId: String) = DETECTOR_CONSENT_VERSION + profileId
        internal fun csfloatQuarantineKey(profileId: String) = CSFLOAT_QUARANTINE_VERSION + profileId
        internal fun csfloatRecoveryKey(profileId: String) = CSFLOAT_RECOVERY_VERSION + profileId

        private var sharedRuntime: GeckoRuntime? = null
        private var sharedProfileId: String? = null
        private var shutdownRequested = false

        /** Called inside `:browser`; Gecko gets a graceful profile flush before process death. */
        fun shutdownBrowserProcess() {
            if (shutdownRequested) return
            shutdownRequested = true
            val runtime = sharedRuntime
            if (runtime == null) {
                // Preserve the reversible WebView sessions while they remain in production.
                CookieManager.getInstance().flush()
                Process.killProcess(Process.myPid())
                return
            }
            runtime.delegate = object : GeckoRuntime.Delegate {
                override fun onShutdown() {
                    Process.killProcess(Process.myPid())
                }
            }
            try {
                runtime.shutdown()
            } catch (_: UnsatisfiedLinkError) {
                Log.e(TAG, "Gecko shutdown requested before native runtime initialization; terminating browser worker.")
                Process.killProcess(Process.myPid())
            }
        }
    }
}

internal fun persistDetectorConsentFailClosed(
    persist: () -> Boolean,
    rollbackInMemory: () -> Unit,
    onRollbackFailure: () -> Unit,
): Boolean {
    val persisted = try {
        persist()
    } catch (_: RuntimeException) {
        false
    }
    if (!persisted) {
        try {
            rollbackInMemory()
        } catch (_: RuntimeException) {
            try {
                onRollbackFailure()
            } catch (_: RuntimeException) {
                // Authorization remains denied even if process termination reports an error.
            }
        }
    }
    return persisted
}
