package com.steamaccountmanager.app.prototype

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

class GeckoViewPrototypeActivity : ComponentActivity() {
    private lateinit var runtime: GeckoRuntime
    private lateinit var session: GeckoSession
    private lateinit var status: TextView
    private lateinit var installButton: Button
    private var installDenied = false
    private var installFailure = PrototypeDiagnostic.INSTALL_FAILED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            text = "Ready to install. The public Steam listing is loading."
            setPadding(24, 16, 24, 16)
        }
        installButton = Button(this).apply {
            text = "Review and install CSFloat"
            setOnClickListener { installExtension() }
        }
        val metadata = TextView(this).apply {
            text = ARTIFACT_METADATA
            setPadding(24, 8, 24, 12)
            setTextIsSelectable(true)
        }
        val geckoView = GeckoView(this)
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(status)
                addView(installButton)
                addView(metadata)
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

        runtime = sharedRuntime ?: GeckoRuntime.create(applicationContext).also { sharedRuntime = it }
        runtime.webExtensionController.promptDelegate = InstallConsentPrompt()
        session = GeckoSession().apply {
            progressDelegate = object : GeckoSession.ProgressDelegate {
                override fun onPageStop(session: GeckoSession, success: Boolean) {
                    if (!success) runOnUiThread {
                        status.text = PrototypeDiagnostic.PAGE_LOAD_FAILED.message
                    }
                }
            }
            open(runtime)
            loadUri(STEAM_LISTING_URL)
        }
        geckoView.setSession(session)
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
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

    private companion object {
        var sharedRuntime: GeckoRuntime? = null

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
