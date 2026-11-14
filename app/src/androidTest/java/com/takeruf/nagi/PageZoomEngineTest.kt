package com.takeruf.nagi

import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Actual renderer scale, not textZoom or an implementation-matching fake. */
class PageZoomEngineTest {
    private lateinit var server: FixtureServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var frame: FrameLayout
    private var initialPageScale = 1.0
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) { result(null) }
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) { result(emptySet()) }
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) { exit() }
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(predicate: () -> Boolean) {
        val callSite = AssertionError("Zoom condition did not complete")
        try { runBlocking { withTimeout(15_000) { while (!predicate()) delay(25) } } }
        catch (failure: TimeoutCancellationException) {
            callSite.addSuppressed(AssertionError(engine.state.value.toString()))
            callSite.initCause(failure)
            throw callSite
        }
    }
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { (engine.surface as WebView).evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun scale() = js("visualViewport.scale").toDouble()
    private fun traceZoom(stage: String) {
        val cssScale = scale()
        val token = js("window.documentToken")
        main {
            @Suppress("DEPRECATION")
            val native = (engine.surface as WebView).scale
            android.util.Log.i("PageZoomSnapshotTest", "$stage native=$native css=$cssScale baseline=$initialPageScale percent=${engine.state.value.pageZoomPercent} scrollY=${(engine.surface as WebView).scrollY} loading=${engine.state.value.isLoading} token=$token")
        }
    }
    private fun suspendable(): Boolean {
        val result = CompletableDeferred<Boolean>()
        main { engine.canSuspend { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun awaitDrawn() {
        val result = CompletableDeferred<Unit>()
        main { (engine.surface as WebView).postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { result.complete(Unit) }
        }); engine.surface.invalidate() }
        runBlocking { withTimeout(5_000) { result.await() } }
    }
    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/zoom-fixture" to """
            <!doctype html><title>Zoom fixture</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=0.5,maximum-scale=5">
            <style>body{margin:20px;font:18px sans-serif}#box{width:120px;height:80px;background:#244435}textarea{display:block;width:180px;height:80px}</style>
            <h1>Page zoom</h1><div id="box"></div><textarea id="draft">initial</textarea>
            <div style="height:2400px">Scroll restoration fixture</div>
            <script>window.documentToken='keep-live-document';</script>
        """.trimIndent(), "/zoom-locked" to """
            <!doctype html><title>Fixed scale</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=1,maximum-scale=1,user-scalable=no">
            <p>This fixture has a fixed native page scale.</p>
        """.trimIndent()))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            frame = FrameLayout(it)
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            frame.addView(engine.surface, ViewGroup.LayoutParams(-1, -1))
            it.addContentView(frame, ViewGroup.LayoutParams(-1, -1))
            engine.setVisible(true)
            engine.loadUrl("${server.origin}/zoom-fixture")
        }
        runBlocking { withTimeout(15_000) { engine.state.first { it.title == "Zoom fixture" && !it.isLoading } } }
        awaitDrawn()
        initialPageScale = scale()
        assertEquals(100, engine.state.value.pageZoomPercent)
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close(); server.close() }

    @Test fun controlsChangeWholePageScaleAndKeepDraftThroughNativeZoomAndReset() {
        js("document.getElementById('draft').value='Unsent 日本語';true")
        val initial = scale()
        main { engine.zoomIn() }
        waitFor { engine.state.value.pageZoomPercent > 100 }
        runBlocking { delay(250) }
        assertTrue(scale() > initial * 1.1)
        assertEquals("120", js("document.getElementById('box').getBoundingClientRect().width"))
        main { assertEquals(100, (engine.surface as WebView).settings.textZoom) }
        val enlarged = engine.state.value.pageZoomPercent
        main { (engine.surface as WebView).zoomBy(1.2f) } // Same native scale path as pinch.
        waitFor { engine.state.value.pageZoomPercent > enlarged }
        main { engine.zoomOut() }
        waitFor { engine.state.value.pageZoomPercent < enlarged + 20 }
        main { engine.resetZoom() }
        waitFor { engine.state.value.pageZoomPercent in 99..101 && kotlin.math.abs(scale() - initial) < 0.02 }
        assertEquals(initial, scale(), 0.02)
        assertEquals("\"keep-live-document\"", js("window.documentToken"))
        assertEquals("\"Unsent 日本語\"", js("document.getElementById('draft').value"))
        assertEquals(1, server.requests.count { it.path == "/zoom-fixture" })
        main { engine.loadUrl("${server.origin}/zoom-locked") }
        runBlocking { withTimeout(5_000) { engine.state.first { it.title == "Fixed scale" && !it.isLoading } } }
        awaitDrawn()
        assertFalse(engine.state.value.canZoomIn)
        assertFalse(engine.state.value.canZoomOut)
        main { engine.zoomIn(); engine.zoomOut(); engine.resetZoom() }
        val eligible = CompletableDeferred<Boolean>()
        main { engine.canSuspend { eligible.complete(it) } }
        assertTrue(runBlocking { withTimeout(5_000) { eligible.await() } })
        assertEquals(1.0, scale(), 0.02)
    }

    @Test fun rapidZoomThenResetUsesTheResultingNativeScaleAndProtectsPendingWork() = runBlocking {
        val initial = scale()
        val enlarged = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { engine.state.first { it.pageZoomPercent > 100 } } }
        var safeToSuspend = true
        main {
            engine.zoomIn(); engine.zoomOut(); engine.zoomIn(); engine.resetZoom()
            engine.canSuspend { safeToSuspend = it }
        }
        assertFalse(safeToSuspend)
        enlarged.await()
        waitFor { engine.state.value.pageZoomPercent in 99..101 }
        delay(250)
        assertEquals(initial, scale(), 0.02)
        repeat(3) { main { engine.zoomIn(); engine.resetZoom() }; delay(350) }
        waitFor { engine.state.value.pageZoomPercent in 99..101 }
        assertEquals(initial, scale(), 0.02)
    }

    @Test fun liveVisibilityThemeAndSnapshotKeepTheZoomBaseline() {
        main { engine.zoomIn() }
        waitFor { engine.state.value.pageZoomPercent > 100 }
        waitFor(::suspendable)
        val enlarged = scale()
        main { engine.setVisible(false); engine.setDarkTheme(true); engine.setVisible(true) }
        awaitDrawn()
        assertEquals(enlarged, scale(), 0.02)
        assertTrue(engine.state.value.pageZoomPercent > 100)
        waitFor(::suspendable)
        main { (engine.surface as WebView).scrollTo(0, 600) }
        awaitDrawn()
        main { assertEquals(600, (engine.surface as WebView).scrollY) }
        var snapshot: EngineSnapshot? = null
        traceZoom("before-save")
        main { snapshot = engine.saveState() }
        scenario.onActivity {
            engine.destroy()
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            frame.addView(engine.surface, ViewGroup.LayoutParams(-1, -1))
            assertTrue(engine.restoreState(snapshot!!))
            engine.setVisible(true)
        }
        traceZoom("after-restore-call")
        waitFor { js("document.readyState") == "\"complete\"" }
        traceZoom("ready-complete")
        awaitDrawn()
        traceZoom("first-drawn-frame")
        waitFor { engine.state.value.pageZoomPercent > 100 }
        waitFor(::suspendable)
        traceZoom("restoration-complete")
        assertEquals(enlarged, scale(), 0.03)
        main { assertEquals(600, (engine.surface as WebView).scrollY) }
        main { engine.resetZoom() }
        waitFor { engine.state.value.pageZoomPercent in 99..101 && kotlin.math.abs(scale() - initialPageScale) < 0.02 }
        assertEquals(1.0, scale(), 0.03)
    }
}
