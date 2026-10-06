package com.takeruf.nagi

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Inject through the window to cover Compose hit testing and the native surface bridge. */
@OptIn(ExperimentalTestApi::class)
class BrowserScrollTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings() }
            container.spaces.create("Scroll QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Search or enter URL").fetchSemanticsNodes().isNotEmpty() }
        openPage()
    }

    @After fun cleanup() { server.close() }

    private fun shortcut(code: Int) {
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, KeyEvent.META_CTRL_ON))
        }
    }

    private fun openPage() {
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement("${server.origin}/scroll")
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.waitUntil(15_000) {
            runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == "Fixture Scroll" && it.closedAt == null } }
        }
        compose.waitUntil(10_000) { webViews().isNotEmpty() }
    }

    private fun webViews(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var views = emptyList<WebView>()
        compose.runOnIdle {
            views = find(compose.activity.window.decorView).filter { it.isShown && it.width > 0 }
                .sortedBy { view -> IntArray(2).also { view.getLocationOnScreen(it) }[0] }
        }
        return views
    }

    private fun metrics(view: WebView): JSONObject {
        val result = LinkedBlockingQueue<String>()
        compose.runOnIdle {
            view.evaluateJavascript("""JSON.stringify({x:scrollX,y:scrollY,nx:document.getElementById('nested').scrollLeft,
                ny:document.getElementById('nested').scrollTop,wheels:window.wheelCount,width:innerWidth,height:innerHeight})""") { result.offer(it) }
        }
        val value = result.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("No WebView metrics")
        return JSONObject(org.json.JSONTokener(value).nextValue() as String)
    }

    private fun settledMetrics(view: WebView): JSONObject {
        var previous = emptyList<Double>()
        var lastChange = SystemClock.uptimeMillis()
        compose.waitUntil(5_000) {
            val current = metrics(view).let { value -> listOf("x", "y", "nx", "ny").map { value.getDouble(it) } }
            if (current != previous) { previous = current; lastChange = SystemClock.uptimeMillis() }
            SystemClock.uptimeMillis() - lastChange >= 200
        }
        return metrics(view)
    }

    private fun scroll(view: WebView, source: Int, vertical: Float = 0f, horizontal: Float = 0f, nested: Boolean = false) {
        compose.runOnIdle {
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            val decorLocation = IntArray(2).also { compose.activity.window.decorView.getLocationOnScreen(it) }
            val localX = if (nested) 100 * view.resources.displayMetrics.density else view.width * 0.75f
            val localY = if (nested) 100 * view.resources.displayMetrics.density else view.height * 0.75f
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = if (source == InputDevice.SOURCE_TOUCHPAD) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
            }
            val coordinates = MotionEvent.PointerCoords().apply {
                x = location[0] - decorLocation[0] + localX
                y = location[1] - decorLocation[1] + localY
                setAxisValue(MotionEvent.AXIS_VSCROLL, vertical)
                setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal)
            }
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(properties), arrayOf(coordinates),
                0, 0, 1f, 1f, 0, 0, source, 0)
            try { compose.activity.window.callback.dispatchGenericMotionEvent(event) } finally { event.recycle() }
        }
    }

    @Test fun mouseWheelScrollsPageInBothAxesAndDirections() {
        val view = webViews().single()
        scroll(view, InputDevice.SOURCE_MOUSE, vertical = -3f, horizontal = -3f)
        compose.waitUntil(5_000) { metrics(view).let { it.getInt("x") > 0 && it.getInt("y") > 0 && it.getInt("wheels") == 1 } }
        val before = settledMetrics(view)
        scroll(view, InputDevice.SOURCE_MOUSE, vertical = 1f, horizontal = 1f)
        compose.waitUntil(5_000) { metrics(view).let { it.getInt("x") < before.getInt("x") && it.getInt("y") < before.getInt("y") } }
    }

    @Test fun touchpadScrollReachesNestedHtmlScroller() {
        val view = webViews().single()
        scroll(view, InputDevice.SOURCE_TOUCHPAD, vertical = -3f, horizontal = -3f, nested = true)
        compose.waitUntil(5_000) { metrics(view).let { it.getInt("nx") > 0 && it.getInt("ny") > 0 && it.getInt("wheels") == 1 } }
        assertEquals(0, metrics(view).getInt("x"))
        assertEquals(0, metrics(view).getInt("y"))
        val before = settledMetrics(view)
        scroll(view, InputDevice.SOURCE_TOUCHPAD, vertical = 1f, horizontal = 1f, nested = true)
        compose.waitUntil(5_000) {
            metrics(view).let { it.getInt("nx") < before.getInt("nx") && it.getInt("ny") < before.getInt("ny") && it.getInt("wheels") == 2 }
        }
    }

    @Test fun touchSwipeAndFractionalWheelScrollKeepWorkingTogether() {
        val view = webViews().single()
        compose.runOnIdle {
            val location = IntArray(2).also { view.getLocationOnScreen(it) }
            val decorLocation = IntArray(2).also { compose.activity.window.decorView.getLocationOnScreen(it) }
            val x = location[0] - decorLocation[0] + view.width * 0.75f
            val startY = location[1] - decorLocation[1] + view.height * 0.8f
            val now = SystemClock.uptimeMillis()
            for (step in 0..10) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 10 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(now, now + step * 20, action, x, startY - view.height * 0.05f * step, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { compose.activity.window.callback.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        compose.waitUntil(5_000) { metrics(view).getInt("y") > 0 }
        scroll(view, InputDevice.SOURCE_MOUSE, vertical = -0.25f)
        compose.waitUntil(5_000) { metrics(view).getInt("wheels") == 1 }
        scroll(view, InputDevice.SOURCE_TOUCHPAD, vertical = -0.25f)
        compose.waitUntil(5_000) { metrics(view).getInt("wheels") == 2 }
    }

    @Test fun scrollingTargetsPaneUnderPointerWithoutPriorClick() {
        shortcut(KeyEvent.KEYCODE_T)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Search or enter URL").fetchSemanticsNodes().isNotEmpty() }
        openPage()
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("New split view").performClick()
        compose.waitUntil(10_000) { webViews().size == 2 }
        val views = webViews()
        scroll(views[1], InputDevice.SOURCE_MOUSE, vertical = -2f)
        compose.waitUntil(5_000) { metrics(views[1]).getInt("y") > 0 }
        assertEquals(0, metrics(views[0]).getInt("y"))
        scroll(views[0], InputDevice.SOURCE_TOUCHPAD, vertical = -2f)
        compose.waitUntil(5_000) { metrics(views[0]).getInt("y") > 0 }
        // Scrolling the right pane also makes its browser shortcuts active.
        scroll(views[1], InputDevice.SOURCE_MOUSE, vertical = -1f)
        shortcut(KeyEvent.KEYCODE_W)
        compose.waitUntil(5_000) { webViews().size == 1 }
        assertSame(views[0], webViews().single())
    }
}
