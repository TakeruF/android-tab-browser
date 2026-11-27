package com.takeruf.nagi

import android.content.ClipboardManager
import android.os.SystemClock
import android.view.ActionMode
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Native selection menus/actions through actual WebView input, without a JS selection UI. */
class NativeSelectionMenuTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var view: WebView
    private lateinit var server: FixtureServer
    private val modes = CopyOnWriteArrayList<ActionMode>()
    private var originalAccessibilityFlags = 0
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { view.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun awaitMode(): ActionMode = runBlocking {
        try { withTimeout(5_000) { while (modes.isEmpty() || copyNode() == null) delay(20) } }
        catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("Floating toolbar not visible: modes=${modes.map { it.type }}, selection=${js("getSelection().toString()")}, focused=${js("document.hasFocus()")}", failure)
        }
        modes.last().also { assertEquals(ActionMode.TYPE_FLOATING, it.type) }
    }
    private fun copyNode(): android.view.accessibility.AccessibilityNodeInfo? {
        fun find(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.isVisibleToUser && node.text?.toString() == view.context.getString(android.R.string.copy)) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) return node
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.windows.firstNotNullOfOrNull { it.root?.let(::find) }
    }
    private fun point(id: String): Pair<Float, Float> {
        val coordinates = JSONArray(js("""(()=>{
            const e=document.getElementById('$id');let r=e.getBoundingClientRect();
            if(e.tagName!=='TEXTAREA'){
                const range=document.createRange();range.setStart(e.firstChild,0);
                range.setEnd(e.firstChild,e.textContent.split(' ')[0].length);r=range.getClientRects()[0];
            }
            return [r.x+Math.min(24,r.width/2),r.y+r.height/2,innerWidth];
        })()"""))
        var scale = 1.0
        main { scale = view.width / coordinates.getDouble(2) }
        return (coordinates.getDouble(0) * scale).toFloat() to (coordinates.getDouble(1) * scale).toFloat()
    }
    private fun mouseSecondary(id: String) {
        val (x, y) = point(id)
        main {
            val time = SystemClock.uptimeMillis()
            val properties = MotionEvent.PointerProperties().apply { this.id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coordinates = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f }
            for ((action, buttons) in listOf(MotionEvent.ACTION_DOWN to MotionEvent.BUTTON_SECONDARY, MotionEvent.ACTION_UP to 0)) {
                val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                    arrayOf(properties), arrayOf(coordinates), 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
    }
    private fun fingerLongPress(id: String) {
        val (x, y) = point(id)
        val time = SystemClock.uptimeMillis()
        main {
            val event = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        runBlocking { withTimeout(5_000) { while (modes.isEmpty()) delay(20) } }
        main {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            try { view.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }
    private fun copy(mode: ActionMode, expectedSelection: String? = null): String {
        assertEquals(ActionMode.TYPE_FLOATING, mode.type)
        val expected = expectedSelection ?: org.json.JSONTokener(js("""(()=>{const e=document.activeElement;
            return e?.tagName==='TEXTAREA'?e.value.slice(e.selectionStart,e.selectionEnd):getSelection().toString()})()""")).nextValue() as String
        val button = copyNode() ?: error("Native floating toolbar Copy button is not visible")
        var target = button
        while (!target.isClickable) target = target.parent ?: break
        assertTrue("Visible native Copy must be actionable", target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        var result = ""
        runBlocking { withTimeout(5_000) { do {
            main { result = view.context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(view.context).toString() }
            if (result != expected) delay(20)
        } while (result != expected) } }
        return result
    }
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
        val directory = java.io.File(view.context.getExternalFilesDir(null), "selection-menu").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Before fun setup() {
        val accessibility = instrumentation.uiAutomation.serviceInfo
        originalAccessibilityFlags = accessibility.flags
        accessibility.flags = accessibility.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = accessibility
        server = FixtureServer(pages = mapOf("/selection" to """<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'>
            <style>body{margin:60px;font:24px sans-serif}p{width:fit-content}iframe{width:100%;height:160px}</style>
            <p id='text'>Alpha beta gamma delta</p><p id='other'>Another selectable sentence</p><textarea id='editor'>Editable draft text</textarea>
            <iframe src='/selection-frame'></iframe>""", "/selection-frame" to """<!doctype html><p id='frame'>Frame selection text</p>"""))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val original = activity.window.callback
            activity.window.callback = object : Window.Callback by original {
                override fun onActionModeStarted(mode: ActionMode) { modes.add(mode); original.onActionModeStarted(mode) }
            }
            engine = WebViewBrowserEngine(activity, host, host, { false }, false, nativePageDrag = { true })
            view = engine.surface as WebView
            // This fixture owns the window; the app's hidden new-tab editor must
            // not compete with the tested WebView for keyboard/window focus.
            activity.setContentView(view, ViewGroup.LayoutParams(-1, -1))
            engine.loadUrl(server.origin + "/selection")
        }
        runBlocking { withTimeout(15_000) { engine.state.first { !it.isLoading && it.url == server.origin + "/selection" && it.progress == 100 } } }
        val painted = CompletableDeferred<Unit>()
        main { view.postVisualStateCallback(0, object : WebView.VisualStateCallback() { override fun onComplete(requestId: Long) { painted.complete(Unit) } }) }
        runBlocking { withTimeout(5_000) { painted.await() } }
        main { view.requestFocus() }
        runBlocking { withTimeout(5_000) { while (!js("document.hasFocus()").toBoolean()) delay(20) } }
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { modes.forEach(ActionMode::finish); engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) server.close()
        val accessibility = instrumentation.uiAutomation.serviceInfo
        accessibility.flags = originalAccessibilityFlags
        instrumentation.uiAutomation.serviceInfo = accessibility
    }

    @Test fun fingerAndSecondaryClickUseFloatingNativeCopyMenu() {
        fingerLongPress("text")
        val fingerMode = awaitMode()
        screenshot("finger")
        val finger = copy(fingerMode)
        assertEquals("Alpha", finger)
        modes.clear()
        mouseSecondary("text")
        val secondary = awaitMode()
        screenshot("secondary")
        assertEquals(finger, copy(secondary))
    }

    @Test fun secondaryClickPreservesAnExistingMultiwordSelection() {
        js("(()=>{const s=getSelection(),r=document.createRange();r.selectNodeContents(document.getElementById('text'));s.removeAllRanges();s.addRange(r);return true})()")
        val selected = js("getSelection().toString()")
        mouseSecondary("text")
        val mode = awaitMode()
        screenshot("secondary-selected")
        assertEquals("Existing selection must survive the menu gesture", selected, js("getSelection().toString()"))
        assertEquals("Alpha beta gamma delta", copy(mode))
    }

    @Test fun secondaryClickUsesNativeEditableSelectionWithoutSubmittingOrChangingDraft() {
        mouseSecondary("editor")
        val mode = awaitMode()
        screenshot("secondary-editable")
        assertTrue(mode.menu.size() >= 2)
        assertEquals("\"Editable draft text\"", js("document.getElementById('editor').value"))
        assertEquals("Editable", copy(mode))
    }

    @Test fun secondaryClickOutsideTheSelectionUsesTheClickedNativeWord() {
        js("(()=>{const s=getSelection(),r=document.createRange();r.selectNodeContents(document.getElementById('text'));s.removeAllRanges();s.addRange(r);return true})()")
        mouseSecondary("other")
        val mode = awaitMode()
        assertEquals("Another", copy(mode))
    }

    private fun mouseDragSelection(id: String, start: Int, end: Int, source: Int = InputDevice.SOURCE_MOUSE, frameUrl: String? = null) {
        main { view.requestFocus() }
        val location = IntArray(2)
        main { view.getLocationOnScreen(location) }
        val points = JSONArray(js("""(()=>{
            const frame='$id'==='frame'?document.querySelector('iframe'):null;
            const e=(frame?frame.contentDocument:document).getElementById('$id'),box=e.getBoundingClientRect();
            const offset=frame?frame.getBoundingClientRect():{left:0,top:0};
            function x(offset) {
                if(e.tagName==='TEXTAREA') {
                    const style=getComputedStyle(e), canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
                    ctx.font=style.font;
                    return box.left+e.clientLeft+parseFloat(style.paddingLeft)+ctx.measureText(e.value.slice(0,offset)).width-e.scrollLeft;
                }
                const range=document.createRange();range.setStart(e.firstChild,offset);range.collapse(true);
                return range.getBoundingClientRect().left;
            }
            return [x($start)+offset.left+(frame?frame.clientLeft:0)+0.1,
                x($end)+offset.left+(frame?frame.clientLeft:0)+0.1,
                box.top+box.height/2+offset.top+(frame?frame.clientTop:0),innerWidth];
        })()"""))
        if (frameUrl != null) {
            js("window.frameLoaded=false;document.querySelector('iframe').onload=()=>window.frameLoaded=true;document.querySelector('iframe').src=" + org.json.JSONObject.quote(frameUrl) + ";true")
            runBlocking { withTimeout(5_000) { while (js("window.frameLoaded") != "true") delay(20) } }
            val painted = CompletableDeferred<Unit>()
            main { view.postVisualStateCallback(1, object : WebView.VisualStateCallback() { override fun onComplete(requestId: Long) { painted.complete(Unit) } }) }
            runBlocking { withTimeout(5_000) { painted.await() } }
        }
        val scale = view.width / points.getDouble(3)
        val startX = (points.getDouble(0) * scale).toFloat()
        val endX = (points.getDouble(1) * scale).toFloat()
        val y = (points.getDouble(2) * scale).toFloat()
        val time = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply { this.id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        fun send(action: Int, x: Float, buttons: Int) {
            val point = MotionEvent.PointerCoords().apply { this.x = x + location[0]; this.y = y + location[1]; pressure = 1f }
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                arrayOf(properties), arrayOf(point), 0, buttons, 1f, 1f, 0, 0, source, 0)
            try {
                if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                    MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType).invoke(event, MotionEvent.BUTTON_PRIMARY)
                }
                assertTrue("Native mouse input must be delivered", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_HOVER_MOVE, startX, 0)
        send(MotionEvent.ACTION_DOWN, startX, MotionEvent.BUTTON_PRIMARY)
        send(MotionEvent.ACTION_BUTTON_PRESS, startX, MotionEvent.BUTTON_PRIMARY)
        repeat(12) { step ->
            SystemClock.sleep(16)
            send(MotionEvent.ACTION_MOVE, startX + (endX - startX) * (step + 1) / 12, MotionEvent.BUTTON_PRIMARY)
        }
        send(MotionEvent.ACTION_BUTTON_RELEASE, endX, 0)
        send(MotionEvent.ACTION_UP, endX, 0)
    }

    @Test fun mouseDragShowsFloatingMenuOnReleaseAndPreservesPartialSelection() {
        mouseDragSelection("text", 0, 10)
        val mode = awaitMode()
        screenshot("mouse-drag-release")
        assertEquals("\"Alpha beta\"", js("getSelection().toString()"))
        assertEquals("Alpha beta", copy(mode))
    }

    @Test fun mouseDragInEditableTextShowsMenuWithoutChangingTheDraft() {
        mouseDragSelection("editor", 9, 14)
        val mode = awaitMode()
        assertEquals("\"Editable draft text\"", js("document.getElementById('editor').value"))
        screenshot("mouse-drag-editable")
        assertEquals("draft", copy(mode))
    }

    @Test fun touchpadDragShowsTheSameFloatingSelectionMenu() {
        mouseDragSelection("text", 0, 10, InputDevice.SOURCE_TOUCHPAD)
        assertEquals("Alpha beta", copy(awaitMode()))
    }

    @Test fun mouseSelectionInCrossOriginFrameOpensTheNativeMenu() {
        FixtureServer(pages = mapOf("/frame" to "<!doctype html><p id='frame'>Frame selection text</p>" )).use { other ->
            mouseDragSelection("frame", 0, 15, frameUrl = other.origin + "/frame")
            val mode = awaitMode()
            screenshot("mouse-cross-origin-frame")
            assertEquals("Frame selection", copy(mode, expectedSelection = "Frame selection"))
        }
    }

    @Test fun ordinaryMouseClickWithoutSelectionDoesNotOpenAToolbar() {
        mouseDragSelection("text", 0, 0)
        SystemClock.sleep(900)
        assertEquals("\"\"", js("getSelection().toString()"))
        assertTrue(modes.isEmpty())
        assertNull(copyNode())
    }

    @Test fun hidingThePageCancelsAQueuedSelectionMenu() {
        mouseDragSelection("text", 0, 10)
        main { engine.setVisible(false) }
        SystemClock.sleep(900)
        assertTrue("A hidden page must not open a delayed toolbar", modes.isEmpty())
        assertNull(copyNode())
    }

    @Test fun navigationCancelsAQueuedSelectionMenu() {
        mouseDragSelection("text", 0, 10)
        main { engine.loadUrl("about:blank") }
        runBlocking { withTimeout(5_000) { engine.state.first { it.url == "about:blank" && !it.isLoading } } }
        SystemClock.sleep(900)
        assertTrue("The previous page must not open a delayed toolbar", modes.isEmpty())
        assertNull(copyNode())
    }
}
