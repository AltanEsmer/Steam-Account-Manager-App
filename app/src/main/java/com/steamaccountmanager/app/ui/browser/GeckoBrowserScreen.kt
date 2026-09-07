package com.steamaccountmanager.app.ui.browser

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.steamaccountmanager.app.browser.SteamLoginDetector
import com.steamaccountmanager.app.browser.WebsitePolicy
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

/** The production GeckoView surface for the built-in Steam browser journey. */
@Composable
fun GeckoBrowserScreen(
    getOrCreateRuntimeAfterConsent: () -> GeckoRuntime,
    accountId: String,
    websiteId: String,
    startUrl: String,
    allowedDomains: List<String>,
    showDetectorConsent: Boolean,
    persistDetectorConsent: () -> Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val policy = remember(allowedDomains) {
        WebsitePolicy(allowedDomains.firstOrNull().orEmpty(), allowedDomains.drop(1))
    }
    var sessionRef by remember { mutableStateOf<GeckoSession?>(null) }
    var detectorExtensionRef by remember { mutableStateOf<WebExtension?>(null) }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var title by remember { mutableStateOf(websiteId) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var blockedUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var failedUrl by remember { mutableStateOf<String?>(null) }
    var showConsent by remember { mutableStateOf(showDetectorConsent) }
    var consentPersistenceFailed by remember { mutableStateOf(false) }

    fun openExternal(uri: Uri) {
        if (policy.decideNavigation(uri.toString()) == WebsitePolicy.NavigationDecision.REJECT) {
            error = REJECTED_NAVIGATION_MESSAGE
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            error = "No browser is available to open this link."
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Allow Steam profile detection?") },
            text = {
                Text(
                    "Steam opens in an isolated browser, and your existing WebView sign-in cannot be migrated, " +
                        "so you may need to sign in again. This app includes a profile detector limited to " +
                        "https://steamcommunity.com and https://www.steamcommunity.com. It reads only visible " +
                        "public avatar and profile links and sends those values only back to this app through " +
                        "its internal connection." +
                        if (consentPersistenceFailed) {
                            " Consent could not be saved. Try again or cancel."
                        } else {
                            ""
                        },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tryPersistDetectorConsent(persistDetectorConsent)) {
                        consentPersistenceFailed = false
                        showConsent = false
                    } else {
                        consentPersistenceFailed = true
                    }
                }) { Text("Allow and continue") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        )
        return
    }

    val runtime = remember { getOrCreateRuntimeAfterConsent() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                Text(title, modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1)
                IconButton(onClick = { sessionRef?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                IconButton(onClick = { sessionRef?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                }
                IconButton(onClick = { sessionRef?.reload() }) { Icon(Icons.Filled.Refresh, "Refresh") }
                IconButton(onClick = { openExternal(Uri.parse(currentUrl)) }) {
                    Icon(Icons.Filled.OpenInBrowser, "Open externally")
                }
            }
        }
        if (loading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val geckoView = GeckoView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                    val session = GeckoSession()
                    session.navigationDelegate = object : GeckoSession.NavigationDelegate {
                        override fun onCanGoBack(session: GeckoSession, value: Boolean) {
                            canGoBack = value
                        }

                        override fun onCanGoForward(session: GeckoSession, value: Boolean) {
                            canGoForward = value
                        }

                        override fun onLocationChange(
                            session: GeckoSession,
                            url: String?,
                            perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                            hasUserGesture: Boolean,
                        ) {
                            url?.let { currentUrl = it }
                        }

                        override fun onLoadRequest(
                            session: GeckoSession,
                            request: GeckoSession.NavigationDelegate.LoadRequest,
                        ): GeckoResult<AllowOrDeny> {
                            val decision = policy.decideNavigation(request.uri)
                            if (decision == WebsitePolicy.NavigationDecision.OFFER_EXTERNAL) {
                                blockedUri = Uri.parse(request.uri)
                            } else if (decision == WebsitePolicy.NavigationDecision.REJECT) {
                                error = REJECTED_NAVIGATION_MESSAGE
                            }
                            if (decision == WebsitePolicy.NavigationDecision.ALLOW_IN_APP &&
                                request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
                            ) {
                                Handler(Looper.getMainLooper()).post { session.loadUri(request.uri) }
                                return GeckoResult.fromValue(AllowOrDeny.DENY)
                            }
                            return GeckoResult.fromValue(
                                if (decision == WebsitePolicy.NavigationDecision.ALLOW_IN_APP) {
                                    AllowOrDeny.ALLOW
                                } else {
                                    AllowOrDeny.DENY
                                },
                            )
                        }

                        override fun onLoadError(
                            session: GeckoSession,
                            uri: String?,
                            webRequestError: WebRequestError,
                        ): GeckoResult<String>? {
                            error = if (
                                uri != null &&
                                policy.decideNavigation(uri) == WebsitePolicy.NavigationDecision.REJECT
                            ) {
                                REJECTED_NAVIGATION_MESSAGE
                            } else {
                                failedUrl = uri
                                "This website could not be reached."
                            }
                            return null
                        }
                    }
                    session.progressDelegate = object : GeckoSession.ProgressDelegate {
                        override fun onPageStart(session: GeckoSession, url: String) {
                            loading = true
                            progress = 0f
                            error = null
                            failedUrl = null
                        }

                        override fun onProgressChange(session: GeckoSession, value: Int) {
                            progress = value.coerceIn(0, 100) / 100f
                        }

                        override fun onPageStop(session: GeckoSession, success: Boolean) {
                            loading = false
                            if (!success && error == null) error = "This website could not be reached."
                        }
                    }
                    session.contentDelegate = object : GeckoSession.ContentDelegate {
                        override fun onTitleChange(session: GeckoSession, value: String?) {
                            title = value?.takeIf { it.isNotBlank() } ?: websiteId
                        }
                    }

                    session.open(runtime)
                    geckoView.setSession(session)
                    runtime.webExtensionController.setTabActive(session, true)
                    sessionRef = session

                    runtime.webExtensionController.ensureBuiltIn(DETECTOR_URI, DETECTOR_EXTENSION_ID).accept(
                        { extension -> geckoView.post {
                            if (sessionRef === session && extension?.id == DETECTOR_EXTENSION_ID) {
                                detectorExtensionRef = extension
                                session.webExtensionController.setMessageDelegate(
                                    extension,
                                    detectorDelegate(extension, session, accountId, context.applicationContext),
                                    DETECTOR_NATIVE_APP,
                                )
                            }
                            session.loadUri(startUrl)
                        } },
                        { geckoView.post {
                            if (sessionRef === session) {
                                error = "Steam profile image detection is unavailable. You can still sign in and browse."
                                session.loadUri(startUrl)
                            }
                        } },
                    )
                    geckoView
                },
                onRelease = { view ->
                    detectorExtensionRef?.let { extension ->
                        sessionRef?.webExtensionController?.setMessageDelegate(
                            extension,
                            null,
                            DETECTOR_NATIVE_APP,
                        )
                    }
                    detectorExtensionRef = null
                    sessionRef?.let { session ->
                        runtime.webExtensionController.setTabActive(session, false)
                        view.releaseSession()
                        session.close()
                    }
                    sessionRef = null
                },
            )
            error?.let { message ->
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message, modifier = Modifier.padding(20.dp))
                        TextButton(onClick = {
                            val retryUrl = failedUrl
                            error = null
                            if (retryUrl != null) sessionRef?.loadUri(retryUrl) else sessionRef?.reload()
                        }) { Text("Try again") }
                    }
                }
            }
        }
    }

    blockedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { blockedUri = null },
            title = { Text("Leaving this website") },
            text = { Text("This link goes outside the allowed domains. Open it in your browser instead?") },
            confirmButton = {
                TextButton(onClick = { blockedUri = null; openExternal(uri) }) { Text("Open externally") }
            },
            dismissButton = { TextButton(onClick = { blockedUri = null }) { Text("Stay here") } },
        )
    }
}

