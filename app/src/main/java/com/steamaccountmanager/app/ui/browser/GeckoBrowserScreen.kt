package com.steamaccountmanager.app.ui.browser

import android.content.Intent
import android.app.Dialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.content.pm.ApplicationInfo
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.steamaccountmanager.app.browser.CsfloatExtensionContract
import com.steamaccountmanager.app.browser.CsfloatDenialState
import com.steamaccountmanager.app.browser.CsfloatPopupState
import com.steamaccountmanager.app.browser.CsfloatPopupStatus
import com.steamaccountmanager.app.browser.csfloatDenialMessage
import java.io.File
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
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
    initialCsfloatQuarantine: Boolean,
    persistCsfloatQuarantine: (Boolean) -> Boolean,
    persistDetectorConsent: () -> Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val debugBuild = remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    val scope = rememberCoroutineScope()
    val policy = remember(allowedDomains) {
        WebsitePolicy(allowedDomains.firstOrNull().orEmpty(), allowedDomains.drop(1))
    }
    var sessionRef by remember { mutableStateOf<GeckoSession?>(null) }
    var detectorExtensionRef by remember { mutableStateOf<WebExtension?>(null) }
    var csfloatExtensionRef by remember { mutableStateOf<WebExtension?>(null) }
    var csfloatPopupUri by remember { mutableStateOf<String?>(null) }
    var csfloatState by remember { mutableStateOf("CSFloat: checking installed state…") }
    var csfloatBusy by remember { mutableStateOf(false) }
    var installPromptText by remember { mutableStateOf<String?>(null) }
    var installPromptResult by remember { mutableStateOf<GeckoResult<WebExtension.PermissionPromptResponse>?>(null) }
    var installAllowsDataCollection by remember { mutableStateOf(false) }
    var installDenied by remember { mutableStateOf(false) }
    var updatePinned by remember { mutableStateOf(false) }
    val popupStatus = remember { CsfloatPopupStatus() }
    var popupState by remember { mutableStateOf(popupStatus.state) }
    var popupDialog by remember { mutableStateOf<Dialog?>(null) }
    var popupView by remember { mutableStateOf<GeckoView?>(null) }
    var popupSession by remember { mutableStateOf<GeckoSession?>(null) }
    var tempXpi by remember { mutableStateOf<File?>(null) }
    var failNextPopupForTest by remember { mutableStateOf(false) }
    var failNextCleanupForTest by remember { mutableStateOf(false) }
    var failNextDeniedVerificationForTest by remember { mutableStateOf(false) }
    var trustAcceptedInstall by remember { mutableStateOf(false) }
    var pendingDeniedVerification by remember { mutableStateOf(false) }
    var pendingCleanup by remember { mutableStateOf<List<WebExtension>>(emptyList()) }
    var pendingCleanupSuccess by remember { mutableStateOf("") }
    var runtimeRef: GeckoRuntime? = null
    var currentUrl by remember { mutableStateOf(startUrl) }
    var safeRecoveryUrl by remember { mutableStateOf(startUrl) }
    var cleanupBlanking by remember { mutableStateOf(false) }
    var csfloatQuarantined by remember { mutableStateOf(initialCsfloatQuarantine) }
    var trustInspectionComplete by remember { mutableStateOf(false) }
    var detectorReady by remember { mutableStateOf(false) }
    var initialPageLoaded by remember { mutableStateOf(false) }
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

    fun renderPopupStatus() {
        popupState = popupStatus.state
    }

    fun quarantineRestoreFailed(message: String) {
        csfloatQuarantined = true
        popupStatus.discoveryFailed()
        renderPopupStatus()
        csfloatState = message
    }

    fun maybeLoadInitialPage() {
        if (!initialPageLoaded && detectorReady && trustInspectionComplete && !csfloatQuarantined) {
            initialPageLoaded = true
            sessionRef?.loadUri(safeRecoveryUrl)
        }
    }

    fun persistQuarantine(active: Boolean): Boolean = try {
        persistCsfloatQuarantine(active)
    } catch (_: Exception) {
        false
    }

    fun quarantine(): Boolean {
        csfloatQuarantined = true
        cleanupBlanking = true
        sessionRef?.loadUri("about:blank")
        return persistQuarantine(true).also { persisted ->
            if (!persisted) {
                popupStatus.discoveryFailed()
                renderPopupStatus()
                csfloatState = "CSFloat: quarantine storage failed. Access remains closed; retry."
            }
        }
    }

    fun clearQuarantineAndRestore(): Boolean {
        if (!persistQuarantine(false)) return false
        csfloatQuarantined = false
        trustInspectionComplete = true
        val wasLoaded = initialPageLoaded
        maybeLoadInitialPage()
        if (wasLoaded) sessionRef?.loadUri(safeRecoveryUrl)
        return true
    }

    fun closePopup() {
        popupDialog?.setOnDismissListener(null)
        popupDialog?.dismiss()
        popupDialog = null
        popupView?.releaseSession()
        popupView = null
        popupSession?.close()
        popupSession = null
    }

    fun dismissPopup() {
        popupStatus.pendingRequest?.let {
            popupStatus.failed(it)
            csfloatState = "CSFloat: official popup closed before loading. Retry."
            renderPopupStatus()
        }
        closePopup()
    }

    fun clearCsfloat() {
        closePopup()
        csfloatExtensionRef = null
        csfloatPopupUri = null
        popupStatus.unavailable()
        renderPopupStatus()
    }

    fun openPopup(request: Long, uri: String) {
        try {
        closePopup()
        val popup = GeckoSession().apply {
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (session !== popupSession) return
                    if (success) {
                        csfloatState = "CSFloat: official popup opened"
                        popupStatus.opened(request)
                    } else {
                        csfloatState = "CSFloat: official popup failed to load. Retry."
                        popupStatus.failed(request)
                    }
                    renderPopupStatus()
                }
            }
            open(requireNotNull(runtimeRef))
        }
        val view = GeckoView(context).apply { setSession(popup) }
        val dialog = Dialog(context).apply {
            setTitle("Official CSFloat popup")
            setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnDismissListener { dismissPopup() }
            show()
        }
        popupSession = popup
        popupView = view
        popupDialog = dialog
        popup.loadUri(uri)
    } catch (_: RuntimeException) {
        popupStatus.failed(request)
        csfloatState = "CSFloat: official popup failed to open. Retry."
        renderPopupStatus()
        closePopup()
    }
    }

    fun bindCsfloat(extension: WebExtension) {
        clearCsfloat()
        if (!CsfloatExtensionContract.canOpenOfficialPopup(
                extension.id,
                extension.metaData.version,
                extension.metaData.signedState,
                extension.metaData.enabled,
            )
        ) return
        val popupUri = CsfloatExtensionContract.officialPopupUri(extension.metaData.baseUrl) ?: run {
            csfloatState = "CSFloat: official popup metadata is invalid. Retry."
            return
        }
        csfloatExtensionRef = extension
        csfloatPopupUri = popupUri
        popupStatus.available()
        renderPopupStatus()
        csfloatState = "CSFloat: enabled"
    }

    fun cleanupCsfloat(targets: List<WebExtension>, successMessage: String) {
        clearCsfloat()
        pendingCleanup = targets
        pendingCleanupSuccess = successMessage
        if (!quarantine()) return
        val controller = requireNotNull(runtimeRef).webExtensionController
        val rejectedIds = targets.map { it.id }.toSet()

        fun cleanupFailed() {
            clearCsfloat()
            popupStatus.discoveryFailed()
            renderPopupStatus()
            csfloatState =
                "CSFloat: cleanup incomplete; access was closed. Retry cleanup before browsing."
        }

        fun inspect() {
            controller.list().accept(
                { installed ->
                    if (installed == null || installed.any { it.id in rejectedIds }) {
                        cleanupFailed()
                    } else {
                        pendingCleanup = emptyList()
                        pendingCleanupSuccess = ""
                        if (clearQuarantineAndRestore()) {
                            csfloatState = successMessage
                        } else {
                            cleanupFailed()
                        }
                    }
                },
                { cleanupFailed() },
            )
        }

        fun uninstallAt(index: Int) {
            if (index == targets.size) {
                inspect()
            } else {
                controller.uninstall(targets[index]).accept(
                    { uninstallAt(index + 1) },
                    { cleanupFailed() },
                )
            }
        }

        if (debugBuild && failNextCleanupForTest) {
            failNextCleanupForTest = false
            cleanupFailed()
        } else {
            uninstallAt(0)
        }
    }

    fun discoverCsfloat(absentMessage: String? = null) {
        requireNotNull(runtimeRef).webExtensionController.list().accept(
            success@{ extensions ->
                if (extensions == null) {
                    clearCsfloat()
                    popupStatus.discoveryFailed()
                    renderPopupStatus()
                    csfloatState = "CSFloat: failed to inspect installed state. Retry."
                    quarantine()
                    return@success
                }
                val byId = extensions.orEmpty().filter { it.id == CsfloatExtensionContract.ID }
                val exact = byId.singleOrNull {
                    CsfloatExtensionContract.isExpected(it.id, it.metaData.version, it.metaData.signedState)
                }
                when {
                    byId.isNotEmpty() && exact == null -> {
                        cleanupCsfloat(
                            byId,
                            "CSFloat: unexpected package removed after verification. Retry installation.",
                        )
                    }
                    exact == null -> {
                        clearCsfloat()
                        trustInspectionComplete = true
                        if (csfloatQuarantined) {
                            if (clearQuarantineAndRestore()) {
                                csfloatState = absentMessage ?: "CSFloat: absent"
                            } else {
                                quarantineRestoreFailed(
                                    "CSFloat: quarantine clearance failed. Access remains closed; retry inspection.",
                                )
                            }
                        } else {
                            csfloatState = absentMessage ?: "CSFloat: absent"
                            maybeLoadInitialPage()
                        }
                    }
                    !exact.metaData.enabled -> {
                        clearCsfloat()
                        trustInspectionComplete = true
                        if (csfloatQuarantined) {
                            if (clearQuarantineAndRestore()) {
                                csfloatState = "CSFloat: quarantine cleared; extension disabled; browsing restored."
                            } else {
                                quarantineRestoreFailed(
                                    "CSFloat: extension is disabled but quarantine clearance failed. Access remains closed; retry inspection.",
                                )
                            }
                        } else {
                            csfloatState = "CSFloat: denied or disabled"
                            maybeLoadInitialPage()
                        }
                    }
                    csfloatQuarantined && trustAcceptedInstall -> {
                        trustAcceptedInstall = false
                        if (clearQuarantineAndRestore()) {
                            bindCsfloat(exact)
                        } else {
                            cleanupCsfloat(
                                listOf(exact),
                                "CSFloat: untrusted installation removed; browsing restored.",
                            )
                        }
                    }
                    csfloatQuarantined -> cleanupCsfloat(
                        listOf(exact),
                        "CSFloat: quarantine cleared; extension absent; browsing restored.",
                    )
                    else -> {
                        bindCsfloat(exact)
                        trustInspectionComplete = true
                        maybeLoadInitialPage()
                    }
                }
            },
            {
                clearCsfloat()
                popupStatus.discoveryFailed()
                renderPopupStatus()
                csfloatState = "CSFloat: failed to inspect installed state. Retry."
                if (!trustInspectionComplete || csfloatQuarantined) quarantine()
            },
        )
    }

    fun verifyDeniedCsfloat() {
        pendingDeniedVerification = true
        if (!quarantine()) return

        fun verificationFailed() {
            clearCsfloat()
            pendingDeniedVerification = true
            popupStatus.discoveryFailed()
            renderPopupStatus()
            csfloatState =
                "CSFloat: consent denied but extension state could not be verified. Access was closed; retry inspection."
        }

        if (debugBuild && failNextDeniedVerificationForTest) {
            failNextDeniedVerificationForTest = false
            verificationFailed()
            return
        }
        requireNotNull(runtimeRef).webExtensionController.list().accept(
            success@{ extensions ->
                if (extensions == null) {
                    verificationFailed()
                    return@success
                }
                val matching = extensions.orEmpty().filter { it.id == CsfloatExtensionContract.ID }
                val enabled = matching.filter { it.metaData.enabled }
                when {
                    enabled.isNotEmpty() -> {
                        pendingDeniedVerification = false
                        cleanupCsfloat(enabled, csfloatDenialMessage(CsfloatDenialState.ENABLED))
                    }
                    matching.isNotEmpty() -> {
                        clearCsfloat()
                        if (clearQuarantineAndRestore()) {
                            pendingDeniedVerification = false
                            csfloatState = csfloatDenialMessage(CsfloatDenialState.DISABLED)
                        } else {
                            quarantineRestoreFailed(
                                "CSFloat: consent denied and extension disabled, but quarantine clearance failed. Access remains closed; retry inspection.",
                            )
                        }
                    }
                    else -> {
                        clearCsfloat()
                        if (clearQuarantineAndRestore()) {
                            pendingDeniedVerification = false
                            csfloatState = csfloatDenialMessage(CsfloatDenialState.ABSENT)
                        } else {
                            quarantineRestoreFailed(
                                "CSFloat: consent denied and extension absent, but quarantine clearance failed. Access remains closed; retry inspection.",
                            )
                        }
                    }
                }
            },
            {
                verificationFailed()
            },
        )
    }

    fun installCsfloat() {
        if (csfloatBusy) return
        if (!quarantine()) return
        csfloatBusy = true
        installDenied = false
        trustAcceptedInstall = false
        csfloatState = "CSFloat: installing verified package…"
        scope.launch {
            try {
                val file = CsfloatExtensionContract.downloadVerified(context.cacheDir)
                tempXpi = file
                requireNotNull(runtimeRef).webExtensionController.install(
                    Uri.fromFile(file).toString(),
                    WebExtensionController.INSTALLATION_METHOD_FROM_FILE,
                ).accept(
                    { extension ->
                        file.delete()
                        tempXpi = null
                        csfloatBusy = false
                        if (installDenied) {
                            verifyDeniedCsfloat()
                        } else if (extension != null && CsfloatExtensionContract.isExpected(
                                extension.id,
                                extension.metaData.version,
                                extension.metaData.signedState,
                            )
                        ) {
                            trustAcceptedInstall = true
                            csfloatState = "CSFloat: installed; discovering official popup…"
                            discoverCsfloat()
                        } else {
                            if (extension == null) {
                                clearCsfloat()
                                discoverCsfloat(
                                    "CSFloat: install returned no package. Retry; browsing remains available.",
                                )
                            } else {
                                cleanupCsfloat(
                                    listOf(extension),
                                    "CSFloat: unexpected package removed after verification. Retry installation.",
                                )
                            }
                        }
                    },
                    {
                        file.delete()
                        tempXpi = null
                        csfloatBusy = false
                        if (installDenied) {
                            verifyDeniedCsfloat()
                        } else {
                            clearCsfloat()
                            discoverCsfloat(
                                "CSFloat: install failed. Check the network and retry; browsing remains available.",
                            )
                        }
                    },
                )
            } catch (_: Exception) {
                tempXpi?.delete()
                tempXpi = null
                csfloatBusy = false
                discoverCsfloat("CSFloat: download verification failed. Check the network and retry.")
            }
        }
    }

    val promptDelegate = remember {
        object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (extension.id != CsfloatExtensionContract.ID || extension.metaData.version != CsfloatExtensionContract.VERSION) {
                    csfloatState = "CSFloat: install request identity mismatch. Access denied."
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                }
                return GeckoResult<WebExtension.PermissionPromptResponse>().also {
                    installPromptResult = it
                    installAllowsDataCollection = dataCollectionPermissions.isNotEmpty()
                    installPromptText = CsfloatExtensionContract.prompt(
                        extension.metaData.name,
                        extension.id,
                        extension.metaData.version,
                        permissions.toList(),
                        origins.toList(),
                        dataCollectionPermissions.toList(),
                    )
                }
            }

            override fun onUpdatePrompt(
                extension: WebExtension,
                newPermissions: Array<out String>,
                newOrigins: Array<out String>,
                newDataCollectionPermissions: Array<out String>,
            ): GeckoResult<AllowOrDeny> {
                updatePinned = true
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }

            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>,
                dataCollectionPermissions: Array<out String>,
            ) = GeckoResult.fromValue(AllowOrDeny.DENY)
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
    runtimeRef = runtime

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
                    Text(title, modifier = Modifier.weight(1f).padding(horizontal = 4.dp), maxLines = 1)
                    IconButton(onClick = { sessionRef?.goBack() }, enabled = canGoBack && !csfloatQuarantined) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    IconButton(onClick = { sessionRef?.goForward() }, enabled = canGoForward && !csfloatQuarantined) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                    }
                    IconButton(onClick = { sessionRef?.reload() }, enabled = !csfloatQuarantined) {
                        Icon(Icons.Filled.Refresh, "Refresh")
                    }
                    IconButton(onClick = { openExternal(Uri.parse(safeRecoveryUrl)) }) {
                        Icon(Icons.Filled.OpenInBrowser, "Open externally")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(csfloatState, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(
                        onClick = { installCsfloat() },
                        enabled = csfloatInstallEnabled(trustInspectionComplete, csfloatQuarantined) &&
                            !csfloatBusy && csfloatExtensionRef == null &&
                            !pendingDeniedVerification && pendingCleanup.isEmpty(),
                    ) {
                        Text(if (csfloatBusy) "Installing" else "Install CSFloat")
                    }
                    TextButton(
                        onClick = {
                            val popupUri = csfloatPopupUri ?: return@TextButton
                            csfloatState = "CSFloat: opening official popup…"
                            popupStatus.requestOpen {
                                val request = requireNotNull(popupStatus.pendingRequest)
                                if (debugBuild && failNextPopupForTest) {
                                    failNextPopupForTest = false
                                    popupStatus.failed(request)
                                    csfloatState = "CSFloat: official popup failed to open. Retry."
                                } else {
                                    openPopup(request, popupUri)
                                }
                            }
                            renderPopupStatus()
                        },
                        enabled = csfloatPopupUri != null && popupStatus.pendingRequest == null,
                    ) { Text("Open CSFloat") }
                }
                Text(
                    when (popupState) {
                        CsfloatPopupState.UNAVAILABLE -> "CSFloat popup: unavailable"
                        CsfloatPopupState.AVAILABLE ->
                            "CSFloat popup: available. Inspect tracking status inside the official popup."
                        CsfloatPopupState.OPENED ->
                            "CSFloat popup: opened. Tracking status is shown only inside the official popup."
                        CsfloatPopupState.FAILED -> "CSFloat popup: failed. Retry is available."
                    },
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Row {
                    if (debugBuild && popupState == CsfloatPopupState.UNAVAILABLE && !pendingDeniedVerification) {
                        TextButton(onClick = { failNextDeniedVerificationForTest = true }) {
                            Text("Test denied verification failure")
                        }
                    }
                    if (debugBuild && popupState == CsfloatPopupState.AVAILABLE) {
                        TextButton(onClick = { failNextPopupForTest = true }) {
                            Text("Test CSFloat popup failure")
                        }
                        TextButton(onClick = {
                            val extension = csfloatExtensionRef ?: return@TextButton
                            failNextCleanupForTest = true
                            cleanupCsfloat(
                                listOf(extension),
                                "CSFloat: test cleanup complete; extension absent; browsing restored.",
                            )
                        }) {
                            Text("Test CSFloat cleanup failure")
                        }
                    }
                    if (popupState == CsfloatPopupState.FAILED) {
                        TextButton(onClick = {
                            popupStatus.recover()
                            renderPopupStatus()
                            val targets = pendingCleanup
                            if (pendingDeniedVerification) verifyDeniedCsfloat()
                            else if (targets.isEmpty()) discoverCsfloat()
                            else cleanupCsfloat(targets, pendingCleanupSuccess)
                        }) {
                            Text("Retry CSFloat")
                        }
                    }
                }
                Text(
                    if (updatePinned) {
                        "CSFloat update denied: reviewed version 5.17.0 remains pinned."
                    } else {
                        "CSFloat update policy: reviewed version 5.17.0 is pinned; updates require review."
                    },
                    Modifier.padding(8.dp),
                )
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
                            url?.let {
                                currentUrl = it
                                if (it == "about:blank" && cleanupBlanking) {
                                    cleanupBlanking = false
                                } else if (policy.decideNavigation(it) == WebsitePolicy.NavigationDecision.ALLOW_IN_APP) {
                                    val scheme = Uri.parse(it).scheme
                                    if (scheme == "http" || scheme == "https") safeRecoveryUrl = it
                                }
                            }
                        }

                        override fun onLoadRequest(
                            session: GeckoSession,
                            request: GeckoSession.NavigationDelegate.LoadRequest,
                        ): GeckoResult<AllowOrDeny> {
                            if (csfloatQuarantined) {
                                return GeckoResult.fromValue(
                                    if (request.uri == "about:blank") AllowOrDeny.ALLOW else AllowOrDeny.DENY,
                                )
                            }
                            if (cleanupBlanking && request.uri == "about:blank") {
                                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                            }
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
                    runtime.webExtensionController.promptDelegate = promptDelegate
                    discoverCsfloat()

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
                            detectorReady = true
                            maybeLoadInitialPage()
                        } },
                        { geckoView.post {
                            if (sessionRef === session) {
                                error = "Steam profile image detection is unavailable. You can still sign in and browse."
                                detectorReady = true
                                maybeLoadInitialPage()
                            }
                        } },
                    )
                    geckoView
                },
                onRelease = { view ->
                    installPromptResult?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    installPromptResult = null
                    installPromptText = null
                    runtime.webExtensionController.promptDelegate = null
                    clearCsfloat()
                    tempXpi?.delete()
                    tempXpi = null
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

    installPromptText?.let { prompt ->
        AlertDialog(
            onDismissRequest = {
                installDenied = true
                installPromptResult?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                installPromptResult = null
                installPromptText = null
                csfloatState = "CSFloat: consent denied; browsing remains available."
            },
            title = { Text("Install-time CSFloat access request") },
            text = { Text(prompt) },
            confirmButton = {
                TextButton(onClick = {
                    installDenied = false
                    installPromptResult?.complete(
                        WebExtension.PermissionPromptResponse(true, false, installAllowsDataCollection),
                    )
                    installPromptResult = null
                    installPromptText = null
                }) { Text("Accept CSFloat access") }
            },
            dismissButton = {
                TextButton(onClick = {
                    installDenied = true
                    installPromptResult?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    installPromptResult = null
                    installPromptText = null
                    csfloatState = "CSFloat: consent denied; browsing remains available."
                }) { Text("Deny CSFloat access") }
            },
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

internal fun csfloatInstallEnabled(trustInspectionComplete: Boolean, quarantined: Boolean) =
    trustInspectionComplete && !quarantined
