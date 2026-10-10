package com.takeruf.nagi

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Window-level injection covers pointer routing, native arbitration, and actual page history. */
class HistorySwipeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false) }
            container.spaces.create("Swipe QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            val tab = container.workspace.dao.tabs(spaceId).single { it.closedAt == null }
            container.tabs.create(spaceId, "${server.origin}/one")
            container.tabs.close(tab.id)
        }
        compose.waitUntil(15_000) { views().size == 1 && ready(views().single(), "/one") }
    }

    @After fun cleanup() { server.close() }

    private fun views(): List<WebView> {
        fun find(v: View): List<WebView> = when (v) {
            is WebView -> listOf(v)
            is ViewGroup -> (0 until v.childCount).flatMap { find(v.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown }
            .sortedBy { IntArray(2).also(it::getLocationOnScreen)[0] } }
        return result
    }

    private fun ready(v: WebView, path: String): Boolean {
        var result = false
        compose.runOnIdle { result = v.url == "${server.origin}$path" && v.progress == 100 }
        return result
    }

    private fun load(v: WebView, path: String) {
        compose.runOnIdle { v.loadUrl("${server.origin}$path") }
        compose.waitUntil(15_000) { ready(v, path) }
    }

    private fun javascript(v: WebView, script: String): String {
        val queue = LinkedBlockingQueue<String>()
        compose.runOnIdle { v.evaluateJavascript(script) { queue.offer(it) } }
        return queue.poll(5, TimeUnit.SECONDS) ?: error("JavaScript did not answer")
    }

    private fun point(v: WebView): Pair<Float, Float> {
        val location = IntArray(2).also(v::getLocationOnScreen)
        val decor = IntArray(2).also(compose.activity.window.decorView::getLocationOnScreen)
        return (location[0] - decor[0] + v.width * 0.4f) to (location[1] - decor[1] + v.height * 0.65f)
    }

    private fun swipe(v: WebView, xDp: Float, yDp: Float = 0f, pinchDp: Float = 0f,
        cancel: Boolean = false, fingers: Int = 2, reverse: Boolean = false) {
        compose.runOnIdle {
            val (x, y) = point(v)
            val d = v.resources.displayMetrics.density
            val time = SystemClock.uptimeMillis()
            val properties = Array(fingers) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            fun send(action: Int, count: Int, fraction: Float, step: Int) {
                val coords = Array(count) { i -> MotionEvent.PointerCoords().apply {
                    this.x = x + xDp * d * fraction + i * 44f * d + (if (i == 0) -1 else 1) * pinchDp * d * fraction
                    this.y = y + yDp * d * fraction; pressure = 1f; size = 0.1f
                } }
                val event = MotionEvent.obtain(time, time + step * 16L, action, count, properties.take(count).toTypedArray(), coords,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { compose.activity.window.callback.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 1, 0f, 0)
            for (i in 1 until fingers) send(MotionEvent.ACTION_POINTER_DOWN or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), i + 1, 0f, i)
            for (i in 1..10) send(MotionEvent.ACTION_MOVE, fingers, i / 10f, i + fingers)
            if (reverse) for (i in 9 downTo 0) send(MotionEvent.ACTION_MOVE, fingers, i / 10f, 22 - i)
            if (cancel) send(MotionEvent.ACTION_CANCEL, fingers, 1f, 33)
            else {
                for (i in fingers - 1 downTo 1) send(MotionEvent.ACTION_POINTER_UP or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), i + 1, if (reverse) 0f else 1f, 34 + fingers - i)
                send(MotionEvent.ACTION_UP, 1, if (reverse) 0f else 1f, 40)
            }
        }
        compose.waitForIdle()
    }

    private fun wheel(v: WebView, horizontal: Float, vertical: Float = 0f, source: Int = InputDevice.SOURCE_TOUCHPAD,
        nested: Boolean = false, finger: Boolean = source == InputDevice.SOURCE_TOUCHPAD, samples: Int = 1) {
        compose.runOnIdle {
            var (x, y) = point(v)
            if (nested) {
                val location = IntArray(2).also(v::getLocationOnScreen)
                val decor = IntArray(2).also(compose.activity.window.decorView::getLocationOnScreen)
                x = location[0] - decor[0] + 100 * v.resources.displayMetrics.density
                y = location[1] - decor[1] + 100 * v.resources.displayMetrics.density
            }
            val props = MotionEvent.PointerProperties().apply { id = 0; toolType = if (finger) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE }
            val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y
                setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal); setAxisValue(MotionEvent.AXIS_VSCROLL, vertical) }
            repeat(samples) {
                val now = SystemClock.uptimeMillis()
                val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f,
                    0, 0, source, 0)
                try { compose.activity.window.callback.dispatchGenericMotionEvent(event) } finally { event.recycle() }
            }
        }
    }

    @Test fun touchscreenTwoFingerSwipesKeepHistoryInBothDirections() {
        val v = views().single()
        load(v, "/two"); load(v, "/one?third")
        swipe(v, 180f)
        assertTrue(ready(v, "/one?third"))
        wheel(v, 4f)
        compose.waitUntil(10_000) { ready(v, "/two") }
        swipe(v, -180f)
        assertTrue(ready(v, "/two"))
    }

    @androidx.annotation.RequiresApi(36)
    private fun classifiedSwipe(v: WebView, xDp: Float, cancel: Boolean = false) {
        compose.runOnIdle {
            val (x, y) = point(v)
            val time = SystemClock.uptimeMillis()
            val props = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
            for (step in 0..11) {
                val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 11 -> if (cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val coords = MotionEvent.PointerCoords().apply {
                    this.x = x + xDp * v.resources.displayMetrics.density * minOf(step, 10) / 10
                    this.y = y; pressure = 1f
                    if (step in 1..10) setAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE, -xDp * v.resources.displayMetrics.density / 10)
                }
                val event = requireNotNull(MotionEvent.obtain(time, time + step * 16L, action, 1, arrayOf(props), arrayOf(coords),
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0, 0,
                    if (step == 11) MotionEvent.CLASSIFICATION_NONE else MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE))
                try { compose.activity.window.callback.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 36)
    @Test fun androidClassifiedTouchpadScrollUsesPixelAxesAndHonorsCancellation() {
        val v = views().single(); load(v, "/two")
        classifiedSwipe(v, 180f, cancel = true)
        assertTrue(ready(v, "/two"))
        classifiedSwipe(v, 180f)
        compose.waitUntil(10_000) { ready(v, "/one") }
        classifiedSwipe(v, -180f)
        compose.waitUntil(10_000) { ready(v, "/two") }
    }

    @Test fun touchscreenGesturesKeepHistory() {
        val v = views().single(); load(v, "/two")
        swipe(v, 40f); swipe(v, 100f, yDp = 180f); swipe(v, 180f, pinchDp = 45f)
        swipe(v, 180f, cancel = true); swipe(v, 180f, reverse = true)
        swipe(v, 180f, fingers = 1); swipe(v, 180f, fingers = 3)
        assertTrue(ready(v, "/two"))
    }

    @Test fun touchpadBurstNavigatesOnceAndMouseHorizontalWheelKeepsHistory() {
        val v = views().single(); load(v, "/two"); load(v, "/one?third")
        wheel(v, 4f, samples = 6)
        compose.waitUntil(10_000) { ready(v, "/two") }
        assertTrue(ready(v, "/two"))
        // End the scroll burst before the opposite gesture.
        SystemClock.sleep(300)
        wheel(v, -4f, source = InputDevice.SOURCE_MOUSE, finger = true)
        compose.waitUntil(10_000) { ready(v, "/one?third") }
        wheel(v, 4f, source = InputDevice.SOURCE_MOUSE)
        assertTrue(ready(v, "/one?third"))
    }

    @Test fun touchpadKeepsNestedAndPageHorizontalScrolling() {
        val v = views().single(); load(v, "/two"); load(v, "/scroll")
        javascript(v, "document.body.style.width='100%'; true")
        wheel(v, -4f, nested = true)
        compose.waitUntil(5_000) { javascript(v, "document.getElementById('nested').scrollLeft").toDouble() > 0 }
        assertEquals("0", javascript(v, "scrollX"))
        assertTrue(ready(v, "/scroll"))
        SystemClock.sleep(300)
        javascript(v, "document.body.style.width='4000px'; true")
        wheel(v, -4f)
        compose.waitUntil(5_000) { javascript(v, "scrollX").toDouble() > 0 }
        assertTrue(ready(v, "/scroll"))
    }

    @Test fun splitTrackpadSwipeTargetsAndFocusesOnlyThePointedPane() {
        val left = views().single(); load(left, "/two")
        runBlocking { container.tabs.create(spaceId, "${server.origin}/one", select = false) }
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON)) }
        compose.onNode(hasSetTextAction()).performTextReplacement(">split")
        compose.onNodeWithText("New split view").performClick()
        compose.waitUntil(10_000) { views().size == 2 }
        val right = views()[1]; load(right, "/one"); load(right, "/two")
        wheel(right, 4f)
        compose.waitUntil(10_000) { ready(right, "/one") }
        assertTrue(ready(left, "/two"))
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_W, 0, KeyEvent.META_CTRL_ON)) }
        compose.waitUntil(10_000) { views().size == 1 }
        assertSame(left, views().single())
    }

    @Test fun historyBoundaryDoesNotCloseTabOrActivity() {
        val v = views().single()
        compose.runOnIdle { v.clearHistory() }
        val before = runBlocking { container.workspace.dao.tabs(spaceId).count { it.closedAt == null } }
        wheel(v, 4f); SystemClock.sleep(300); wheel(v, -4f)
        assertTrue(ready(v, "/one"))
        assertEquals(before, runBlocking { container.workspace.dao.tabs(spaceId).count { it.closedAt == null } })
        assertFalse(compose.activity.isFinishing)
    }
}