internal fun tryPersistDetectorConsent(persist: () -> Boolean): Boolean = try {
    persist()
} catch (_: RuntimeException) {
    false
}

private fun detectorDelegate(
    extension: WebExtension,
    selectedSession: GeckoSession,
    accountId: String,
    appContext: android.content.Context,
) = object : WebExtension.MessageDelegate {
    override fun onConnect(port: WebExtension.Port) {
        val sender = port.sender
        val senderIsValid = port.name == DETECTOR_NATIVE_APP &&
            sender.webExtension.id == DETECTOR_EXTENSION_ID && sender.webExtension.id == extension.id &&
            sender.environmentType == WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT &&
            sender.session === selectedSession && sender.isTopLevel &&
            SteamLoginDetector.looksLikeLoggedInSteamPage(sender.url)
        if (!senderIsValid) {
            port.disconnect()
            return
        }
        port.setDelegate(object : WebExtension.PortDelegate {
            override fun onPortMessage(message: Any, sourcePort: WebExtension.Port) {
                try {
                    if (sourcePort !== port || message !is JSONObject || message.optString("type") != "profile") return
                    val allowedKeys = setOf("type", "avatarUrl", "profileUrl")
                    val keys = message.keys().asSequence().toSet()
                    if (keys != allowedKeys) return
                    SteamLoginDetector.parseResult(message.toString())?.let {
                        SteamLoginDetector.sendResult(it, accountId, appContext)
                    }
                } finally {
                    sourcePort.disconnect()
                }
            }
        })
    }
}

private const val DETECTOR_URI = "resource://android/assets/steam-profile-detector/"
private const val DETECTOR_EXTENSION_ID = "steam-profile-detector@steam-account-manager.invalid"
private const val DETECTOR_NATIVE_APP = "steamProfileDetector"
private const val REJECTED_NAVIGATION_MESSAGE = "This link cannot be opened safely. You can stay here and try another link."
