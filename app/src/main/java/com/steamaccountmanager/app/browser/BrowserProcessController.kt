package com.steamaccountmanager.app.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.steamaccountmanager.app.BrowserActivity
import com.steamaccountmanager.app.domain.model.SessionIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Orchestrates opening a given [SessionIdentifier] (account + website pair) with a
 * genuinely isolated, persistent WebView profile.
 *
 * ## Why this exists
 * `WebView.setDataDirectorySuffix(suffix)` is the only supported way to give a
 * WebView its own private cookie / localStorage / IndexedDB / cache directory on
 * Android. Its documented constraints are:
 *   1. It must be called before ANY WebView instance is created in the current
 *      process.
 *   2. It can only be called ONCE per process -- a second call with a different
 *      suffix in the same still-running process throws / is not supported, since
 *      the underlying Chromium data-directory lock is acquired once per process.
 *
 * Because this app needs to move between ~100 possible (account x website) session
 * directories at runtime -- not just once at process start -- a single long-lived
 * process cannot host more than one session's data directory for its entire
 * lifetime. The reliable way to switch is therefore to run the WebView in a
 * dedicated process (declared as `:browser` in the manifest, hosted by
 * [com.steamaccountmanager.app.BrowserActivity]) and fully restart that process
 * every time the requested session differs from whichever suffix it was last
 * started with. A brand-new process is always allowed to call
 * `setDataDirectorySuffix()` for the first time, so this gives full, genuine
 * per-session isolation of cookies/localStorage/IndexedDB/cache with no manual
 * cookie-jar swapping and no risk of one account inheriting another's storage.
 *
 * This also directly satisfies the "don't keep 80 sessions in memory" requirement:
 * by construction, at most one session's WebView is ever alive at a time. Every
 * other session's data simply sits on disk under
 * `/data/data/<pkg>/app_webview_<suffix>/` until it's opened again.
 *
 * The trade-off, documented honestly: switching between two *different* sessions
 * costs a small process-restart delay (typically well under a second on modern
 * hardware) rather than being instantaneous. Re-opening the *same* session that is
 * already the active one is instantaneous (no restart needed).
 */
object BrowserProcessController {

    const val ACTION_SHUTDOWN_BROWSER_PROCESS = "com.steamaccountmanager.app.action.SHUTDOWN_BROWSER_PROCESS"

    private const val ROUTER_PREFS = "browser_router_prefs"
    private const val KEY_ACTIVE_SESSION = "active_session"
    private const val KEY_ROUTING_TOKEN = "routing_token"
    private const val SHUTDOWN_POLL_INTERVAL_MS = 100L
    private const val SHUTDOWN_TIMEOUT_MS = 8_000L
    private const val LAUNCH_CONFIRMATION_TIMEOUT_MS = 8_000L
    private val routingMutex = Mutex()

    /**
     * Opens [sessionId] at [targetUrl], restricted to [allowedDomains]. Suspends
     * briefly on a background dispatcher only when a *different* session is
     * currently loaded and the browser process must be restarted first.
     */
    suspend fun openWebsite(
        context: Context,
        sessionId: SessionIdentifier,
        targetUrl: String,
        allowedDomains: List<String>,
    ) = routingMutex.withLock {
        val appContext = context.applicationContext
        val requestedSuffix = sessionId.dataDirectorySuffix
        val requestedSession = isolationIdentity(sessionId)
        var routingToken = UUID.randomUUID().toString()

        val authorized = withContext(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences(ROUTER_PREFS, Context.MODE_PRIVATE)
            val activeSession = prefs.getString(KEY_ACTIVE_SESSION, null)
                ?: prefs.getString(LEGACY_KEY_ACTIVE_SUFFIX, null)?.let { "webview_$it" }
            val processRunning = browserProcesses(appContext).isNotEmpty()

            if (processRunning && activeSession != requestedSession && !requestShutdownAndAwaitDeath(appContext)) {
                Log.e(TAG, "Browser session switch timed out; refusing to launch the requested profile.")
                return@withContext false
            }
            if (processRunning && activeSession == requestedSession) {
                routingToken = prefs.getString(KEY_ROUTING_TOKEN, null) ?: routingToken
            }

            prefs.edit()
                .putString(KEY_ACTIVE_SESSION, requestedSession)
                .putString(KEY_ROUTING_TOKEN, routingToken)
                .remove(LEGACY_KEY_ACTIVE_SUFFIX)
                .commit()
        }
        if (!authorized) return@withLock

        val intent = Intent(appContext, BrowserActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_DATA_DIR_SUFFIX, requestedSuffix)
            putExtra(EXTRA_ACCOUNT_ID, sessionId.accountId)
            putExtra(EXTRA_WEBSITE_ID, sessionId.websiteId)
            putExtra(EXTRA_START_URL, targetUrl)
            putExtra(EXTRA_ROUTING_TOKEN, routingToken)
            putStringArrayListExtra(EXTRA_ALLOWED_DOMAINS, ArrayList(allowedDomains))
        }
        try {
            appContext.startActivity(intent)
        } catch (error: Exception) {
            clearLaunchAuthorization(appContext, routingToken)
            Log.e(TAG, "Browser worker launch failed; routing authorization was cleared.", error)
            return@withLock
        }

