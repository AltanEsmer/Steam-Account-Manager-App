package com.steamaccountmanager.app.prototype

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class GeckoViewPrototypeActivity : ComponentActivity() {
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession
    private lateinit var status: TextView
    private lateinit var installButton: Button
    private lateinit var trackingStatus: TextView
    private lateinit var actionButton: Button
    private lateinit var recordStatusButton: Button
    private lateinit var simulateFailureButton: Button
    private lateinit var recoverButton: Button
    private lateinit var markerStatus: TextView
    private lateinit var engineStatus: TextView
    private lateinit var extensionState: TextView
    private lateinit var markerExtensionState: TextView
    private lateinit var slot: String
    private lateinit var profileId: String
    private var installDenied = false
    private var installFailure = PrototypeDiagnostic.INSTALL_FAILED
    private val tracking = PrototypeTracking()
    private var boundExtension: WebExtension? = null
    private var defaultAction: WebExtension.Action? = null
    private var sessionAction: WebExtension.Action? = null
    private var effectiveAction: WebExtension.Action? = null
    private var popupDialog: Dialog? = null
    private var popupView: GeckoView? = null
    private var popupSession: GeckoSession? = null
    private var pendingPopupRequestId: Long? = null
    private var markerExtension: WebExtension? = null
    private var markerPort: WebExtension.Port? = null
    private var markerMutationInFlight = false
    private var csfloatMutationInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        slot = intent.getStringExtra(EXTRA_SLOT).takeIf { it == "A" || it == "B" } ?: run {
            finish()
            return
        }
        profileId = slotProfileId(slot)

        status = TextView(this).apply {
            text = "Ready to install. The public Steam listing is loading."
            setPadding(24, 16, 24, 16)
        }
        installButton = Button(this).apply {
            text = "Review and install CSFloat"
            setOnClickListener { installExtension() }
        }
        trackingStatus = TextView(this).apply { setPadding(24, 8, 24, 8) }
        actionButton = Button(this).apply {
            text = "Open official CSFloat action"
            setOnClickListener { requestAction() }
        }
        recordStatusButton = Button(this).apply {
            text = "Record visible official status"
            setOnClickListener {
                tracking.recordVisibleOfficialStatus()
                renderTracking()
            }
        }
        simulateFailureButton = Button(this).apply {
            text = "Simulate popup failure (test only)"
            setOnClickListener {
                closePopup()
                pendingPopupRequestId = null
                tracking.simulatePopupFailure()
                renderTracking()
            }
        }
        recoverButton = Button(this).apply {
            text = "Recover and rediscover CSFloat"
            setOnClickListener {
                closePopup()
                pendingPopupRequestId = null
                tracking.recover()
                renderTracking()
                discoverAction()
            }
        }
        markerStatus = TextView(this).apply {
            text = "GV-MARKER-WAIT slot=$slot profile=$profileId"
            setPadding(24, 8, 24, 8)
        }
        engineStatus = TextView(this).apply {
            text = "GV6|loading"
            setPadding(24, 8, 24, 8)
        }
        extensionState = TextView(this).apply {
            text = "GV-CSFLOAT-STATE-UNKNOWN"
            setPadding(24, 8, 24, 8)
        }
        markerExtensionState = TextView(this).apply {
            text = "GV-MARKER-STATE-UNKNOWN slot=$slot profile=$profileId"
            setPadding(24, 8, 24, 8)
        }
        val metadata = TextView(this).apply {
            text = ARTIFACT_METADATA
            setPadding(24, 8, 24, 12)
            setTextIsSelectable(true)
        }
        val geckoView = GeckoView(this)
        val controls = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(markerStatus)
                addView(engineStatus)
                addView(installButton)
                addView(trackingStatus)
                addView(actionButton)
                addView(recordStatusButton)
                addView(simulateFailureButton)
                addView(recoverButton)
                addView(button("Open synthetic isolation marker") { loadSyntheticMarker() })
                addView(button("Open public Steam listing") { session.loadUri(STEAM_LISTING_URL) })
                addView(button("Recreate worker activity") { recreate() })
                addView(button("Close worker screen") { finish() })
                addView(extensionState)
                addView(button("Disable CSFloat") { changeCsfloat("disable") })
                addView(button("Enable CSFloat") { changeCsfloat("enable") })
                addView(button("Uninstall CSFloat") { changeCsfloat("uninstall") })
                addView(button("Reinstall CSFloat with consent") { installExtension() })
                addView(markerExtensionState)
                addView(button("Disable issue6 marker") { changeMarker("disable") })
                addView(button("Enable issue6 marker") { changeMarker("enable") })
                addView(button("Uninstall issue6 marker") { changeMarker("uninstall") })
                addView(button("Reinstall issue6 marker (synthetic only)") { changeMarker("reinstall") })
                addView(metadata)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(ScrollView(this@GeckoViewPrototypeActivity).apply { addView(controls) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                addView(
                    geckoView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    ),
                )
            },
        )

        if (!PrototypeLoopbackServer.start()) {
            status.text = "GV-LOOPBACK-START-FAILED: Synthetic fixture unavailable."
            finish()
            return
        }

        val root = File(noBackupFilesDir, "gecko-prototype-profiles").toPath()
        val profile = requireContainedProfilePath(root, root.resolve(profileId)).toFile().apply { mkdirs() }
        val existing = sharedRuntime
        if (existing != null && sharedProfileId != profileId) {
            status.text = "GV-PROFILE-MISMATCH: Worker restart required."
            finish()
            return
        }
        runtime = existing ?: GeckoRuntime.create(
            applicationContext,
            GeckoRuntimeSettings.Builder()
                .arguments(arrayOf("--profile", profile.absolutePath))
                .build(),
        ).also {
            sharedRuntime = it
            sharedProfileId = profileId
        }
        runtime.webExtensionController.promptDelegate = InstallConsentPrompt()
        session = GeckoSession().apply {
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (!success) runOnUiThread {
                        status.text = PrototypeDiagnostic.PAGE_LOAD_FAILED.message
                    }
                }
            }
            contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    if (title?.matches(Regex("^GV6\\|slot=[AB]\\|cookie=[AB]\\|local=[AB]\\|idb=[AB]\\|nav=[AB]$")) == true) {
                        runOnUiThread { engineStatus.text = title }
                    }
                }
            }
            open(runtime)
            loadUri("http://127.0.0.1:$LOOPBACK_PORT/slot/$slot")
        }
        geckoView.setSession(session)
        runtime.webExtensionController.setTabActive(session, true)
        renderTracking()
        discoverAction()
        discoverMarkerExtension()
        refreshCsfloatState()
    }

    override fun onDestroy() {
        if (!::runtime.isInitialized || !::session.isInitialized) {
            super.onDestroy()
            return
        }
        closePopup()
        clearActionDelegates()
        markerPort?.disconnect()
        markerPort = null
        markerExtension?.setMessageDelegate(null, MARKER_NATIVE_APP)
        markerExtension = null
        runtime.webExtensionController.promptDelegate = null
        runtime.webExtensionController.setTabActive(session, false)
        session.close()
        super.onDestroy()
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun loadSyntheticMarker() {
        session.loadUri("http://127.0.0.1:$LOOPBACK_PORT/slot/$slot")
    }

    private fun installMarkerExtension() {
        // Mozilla requires geckoViewAddons for background-script native messaging;
        // this privilege exists only in the non-shipping acceptance fixture.
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/issue6-marker/",
            MARKER_EXTENSION_ID,
        ).accept(
            { extension -> runOnUiThread {
                if (extension?.id != MARKER_EXTENSION_ID) {
                    markerMutationInFlight = false
                    markerStatus.text = "GV-MARKER-INSTALL-FAILED"
                    return@runOnUiThread
                }
                markerExtension = extension
                extension.setMessageDelegate(markerMessageDelegate, MARKER_NATIVE_APP)
                refreshMarkerState()
            } },
            { runOnUiThread {
                markerMutationInFlight = false
                markerStatus.text = "GV-MARKER-INSTALL-FAILED"
                markerExtensionState.text = "GV-MARKER-STATE-FAILED slot=$slot profile=$profileId"
            } },
        )
    }

    private fun discoverMarkerExtension() {
        runtime.webExtensionController.list().accept(
            { extensions -> runOnUiThread {
                val exact = extensions.orEmpty().singleOrNull { it.id == MARKER_EXTENSION_ID }
                if (exact == null) {
                    markerExtensionState.text = "GV-MARKER-STATE-ABSENT slot=$slot profile=$profileId"
                } else {
                    markerExtension = exact
                    exact.setMessageDelegate(markerMessageDelegate, MARKER_NATIVE_APP)
                    refreshMarkerState()
                }
            } },
            { runOnUiThread {
                markerExtensionState.text = "GV-MARKER-STATE-FAILED slot=$slot profile=$profileId"
            } },
        )
    }

    private fun refreshMarkerState(after: (() -> Unit)? = null) {
        runtime.webExtensionController.list().accept(
            { extensions -> runOnUiThread {
                val exact = extensions.orEmpty().singleOrNull { it.id == MARKER_EXTENSION_ID }
                if (exact == null) {
                    markerPort?.disconnect()
                    markerPort = null
                    markerExtension?.setMessageDelegate(null, MARKER_NATIVE_APP)
                    markerExtension = null
                }
                markerExtensionState.text = when {
                    exact == null -> "GV-MARKER-STATE-ABSENT slot=$slot profile=$profileId"
                    exact.metaData.enabled -> "GV-MARKER-STATE-ENABLED slot=$slot profile=$profileId"
                    else -> "GV-MARKER-STATE-DISABLED slot=$slot profile=$profileId"
                }
                markerMutationInFlight = false
                after?.invoke()
            } },
            { runOnUiThread {
                markerMutationInFlight = false
                markerExtensionState.text = "GV-MARKER-STATE-FAILED slot=$slot profile=$profileId"
            } },
        )
    }

    private fun changeMarker(operation: String) {
        if (markerMutationInFlight) return
        markerMutationInFlight = true
        if (operation == "reinstall") {
            installMarkerExtension()
            return
        }
        runtime.webExtensionController.list().accept(
            { extensions -> runOnUiThread {
                val exact = extensions.orEmpty().singleOrNull { it.id == MARKER_EXTENSION_ID }
                if (exact == null) {
                    refreshMarkerState()
                    return@runOnUiThread
                }
                val success = { _: Any? -> refreshMarkerState {
                    if (operation == "enable") installMarkerExtension()
                } }
                val failed = { _: Throwable? -> runOnUiThread {
                    markerMutationInFlight = false
                    markerExtensionState.text = "GV-MARKER-STATE-FAILED slot=$slot profile=$profileId"
                } }
                when (operation) {
                    "disable" -> runtime.webExtensionController
                        .disable(exact, WebExtensionController.EnableSource.APP).accept(success, failed)
                    "enable" -> runtime.webExtensionController
                        .enable(exact, WebExtensionController.EnableSource.APP).accept(success, failed)
                    "uninstall" -> runtime.webExtensionController.uninstall(exact).accept(success, failed)
                    else -> markerMutationInFlight = false
                }
            } },
            { runOnUiThread {
                markerMutationInFlight = false
                markerExtensionState.text = "GV-MARKER-STATE-FAILED slot=$slot profile=$profileId"
            } },
        )
    }

    private val markerMessageDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            val sender = port.sender
            if (port.name != MARKER_NATIVE_APP || sender.webExtension.id != MARKER_EXTENSION_ID ||
                sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION
            ) {
                runOnUiThread { markerStatus.text = "GV-MARKER-SCHEMA-REJECTED slot=$slot" }
                port.disconnect()
                return
            }
            markerPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (port !== markerPort || message !is JSONObject || message.optString("type") != "result") {
                        runOnUiThread { markerStatus.text = "GV-MARKER-SCHEMA-REJECTED slot=$slot" }
                        return
                    }
                    val reportedSlot = message.optString("slot")
                    val prior = message.optString("prior")
                    val current = message.optString("current")
                    if (reportedSlot == slot && prior in setOf("A", "B") && current in setOf("A", "B")) {
                        runOnUiThread {
                            markerStatus.text = "GV-MARKER-RESULT slot=$slot prior=$prior current=$current profile=$profileId"
                        }
                    }
                }
                override fun onDisconnect(port: WebExtension.Port) {
                    if (port === markerPort) markerPort = null
                }
            })
            port.postMessage(JSONObject().put("type", "read").put("slot", slot))
        }
    }

    private fun refreshCsfloatState(after: ((Boolean) -> Unit)? = null) {
        runtime.webExtensionController.list().accept(
            { extensions -> runOnUiThread {
                val exact = extensions.orEmpty().singleOrNull { isExpectedCsfloat(it.id, it.metaData.version) }
                val enabled = exact?.metaData?.enabled == true
                extensionState.text = when {
                    exact == null -> "GV-CSFLOAT-STATE-ABSENT slot=$slot"
                    enabled -> "GV-CSFLOAT-STATE-ENABLED slot=$slot"
                    else -> "GV-CSFLOAT-STATE-DISABLED slot=$slot"
                }
                if (!enabled) revokeOfficialAction()
                after?.invoke(enabled)
            } },
            { runOnUiThread {
                extensionState.text = "GV-CSFLOAT-STATE-FAILED"
                revokeOfficialAction()
                after?.invoke(false)
            } },
        )
    }

    private fun changeCsfloat(operation: String) {
        if (csfloatMutationInFlight || operation !in setOf("disable", "enable", "uninstall")) return
        csfloatMutationInFlight = true
        runtime.webExtensionController.list().accept(
            { extensions -> runOnUiThread {
                val exact = extensions.orEmpty().singleOrNull { isExpectedCsfloat(it.id, it.metaData.version) }
                if (exact == null) {
                    extensionState.text = "GV-CSFLOAT-STATE-ABSENT slot=$slot"
                    revokeOfficialAction()
                    csfloatMutationInFlight = false
                } else {
                    val result = when (operation) {
                        "disable" -> runtime.webExtensionController.disable(exact, WebExtensionController.EnableSource.APP)
                        "enable" -> runtime.webExtensionController.enable(exact, WebExtensionController.EnableSource.APP)
                        "uninstall" -> runtime.webExtensionController.uninstall(exact).map { exact }
                        else -> error("unreachable")
                    }
                    result.accept(
                        { runOnUiThread {
                            if (operation != "enable") revokeOfficialAction()
                            refreshCsfloatState { enabled ->
                                csfloatMutationInFlight = false
                                if (operation == "enable" && enabled) discoverAction()
                            }
                        } },
                        { runOnUiThread {
                            csfloatMutationInFlight = false
                            extensionState.text = "GV-CSFLOAT-STATE-FAILED"
                            revokeOfficialAction()
                        } },
                    )
                }
            } },
            { runOnUiThread {
                csfloatMutationInFlight = false
                extensionState.text = "GV-CSFLOAT-STATE-FAILED"
                revokeOfficialAction()
            } },
        )
    }

    private fun installExtension() {
        installButton.isEnabled = false
        installDenied = false
        installFailure = PrototypeDiagnostic.INSTALL_FAILED
        status.text = "Installing signed CSFloat package…"
        runtime.webExtensionController.install(
            CSFLOAT_XPI_URL,
            WebExtensionController.INSTALLATION_METHOD_MANAGER,
        ).accept(
            { extension ->
                runOnUiThread {
                    if (installDenied) {
                        verifyDeniedState()
                    } else extension?.let(::showInstalled) ?: run {
                        status.text = PrototypeDiagnostic.INSTALL_NO_RESULT.message
                        installButton.isEnabled = true
                    }
                }
            },
            {
                runOnUiThread {
                    if (installDenied) {
                        verifyDeniedState()
                    } else {
                        status.text = installFailure.message
                        installButton.isEnabled = true
                    }
                }
            },
        )
    }

    private fun showInstalled(extension: WebExtension) {
        if (isExpectedCsfloat(extension.id, extension.metaData.version)) {
            status.text = "Installed and ready: $CSFLOAT_NAME $CSFLOAT_VERSION; " +
                "GeckoView signed state ${extension.metaData.signedState}. Reloading the listing for injection."
            installButton.isEnabled = false
            session.reload()
            discoverAction()
            refreshCsfloatState()
            return
        }

        runtime.webExtensionController.uninstall(extension).accept(
            {
                runOnUiThread {
                    status.text = PrototypeDiagnostic.PACKAGE_MISMATCH.message
                    installButton.isEnabled = true
                }
            },
            {
                runOnUiThread {
                    status.text = PrototypeDiagnostic.CLEANUP_FAILED.message
                    installButton.isEnabled = false
                }
            },
        )
    }

    private fun verifyDeniedState() {
        runtime.webExtensionController.list().accept(
            { extensions ->
                runOnUiThread {
                    if (extensions == null) {
                        status.text = PrototypeDiagnostic.DENIAL_QUERY_FAILED.message
                    } else {
                        val expectedStates = extensions
                            .filter { it.id == CSFLOAT_ID }
                            .map { it.metaData.enabled }
                        status.text = denialMessage(denialState(expectedStates))
                    }
                    installButton.isEnabled = true
                }
            },
            {
                runOnUiThread {
                    status.text = PrototypeDiagnostic.DENIAL_QUERY_FAILED.message
                    installButton.isEnabled = true
                }
            },
        )
    }

    private fun discoverAction() {
        pendingPopupRequestId = null
        tracking.unavailable()
        renderTracking()
        runtime.webExtensionController.list().accept(
            { extensions ->
                runOnUiThread {
                    if (extensions == null) {
                        tracking.discoveryFailed()
                        renderTracking()
                        return@runOnUiThread
                    }
                    val extension = extensions.singleOrNull {
                        isEnabledExpectedExtension(it.id, it.metaData.version, it.metaData.enabled)
                    }
                    val bound = boundExtension
                    if (canReuseValidatedAction(
                            bound?.id,
                            bound?.metaData?.version,
                            extension?.id,
                            extension?.metaData?.version,
                            extension?.metaData?.enabled == true,
                            effectiveAction != null,
                        )
                    ) {
                        tracking.actionAvailable()
                        renderTracking()
                    } else if (extension == null) {
                        clearActionDelegates()
                        renderTracking()
                    } else if (extension !== bound) {
                        bindAction(extension)
                    }
                }
            },
            {
                runOnUiThread {
                    tracking.discoveryFailed()
                    renderTracking()
                }
            },
        )
    }

    private fun bindAction(extension: WebExtension) {
        clearActionDelegates()
        boundExtension = extension
        extension.setActionDelegate(actionDelegate)
        session.webExtensionController.setActionDelegate(extension, actionDelegate)
    }

    private fun clearActionDelegates() {
        pendingPopupRequestId = null
        boundExtension?.let { extension ->
            extension.setActionDelegate(null)
            session.webExtensionController.setActionDelegate(extension, null)
        }
        boundExtension = null
        defaultAction = null
        sessionAction = null
        effectiveAction = null
    }

    private fun revokeOfficialAction() {
        closePopup()
        clearActionDelegates()
        tracking.unavailable()
        renderTracking()
    }

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            callbackSession: GeckoSession?,
            action: WebExtension.Action,
        ) = receiveAction(extension, callbackSession, action)

        override fun onPageAction(
            extension: WebExtension,
            callbackSession: GeckoSession?,
            action: WebExtension.Action,
        ) = receiveAction(extension, callbackSession, action)

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> {
            val requestId = pendingPopupRequestId
            if (requestId == null || requestId != tracking.inFlightRequestId) {
                return GeckoResult.fromValue(null)
            }
            if (popupSession == null) return openPopup(requestId)
            closePopup()
            if (tracking.popupOpened(requestId)) pendingPopupRequestId = null
            renderTracking()
            return GeckoResult.fromValue(null)
        }

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession> {
            val requestId = pendingPopupRequestId
            if (requestId == null || requestId != tracking.inFlightRequestId) {
                return GeckoResult.fromValue(null)
            }
            // Gecko does not return the originating request token. The single pending
            // dispatch is the only correlatable callback; the reducer rejects explicit stale tokens.
            return openPopup(requestId)
        }
    }

    private fun receiveAction(
        extension: WebExtension,
        callbackSession: GeckoSession?,
        action: WebExtension.Action,
    ) {
        if (extension !== boundExtension) return
        runOnUiThread {
            if (callbackSession == null) defaultAction = action else sessionAction = action
            val default = defaultAction
            val sessionOverride = sessionAction
            effectiveAction = if (default != null && sessionOverride != null) {
                sessionOverride.withDefault(default)
            } else {
                default
            }
            if (effectiveAction?.enabled == true) tracking.actionAvailable() else tracking.unavailable()
            renderTracking()
        }
    }

    private fun requestAction() {
        val action = effectiveAction ?: return
        tracking.requestAction {
            val requestId = requireNotNull(tracking.inFlightRequestId)
            pendingPopupRequestId = requestId
            try {
                action.click()
            } catch (_: RuntimeException) {
                if (tracking.actionClickFailed(requestId)) pendingPopupRequestId = null
            }
        }
        renderTracking()
    }

    private fun openPopup(requestId: Long): GeckoResult<GeckoSession> = try {
        closePopup()
        val popup = GeckoSession().apply { open(runtime) }
        val view = GeckoView(this).apply {
            minimumHeight = 600
            setSession(popup)
        }
        val dialog = Dialog(this).apply {
            setTitle("Official CSFloat popup")
            setContentView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            setOnDismissListener { closePopup() }
            show()
        }
        popupSession = popup
        popupView = view
        popupDialog = dialog
        if (tracking.popupOpened(requestId)) pendingPopupRequestId = null
        renderTracking()
        GeckoResult.fromValue(popup)
    } catch (_: RuntimeException) {
        if (tracking.popupFailed(requestId)) pendingPopupRequestId = null
        renderTracking()
        GeckoResult.fromException(IllegalStateException("GV-ACTION-FAILED"))
    }

    private fun closePopup() {
        popupDialog?.setOnDismissListener(null)
        popupDialog?.dismiss()
        popupDialog = null
        popupView?.releaseSession()
        popupView = null
        popupSession?.close()
        popupSession = null
    }

    private fun renderTracking() {
        trackingStatus.text = tracking.diagnostic ?: trackingMessage(tracking.state)
        val canAct = tracking.state == TrackingState.READY || tracking.state == TrackingState.ACTIVE
        actionButton.isEnabled = canAct && tracking.inFlightRequestId == null && effectiveAction != null
        recordStatusButton.isEnabled = tracking.state == TrackingState.READY &&
            tracking.inFlightRequestId == null && tracking.officialSurfaceOpened
        simulateFailureButton.isEnabled = tracking.state == TrackingState.READY || tracking.inFlightRequestId != null
        recoverButton.isEnabled = tracking.state == TrackingState.FAILED
    }

    private inner class InstallConsentPrompt : WebExtensionController.PromptDelegate {
        override fun onInstallPromptRequest(
            extension: WebExtension,
            permissions: Array<out String>,
            origins: Array<out String>,
            dataCollectionPermissions: Array<out String>,
        ): GeckoResult<WebExtension.PermissionPromptResponse> {
            val result = GeckoResult<WebExtension.PermissionPromptResponse>()
            if (!isExpectedCsfloat(extension.id, extension.metaData.version)) {
                runOnUiThread {
                    installFailure = PrototypeDiagnostic.CONSENT_IDENTITY_MISMATCH
                    status.text = installFailure.message
                    installButton.isEnabled = true
                    result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                }
                return result
            }
            val lines = installPrompt(
                extension.metaData.name,
                extension.id,
                extension.metaData.version,
                permissions.toList(),
                origins.toList(),
                dataCollectionPermissions.toList(),
            )

            runOnUiThread {
                AlertDialog.Builder(this@GeckoViewPrototypeActivity)
                    .setTitle("Install-time access request")
                    .setMessage(lines)
                    .setNegativeButton("Deny") { _, _ ->
                        installDenied = true
                        status.text = "Consent denied; checking expected CSFloat state…"
                        result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    }
                    .setPositiveButton("Accept") { _, _ ->
                        status.text = "Consent accepted. Finishing signed-package installation…"
                        result.complete(
                            WebExtension.PermissionPromptResponse(
                                true,
                                false,
                                dataCollectionPermissions.isNotEmpty(),
                            ),
                        )
                    }
                    .setOnCancelListener {
                        installDenied = true
                        status.text = "Consent denied; checking expected CSFloat state…"
                        result.complete(WebExtension.PermissionPromptResponse(false, false, false))
                    }
                    .show()
            }
            return result
        }
    }

    companion object {
        var sharedRuntime: GeckoRuntime? = null
        var sharedProfileId: String? = null

        fun shutdownProcess() {
            sharedRuntime?.shutdown()
            sharedRuntime = null
            sharedProfileId = null
            Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 500)
        }

        const val STEAM_LISTING_URL =
            "https://steamcommunity.com/market/listings/730/AK-47%20%7C%20Redline%20%28Field-Tested%29"
        const val CSFLOAT_XPI_URL =
            "https://addons.mozilla.org/firefox/downloads/file/4957680/csgofloat-5.17.0.xpi"
        const val CSFLOAT_NAME = "CSFloat Market Checker"
        const val ARTIFACT_METADATA =
            "GeckoView 153.0.20260810162159\n" +
                "CSFloat Market Checker 5.17.0\n" +
                "Gecko ID: {194d0dc6-7ada-41c6-88b8-95d7636fe43c}\n" +
                "Official signed XPI: $CSFLOAT_XPI_URL\n" +
                "Size: 7,011,169 bytes\n" +
                "SHA-256: 70C540B8B1DF125596EF615FE37028542DE4D92B3816AD81EB6AD5CE3D11798D\n" +
                "Signature is validated by GeckoView during install; observed signed state appears after success."
    }
}

