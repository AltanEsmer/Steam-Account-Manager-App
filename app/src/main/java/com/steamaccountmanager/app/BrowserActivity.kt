package com.steamaccountmanager.app

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.steamaccountmanager.app.browser.BrowserProcessController
import com.steamaccountmanager.app.ui.browser.BrowserScreen
import com.steamaccountmanager.app.ui.theme.SteamAccountManagerTheme

/**
 * The ONLY Android component in this app that ever instantiates a [WebView].
 * Always runs in the dedicated `:browser` process (declared in the manifest) so
 * that [WebView.setDataDirectorySuffix] can be called once per process launch to
 * select a fully isolated on-disk profile for exactly one (account, website)
 * session. See [BrowserProcessController] for the full rationale and the
 * kill-and-restart dance used to switch between sessions.
 */
class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must happen before super.onCreate() / setContent(), and therefore before
        // any WebView is constructed -- this Activity is always the first and only
        // thing that runs in a freshly-spawned :browser process, so this is
        // guaranteed to be the first WebView-related call made in this process.
        val suffix = intent.getStringExtra(BrowserProcessController.EXTRA_DATA_DIR_SUFFIX)
        if (suffix.isNullOrBlank()) {
            Log.e(TAG, "BrowserActivity started without a data directory suffix; finishing.")
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        WebView.setDataDirectorySuffix(suffix)

        super.onCreate(savedInstanceState)

        val accountId = intent.getStringExtra(BrowserProcessController.EXTRA_ACCOUNT_ID).orEmpty()
        val websiteId = intent.getStringExtra(BrowserProcessController.EXTRA_WEBSITE_ID).orEmpty()
        val startUrl = intent.getStringExtra(BrowserProcessController.EXTRA_START_URL).orEmpty()
        val allowedDomains =
            intent.getStringArrayListExtra(BrowserProcessController.EXTRA_ALLOWED_DOMAINS).orEmpty()

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
    }
}
