package com.steamaccountmanager.app.ui.browser

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.steamaccountmanager.app.browser.DomainRestrictedWebViewClient
import com.steamaccountmanager.app.browser.SteamLoginDetector
import com.steamaccountmanager.app.browser.WebsitePolicy

/**
 * Renders one isolated browser session. This composable never needs to know
 * about accounts other than the one it was launched for -- isolation is already
 * guaranteed at the process/data-directory level by the time this screen exists
 * (see [com.steamaccountmanager.app.BrowserActivity]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    accountId: String,
    websiteId: String,
    startUrl: String,
    allowedDomains: List<String>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf(websiteId) }
    var blockedUri by remember { mutableStateOf<Uri?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val policy = remember(websiteId, allowedDomains) {
        WebsitePolicy(primaryDomain = allowedDomains.firstOrNull().orEmpty(), allowedAuthDomains = allowedDomains.drop(1))
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrowserTopBar(
            title = currentTitle,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = { webViewRef?.goBack() },
            onForward = { webViewRef?.goForward() },
            onRefresh = { webViewRef?.reload() },
            onOpenExternally = {
                webViewRef?.url?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
            onClose = onClose,
        )

        if (isLoading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        configureSecureSettings(this)

                        // Third-party cookies must be explicitly enabled per-WebView on
                        // modern WebView; this only affects cookies *within this
                        // process's already-isolated data directory*, so it does not
                        // weaken account isolation -- it's required for normal
                        // "sign in through Steam" style OAuth/OpenID redirects to work.
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        CookieManager.getInstance().setAcceptCookie(true)

                        webViewClient = DomainRestrictedWebViewClient(
                            context = ctx,
                            policy = policy,
                            onPageStarted = { isLoading = true },
                            onPageFinished = { url ->
                                isLoading = false
                                canGoBack = this.canGoBack()
                                canGoForward = this.canGoForward()
                                currentTitle = this.title?.takeIf { it.isNotBlank() } ?: websiteId
                                loadError = null
                                if (websiteId == "steam" && SteamLoginDetector.looksLikeLoggedInSteamPage(url)) {
                                    SteamLoginDetector.tryDetect(this, accountId, context.applicationContext)
                                }
                            },
                            onBlockedNavigation = { uri -> blockedUri = uri },
                            onLoadError = { _, description -> loadError = description ?: "This website could not be reached." },
                        )
                        webChromeClient = android.webkit.WebChromeClient()

                        loadUrl(startUrl)
                        webViewRef = this
                    }
                },
                onRelease = { view ->
                    // Deliberately do NOT clear cookies/cache/history here. Session data
                    // must outlive this Composable/Activity -- it is only released from
                    // memory, never deleted from disk, when the user navigates away.
                    view.stopLoading()
                    // Flush cookies to disk to ensure login state persists across sessions
                    CookieManager.getInstance().flush()
                    (view.parent as? ViewGroup)?.removeView(view)
                    view.destroy()
                },
            )

            loadError?.let { message ->
                ErrorOverlay(message = message, onRetry = { webViewRef?.reload() })
            }
        }
    }

    blockedUri?.let { uri ->
        BlockedNavigationSheet(
            uri = uri,
            onOpenExternally = {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                blockedUri = null
            },
            onDismiss = { blockedUri = null },
        )
    }
}

private fun configureSecureSettings(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        // Sites need to stay logged in across app restarts -- that's the whole point --
        // so we do not force a cache-clearing policy here beyond WebView defaults.
        cacheMode = WebSettings.LOAD_DEFAULT

        // File access hardening: never let page JS reach the filesystem.
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false

        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        setGeolocationEnabled(false)
        userAgentString = userAgentString?.replace("; wv", "") // present as a normal mobile browser to sites that reject WebView UAs
    }
    webView.isVerticalScrollBarEnabled = true
}

@Composable
private fun BrowserTopBar(
    title: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenExternally: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    maxLines = 1,
                )
                IconButton(onClick = onBack, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = onForward, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onOpenExternally) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open externally")
                }
            }
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
private fun BlockedNavigationSheet(uri: Uri, onOpenExternally: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leaving this website") },
        text = { Text("This link goes to ${uri.host}, which is outside this website's allowed domain. Open it in your browser instead?") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onOpenExternally) { Text("Open externally") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Stay here") } },
    )
}
