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
        val runtime = existing ?: GeckoRuntime.create(
            applicationContext,
            GeckoRuntimeSettings.Builder().arguments(arrayOf("--profile", profile.absolutePath)).build(),
        ).also {
            sharedRuntime = it
            sharedProfileId = profileId
        }

        val noticePreferences = getSharedPreferences(REAUTH_NOTICE_PREFERENCES, MODE_PRIVATE)
        val showReauthenticationNotice = !noticePreferences.getBoolean(profileId, false)

        super.onCreate(savedInstanceState)
        setContent {
            SteamAccountManagerTheme {
                GeckoBrowserScreen(
                    runtime = runtime,
                    accountId = accountId,
                    websiteId = websiteId,
                    startUrl = startUrl,
                    allowedDomains = allowedDomains,
                    showReauthenticationNotice = showReauthenticationNotice,
                    onReauthenticationNoticeAcknowledged = {
                        noticePreferences.edit().putBoolean(profileId, true).apply()
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
        private const val REAUTH_NOTICE_PREFERENCES = "gecko_reauthentication_notices"

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
            runtime.shutdown()
        }
    }
}
