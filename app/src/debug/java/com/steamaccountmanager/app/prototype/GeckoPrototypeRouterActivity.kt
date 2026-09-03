package com.steamaccountmanager.app.prototype

import android.app.ActivityManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

internal const val EXTRA_SLOT = "prototype_slot"
internal const val ACTION_SHUTDOWN = "com.steamaccountmanager.app.debug.PROTOTYPE_SHUTDOWN"
internal const val EXTRA_GENERATION = "prototype_generation"
internal const val LOOPBACK_PORT = 38947
internal const val MARKER_EXTENSION_ID = "issue6-marker@steam-account-manager.invalid"
internal const val MARKER_NATIVE_APP = "issue6Marker"

internal fun slotProfileId(slot: String): String = when (slot) {
    "A" -> geckoProfileId("synthetic-account-a", "synthetic-website")
    "B" -> geckoProfileId("synthetic-account-b", "synthetic-website")
    else -> error("GV-PROFILE-SLOT-INVALID")
}

class GeckoPrototypeRouterActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var switches: ProfileSwitchCoordinator
    private var selectedSlot: String? = null
    private var stopGeneration = 0L
    private var destroyed = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedSlot = getPreferences(MODE_PRIVATE).getString("slot", null)
        switches = ProfileSwitchCoordinator(selectedSlot?.let(::slotProfileId))
        status = TextView(this).apply { setPadding(24, 24, 24, 24) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(button("Open synthetic slot A") { select("A") })
            addView(button("Open synthetic slot B") { select("B") })
            addView(button("Reopen selected slot") { selectedSlot?.let(::select) })
            addView(button("Recreate router activity") { recreate() })
            addView(button("Stop worker process") {
                val requestedGeneration = ++stopGeneration
                stopWorker(
                    "GV-WORKER-STOP-WAIT",
                    "GV-WORKER-STOP-TIMEOUT",
                    requestedGeneration,
                    { requestedGeneration == stopGeneration },
                ) { stoppedGeneration -> render("GV-WORKER-STOPPED generation=$stoppedGeneration") }
            })
        })
        render("GV-ROUTER-READY")
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun select(slot: String) {
        stopGeneration++
        val profileId = slotProfileId(slot)
        val request = switches.request(profileId)
        if (!request.requiresProcessRestart) {
            authorize(slot)
            return
        }
        if (!workerRunning()) {
            if (switches.processDeathObserved(request.generation) == profileId) authorize(slot)
            return
        }
        stopWorker(
            "GV-PROFILE-SWITCH-WAIT",
            "GV-PROFILE-SWITCH-TIMEOUT",
            request.generation,
            { switches.isPending(request.generation, profileId) },
        ) { stoppedGeneration ->
            if (switches.processDeathObserved(stoppedGeneration) == profileId) authorize(slot)
        }
    }

    private fun stopWorker(
        waitCode: String,
        timeoutCode: String,
        requestedGeneration: Long,
        isCurrent: () -> Boolean,
        stopped: (Long) -> Unit,
    ) {
        render("$waitCode generation=$requestedGeneration")
        sendBroadcast(Intent(this, PrototypeWorkerShutdownReceiver::class.java).setAction(ACTION_SHUTDOWN).apply {
            putExtra(EXTRA_GENERATION, requestedGeneration)
        })
        val deadline = System.currentTimeMillis() + 8_000
        fun poll() {
            if (destroyed || !isCurrent()) return
            terminateAppChildProcesses()
            if (!workerRunning()) stopped(requestedGeneration)
            else if (System.currentTimeMillis() >= deadline) {
                if (timeoutCode == "GV-PROFILE-SWITCH-TIMEOUT") {
                    if (switches.timedOut(requestedGeneration)) render("$timeoutCode generation=$requestedGeneration")
                } else render("$timeoutCode generation=$requestedGeneration")
            } else handler.postDelayed(::poll, 200)
        }
        handler.postDelayed(::poll, 200)
    }

    override fun onDestroy() {
        destroyed = true
        stopGeneration++
        switches.invalidate()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun authorize(slot: String) {
        selectedSlot = slot
        getPreferences(MODE_PRIVATE).edit()
            .putString("slot", slot)
            .putString("profile", slotProfileId(slot))
            .apply()
        render("GV-PROFILE-AUTHORIZED slot=$slot profile=${slotProfileId(slot)}")
        startActivity(Intent(this, GeckoViewPrototypeActivity::class.java).putExtra(EXTRA_SLOT, slot))
    }

    private fun workerRunning(): Boolean {
        val prefix = "$packageName:"
        return (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.orEmpty()
            .any { it.processName.startsWith(prefix) }
    }

    private fun terminateAppChildProcesses() {
        val prefix = "$packageName:"
        val worker = "${packageName}:gecko_prototype"
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.orEmpty()
            .filter { process -> process.processName.startsWith(prefix) && process.processName != worker }
            .forEach { Process.killProcess(it.pid) }
    }

    private fun render(code: String) {
        status.text = "$code\nselected=${selectedSlot ?: "none"}\nrouterPid=${android.os.Process.myPid()}"
    }
}

class PrototypeWorkerShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHUTDOWN) GeckoViewPrototypeActivity.shutdownProcess()
    }
}
