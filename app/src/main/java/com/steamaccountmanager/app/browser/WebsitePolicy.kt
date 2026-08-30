package com.steamaccountmanager.app.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewClientCompat

/**
 * Restricts navigation for a single browser session to its configured website's
 * domain, plus an explicit allowlist of auth-related domains (e.g. Steam's own
 * login/redirect domains for sites that offer "Sign in through Steam"). Anything
 * else is blocked from loading in-app; the app instead offers to open it in the
 * device's default browser (Section 12 / Section 11: "optional open externally").
 */
class WebsitePolicy(
    private val primaryDomain: String,
    private val allowedAuthDomains: List<String>,
) {
    private val allDomains: List<String> = (listOf(primaryDomain) + allowedAuthDomains).map { it.lowercase() }

    fun isHostAllowed(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return allDomains.any { domain -> h == domain || h.endsWith(".$domain") }
    }
}

/**
 * WebViewClient that consults a [WebsitePolicy] on every navigation and blocks
 * anything outside the allowlist, while still permitting normal in-page
 * navigation, redirects, and resource loads within the allowed domains.
 *
 * Legitimate third-party auth redirects (e.g. a site bouncing through
 * steamcommunity.com/openid) are handled by including that domain in the
 * website's `allowedAuthDomains`, configured per-website -- not by disabling
 * the policy.
 */
class DomainRestrictedWebViewClient(
    private val context: Context,
    private val policy: WebsitePolicy,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String) -> Unit,
    private val onBlockedNavigation: (Uri) -> Unit,
    private val onLoadError: (Int, String?) -> Unit,
) : WebViewClientCompat() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme != "https" && uri.scheme != "http") {
            // Let the OS handle non-web schemes (e.g. intent://, mailto:) rather than
            // trying to load them in the WebView.
            return tryLaunchExternalIntent(uri)
        }
        return if (policy.isHostAllowed(uri.host)) {
            false // allow WebView to load it normally
        } else {
            onBlockedNavigation(uri)
            true // we handle it (by not loading it in-app)
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
        url?.let(onPageStarted)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        url?.let(onPageFinished)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: androidx.webkit.WebResourceErrorCompat,
    ) {
        if (request.isForMainFrame) {
            onLoadError(error.errorCode, error.description?.toString())
        }
    }

    private fun tryLaunchExternalIntent(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            true // swallow -- nothing sensible we can do in-app with an unhandled scheme
        }
    }
}
