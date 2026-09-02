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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

internal const val EXTRA_SLOT = "prototype_slot"
internal const val ACTION_SHUTDOWN = "com.steamaccountmanager.app.debug.PROTOTYPE_SHUTDOWN"
internal const val EXTRA_GENERATION = "prototype_generation"
internal const val LOOPBACK_PORT = 38947
internal const val MARKER_EXTENSION_ID = "issue6-marker@steam-account-manager.invalid"
internal const val MARKER_NATIVE_APP = "issue6Marker"
private val GECKO_CHILD_NAMES = setOf("tab", "gpu", "crashhelper", "socket", "rdd", "utility", "extension")

internal fun slotProfileId(slot: String): String = when (slot) {
    "A" -> geckoProfileId("synthetic-account-a", "synthetic-website")
    "B" -> geckoProfileId("synthetic-account-b", "synthetic-website")
    else -> error("GV-PROFILE-SLOT-INVALID")
}

class GeckoPrototypeRouterActivity : ComponentActivity() {
    private lateinit var status: TextView
    private var generation = 0L
    private var selectedSlot: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrototypeLoopbackServer.start()
        selectedSlot = getPreferences(MODE_PRIVATE).getString("slot", null)
        status = TextView(this).apply { setPadding(24, 24, 24, 24) }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(button("Open synthetic slot A") { select("A") })
            addView(button("Open synthetic slot B") { select("B") })
            addView(button("Reopen selected slot") { selectedSlot?.let(::select) })
            addView(button("Recreate router activity") { recreate() })
            addView(button("Stop worker process") {
                stopWorker(
                    "GV-WORKER-STOP-WAIT",
                    "GV-WORKER-STOP-TIMEOUT",
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
        val previous = selectedSlot
        if (previous == null || previous == slot || !workerRunning()) {
            authorize(slot)
            return
        }
        stopWorker(
            "GV-PROFILE-SWITCH-WAIT",
            "GV-PROFILE-SWITCH-TIMEOUT",
        ) { authorize(slot) }
    }

    private fun stopWorker(
        waitCode: String,
        timeoutCode: String,
        stopped: (Long) -> Unit,
    ) {
        generation += 1
        val requestedGeneration = generation
        render("$waitCode generation=$requestedGeneration")
        sendBroadcast(Intent(this, PrototypeWorkerShutdownReceiver::class.java).setAction(ACTION_SHUTDOWN).apply {
            putExtra(EXTRA_GENERATION, requestedGeneration)
        })
        val deadline = System.currentTimeMillis() + 8_000
        fun poll() {
            if (requestedGeneration != generation) return
            terminateRecognizedGeckoChildren()
            if (!workerRunning()) stopped(requestedGeneration)
            else if (System.currentTimeMillis() >= deadline) {
                render("$timeoutCode generation=$requestedGeneration")
            } else handler.postDelayed(::poll, 200)
        }
        handler.postDelayed(::poll, 200)
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
            .any { it.processName == "${packageName}:gecko_prototype" ||
                (it.processName.startsWith(prefix) && GECKO_CHILD_NAMES.any { child ->
                    it.processName.removePrefix(prefix).startsWith(child)
                }) }
    }

    private fun terminateRecognizedGeckoChildren() {
        val prefix = "$packageName:"
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses.orEmpty()
            .filter { process -> process.processName.startsWith(prefix) && GECKO_CHILD_NAMES.any { child ->
                process.processName.removePrefix(prefix).startsWith(child)
            } }
            .forEach { Process.killProcess(it.pid) }
    }

    private fun render(code: String) {
        status.text = "$code\nselected=${selectedSlot ?: "none"}\nrouterPid=${android.os.Process.myPid()}"
    }
}

private object PrototypeLoopbackServer {
    @Volatile private var started = false

    @Synchronized fun start() {
        if (started) return
        started = true
        Executors.newSingleThreadExecutor().execute {
            try {
                ServerSocket(LOOPBACK_PORT, 8, InetAddress.getByName("127.0.0.1")).use { server ->
                    while (true) try {
                        server.accept().use { socket ->
                            socket.soTimeout = 2_000
                            val request = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII))
                                .readLine().orEmpty()
                            val slot = Regex("^GET /slot/([AB]) HTTP/1\\.[01]$").matchEntire(request)?.groupValues?.get(1)
                            val body = if (slot == null) "<!doctype html><title>GV6|error=request</title>" else page(slot)
                            val status = if (slot == null) "400 Bad Request" else "200 OK"
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
            } catch (_: Exception) {
                started = false
            }
        }
    }

    private fun page(slot: String) = """<!doctype html><meta charset=utf-8><body><h1>Issue 6 synthetic slot $slot</h1><pre id=o>GV6|loading</pre><script>
const s='$slot'; if(!document.cookie.includes('gv6='))document.cookie='gv6='+s+'; SameSite=Strict';
if(!localStorage.gv6)localStorage.gv6=s;if(!localStorage.gv6nav)localStorage.gv6nav=s;
const n=localStorage.gv6nav;if(!history.state)history.replaceState({gv6:n},'',location.pathname+'#'+n);
const q=indexedDB.open('gv6',1);q.onupgradeneeded=()=>q.result.createObjectStore('m');q.onsuccess=()=>{const d=q.result.transaction('m','readwrite').objectStore('m');const g=d.get('slot');g.onsuccess=()=>{const prior=g.result||s;if(!g.result)d.put(s,'slot');const c=(document.cookie.match(/gv6=([AB])/)||[])[1]||'missing';const l=localStorage.gv6||'missing';const text=`GV6|slot=${'$'}{s}|cookie=${'$'}{c}|local=${'$'}{l}|idb=${'$'}{prior}|nav=${'$'}{n}`;document.title=text;document.getElementById('o').textContent=text;};};
</script></body>"""
}

class PrototypeWorkerShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHUTDOWN) GeckoViewPrototypeActivity.shutdownProcess()
    }
}
