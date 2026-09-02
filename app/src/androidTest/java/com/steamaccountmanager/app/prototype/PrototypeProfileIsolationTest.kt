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
    fun syntheticSlotsRestoreEngineAndExtensionMarkersAcrossRealWorkerLifecycles() {
        val router = ComponentName(context, GeckoPrototypeRouterActivity::class.java)
        val worker = ComponentName(context, GeckoViewPrototypeActivity::class.java)
        assertTrue(context.packageManager.getActivityInfo(router, 0).exported)
        assertFalse(context.packageManager.getActivityInfo(worker, 0).exported)

        context.startActivity(Intent().setComponent(router).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitText("GV-ROUTER-READY")
        click("Open synthetic slot A")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)

        instrumentation.runOnMainSync {
            instrumentation.uiAutomation.rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        click("Recreate worker activity")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)

        backToRouter()
        click("Reopen selected slot")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 30_000)

        backToRouter()
        click("Open synthetic slot B")
        awaitText("GV6|slot=B|cookie=B|local=B|idb=B|nav=B", 45_000)
        awaitText("GV-MARKER-RESULT slot=B prior=B current=B", 30_000)

        backToRouter()
        click("Open synthetic slot A")
        awaitText("GV6|slot=A|cookie=A|local=A|idb=A|nav=A", 45_000)
        awaitText("GV-MARKER-RESULT slot=A prior=A current=A", 30_000)
    }

    private fun backToRouter() {
        instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        awaitText("Reopen selected slot")
    }

    private fun click(text: String) {
        val node = awaitNode(text)
        instrumentation.runOnMainSync { assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) }
    }

    private fun awaitText(text: String, timeoutMs: Long = 10_000) = awaitNode(text, timeoutMs)

    private fun awaitNode(text: String, timeoutMs: Long = 10_000): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        do {
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
            if (node != null) return node
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
}