        val workerAppeared = withContext(Dispatchers.IO) { awaitBrowserWorker(appContext) }
        if (!workerAppeared) {
            clearLaunchAuthorization(appContext, routingToken)
            Log.e(TAG, "Browser worker launch was not observed; routing authorization was cleared.")
            withContext(Dispatchers.IO) {
                if (browserProcesses(appContext).isNotEmpty()) requestShutdownAndAwaitDeath(appContext)
            }
        }
    }

    /** Browser-process startup guard against a delayed launch whose router authorization expired. */
    fun isLaunchAuthorized(context: Context, sessionId: SessionIdentifier, routingToken: String?): Boolean {
        if (routingToken == null) return false
        val prefs = context.getSharedPreferences(ROUTER_PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_SESSION, null) == isolationIdentity(sessionId) &&
            prefs.getString(KEY_ROUTING_TOKEN, null) == routingToken
    }

    /** Whether the currently-loaded browser session (if any) is this one. */
    fun isSessionCurrentlyActive(context: Context, sessionId: SessionIdentifier): Boolean {
        val prefs = context.getSharedPreferences(ROUTER_PREFS, Context.MODE_PRIVATE)
        return browserProcesses(context).isNotEmpty() &&
            prefs.getString(KEY_ACTIVE_SESSION, null) == isolationIdentity(sessionId)
    }

    private fun browserProcesses(context: Context): List<ActivityManager.RunningAppProcessInfo> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val prefix = context.packageName + ":"
        return am.runningAppProcesses.orEmpty().filter { it.processName.startsWith(prefix) }
    }

    private fun requestShutdownAndAwaitDeath(context: Context): Boolean {
        context.sendBroadcast(
            Intent(context, BrowserShutdownReceiver::class.java).setAction(ACTION_SHUTDOWN_BROWSER_PROCESS),
        )
        val deadline = System.currentTimeMillis() + SHUTDOWN_TIMEOUT_MS
        val workerName = context.packageName + BROWSER_PROCESS_SUFFIX
        while (System.currentTimeMillis() < deadline) {
            val processes = browserProcesses(context)
            if (processes.isEmpty()) return true
            if (processes.none { it.processName == workerName }) {
                processes.forEach { Process.killProcess(it.pid) }
            }
            Thread.sleep(SHUTDOWN_POLL_INTERVAL_MS)
        }
        return browserProcesses(context).isEmpty()
    }

    private fun awaitBrowserWorker(context: Context): Boolean {
        val deadline = System.currentTimeMillis() + LAUNCH_CONFIRMATION_TIMEOUT_MS
        val workerName = context.packageName + BROWSER_PROCESS_SUFFIX
        while (System.currentTimeMillis() < deadline) {
            if (browserProcesses(context).any { it.processName == workerName }) return true
            Thread.sleep(SHUTDOWN_POLL_INTERVAL_MS)
        }
        return browserProcesses(context).any { it.processName == workerName }
    }

    private fun clearLaunchAuthorization(context: Context, routingToken: String) {
        val prefs = context.getSharedPreferences(ROUTER_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_ROUTING_TOKEN, null) == routingToken) {
            prefs.edit().remove(KEY_ACTIVE_SESSION).remove(KEY_ROUTING_TOKEN).commit()
        }
    }

    private fun isolationIdentity(sessionId: SessionIdentifier): String =
        if (sessionId.websiteId == STEAM_WEBSITE_ID) GeckoProfileIdentity.idFor(sessionId)
        else "webview_${sessionId.dataDirectorySuffix}"

    private const val TAG = "BrowserProcessController"
    private const val STEAM_WEBSITE_ID = "steam"
    private const val LEGACY_KEY_ACTIVE_SUFFIX = "active_suffix"
    private const val BROWSER_PROCESS_SUFFIX = ":browser"
    const val EXTRA_DATA_DIR_SUFFIX = "extra_data_dir_suffix"
    const val EXTRA_ACCOUNT_ID = "extra_account_id"
    const val EXTRA_WEBSITE_ID = "extra_website_id"
    const val EXTRA_START_URL = "extra_start_url"
    const val EXTRA_ROUTING_TOKEN = "extra_routing_token"
    const val EXTRA_ALLOWED_DOMAINS = "extra_allowed_domains"
}
