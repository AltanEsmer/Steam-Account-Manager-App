package com.steamaccountmanager.app.prototype

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrototypeProfileIsolationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun latestReopenWinsWhileCrossProfileShutdownIsPending() {
        val router = ComponentName(context, GeckoPrototypeRouterActivity::class.java)
        context.startActivity(Intent().setComponent(router).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText("GV-ROUTER-READY")
        click("Open synthetic slot A")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 45_000)

        backToRouter()
        click("Open synthetic slot B")
        click("Reopen selected slot")

        SystemClock.sleep(3_000)
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 20_000)
    }

    @Test
    fun syntheticSlotsRestoreEngineAndExtensionMarkersAcrossRealWorkerLifecycles() {
        val router = ComponentName(context, GeckoPrototypeRouterActivity::class.java)
        val worker = ComponentName(context, GeckoViewPrototypeActivity::class.java)
        assertTrue(context.packageManager.getActivityInfo(router, 0).exported)
        assertFalse(context.packageManager.getActivityInfo(worker, 0).exported)

        context.startActivity(Intent().setComponent(router).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText("GV-ROUTER-READY")
        click("Open synthetic slot A")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)
        ensureMarkerEnabled("A")
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)
        awaitText("GV-MARKER-STATE-ENABLED slot=A")

        instrumentation.runOnMainSync {
            instrumentation.uiAutomation.rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        click("Recreate worker activity")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)

        backToRouter()
        click("Reopen selected slot")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)

        click("Disable issue6 marker")
        awaitText("GV-MARKER-STATE-DISABLED slot=A")

        backToRouter()
        click("Open synthetic slot B")
        awaitText("GV6|slot=B|cookie=B|local=B|idb=B|nav=B", 45_000)
        ensureMarkerEnabled("B")
        awaitText("GV-MARKER-RESULT slot=B prior=B current=B", 30_000)
        awaitText("GV-MARKER-STATE-ENABLED slot=B")

        backToRouter()
        click("Open synthetic slot A")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 45_000)
        awaitText("GV-MARKER-STATE-DISABLED slot=A")
        click("Enable issue6 marker")
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)

        click("Uninstall issue6 marker")
        awaitText("GV-MARKER-STATE-ABSENT slot=A")

        backToRouter()
        click("Open synthetic slot B")
        awaitText("GV-MARKER-STATE-ENABLED slot=B", 45_000)
        awaitText("GV-MARKER-RESULT slot=B prior=B current=B", 30_000)

        backToRouter()
        click("Open synthetic slot A")
        awaitText("GV-MARKER-STATE-ABSENT slot=A", 45_000)
        click("Reinstall issue6 marker (synthetic only)")
        awaitText("GV-MARKER-STATE-ENABLED slot=A", 30_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)

        backToRouter()
        click("Stop worker process")
        awaitText("GV-WORKER-STOPPED", 30_000)
        click("Reopen selected slot")
        awaitText("GV-MARKER-STATE-ENABLED slot=A", 45_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)
    }

    private fun backToRouter() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        awaitText("Reopen selected slot")
    }

    private fun ensureMarkerEnabled(slot: String) {
        when (awaitAny("GV-MARKER-STATE-ENABLED slot=$slot", "GV-MARKER-STATE-DISABLED slot=$slot", "GV-MARKER-STATE-ABSENT slot=$slot")) {
            1 -> click("Enable issue6 marker")
            2 -> click("Reinstall issue6 marker (synthetic only)")
        }
        awaitText("GV-MARKER-STATE-ENABLED slot=$slot", 30_000)
    }

    private fun awaitAny(vararg texts: String): Int {
        val deadline = SystemClock.uptimeMillis() + 30_000
        do {
            texts.forEachIndexed { index, text ->
                if (instrumentation.uiAutomation.rootInActiveWindow
                        ?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
                ) return index
            }
            scroll(true)
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("No fixed marker state became visible")
    }

    private fun click(text: String) {
        val node = awaitNode(text)
        instrumentation.runOnMainSync { assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }
    }

    private fun awaitText(text: String, timeoutMs: Long = 10_000) = awaitNode(text, timeoutMs)

    private fun awaitNode(text: String, timeoutMs: Long = 10_000): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        repeat(12) { scroll(false) }
        do {
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
            if (node != null) return node
            scroll(true)
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        val visibleCodes = buildList {
            fun collect(node: AccessibilityNodeInfo?) {
                node ?: return
                node.text?.toString()?.takeIf { it.startsWith("GV-") || it.startsWith("GV6|") }?.let(::add)
                repeat(node.childCount) { collect(node.getChild(it)) }
            }
            collect(instrumentation.uiAutomation.rootInActiveWindow)
        }
        throw AssertionError("Fixed prototype marker was not visible: $text; visible=$visibleCodes")
    }

    private fun scroll(forward: Boolean) {
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            node ?: return null
            if (node.isScrollable) return node
            repeat(node.childCount) { scrollable(node.getChild(it))?.let { found -> return found } }
            return null
        }
        scrollable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )
    }
}