private object PrototypeLoopbackServer {
    @Volatile private var server: ServerSocket? = null

    @Synchronized fun start(): Boolean {
        if (server != null) return true
        val bound = try {
            ServerSocket(LOOPBACK_PORT, 8, InetAddress.getByName("127.0.0.1"))
        } catch (_: Exception) {
            return false
        }
        server = bound
        Executors.newSingleThreadExecutor().execute {
            bound.use {
                while (!it.isClosed) try {
                    it.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val request = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                            .readLine().orEmpty()
                        val requestedSlot = Regex("^GET /slot/([AB]) HTTP/1\\.[01]$")
                            .matchEntire(request)?.groupValues?.get(1)
                        val body = if (requestedSlot == null) {
                            "<!doctype html><title>GV6|error=request</title>"
                        } else {
                            page(requestedSlot)
                        }
                        val status = if (requestedSlot == null) "400 Bad Request" else "200 OK"
                        val bytes = body.toByteArray(StandardCharsets.UTF_8)
                        socket.getOutputStream().write(
                            "HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                                .toByteArray(StandardCharsets.US_ASCII),
                        )
                        socket.getOutputStream().write(bytes)
                    }
                } catch (_: Exception) {
                    // A bounded client failure must not terminate the fixed test server.
                }
            }
            synchronized(this) {
                if (server === bound) server = null
            }
        }
        return true
    }

    private fun page(slot: String) = """<!doctype html><meta charset=utf-8><body><h1>Issue 6 synthetic slot $slot</h1><pre id=o>GV6|loading</pre><script>
const s='$slot'; if(!document.cookie.includes('gv6='))document.cookie='gv6='+s+'; SameSite=Strict';
if(!localStorage.gv6)localStorage.gv6=s;if(!localStorage.gv6nav)localStorage.gv6nav=s;
const n=localStorage.gv6nav;if(!history.state)history.replaceState({gv6:n},'',location.pathname+'#'+n);
const q=indexedDB.open('gv6',1);q.onupgradeneeded=()=>q.result.createObjectStore('m');q.onsuccess=()=>{const d=q.result.transaction('m','readwrite').objectStore('m');const g=d.get('slot');g.onsuccess=()=>{const prior=g.result||s;if(!g.result)d.put(s,'slot');const c=(document.cookie.match(/gv6=([AB])/)||[])[1]||'missing';const l=localStorage.gv6||'missing';const text=`GV6|slot=${'$'}{s}|cookie=${'$'}{c}|local=${'$'}{l}|idb=${'$'}{prior}|nav=${'$'}{n}`;document.title=text;document.getElementById('o').textContent=text;};};
</script></body>"""
}
