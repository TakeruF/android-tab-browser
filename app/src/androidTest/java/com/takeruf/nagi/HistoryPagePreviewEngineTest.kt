package com.takeruf.nagi

import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class HistoryPagePreviewEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var server: FixtureServer
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var view: WebView
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun js(code: String): String {
        val result = CompletableDeferred<String>()
        main { view.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun ready(path: String) = runBlocking {
        withTimeout(15_000) { engine.state.first { !it.isLoading && it.url == server.origin + path && it.progress == 100 } }
        val painted = CompletableDeferred<Unit>()
        main { view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { painted.complete(Unit) }
        }) }
        withTimeout(5_000) { painted.await() }
    }
    private fun load(path: String) { main { engine.loadUrl(server.origin + path) }; ready(path) }

    @Before fun setup() {
        val header = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0}</style>"
        server = FixtureServer(pages = mapOf(
            "/old" to "$header<div style='height:1200px;background:#e65a45'></div><div style='height:4000px;background:#dcbc45'></div>",
            "/current" to "$header<body style='background:#2879b2'><textarea id='draft'>Unsent</textarea></body>",
            "/green" to "$header<body style='background:#4b9860'></body>"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, false)
            view = engine.surface as WebView
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun viewportCaptureKeepsScrollAndForwardPreviewKeepsDepartingDocument() {
        load("/old")
        js("window.scrollTo(0,1500); true")
        ready("/old")
        load("/current")
        main {
            val preview = engine.historyPagePreview(true)!!
            assertEquals(server.origin + "/old", preview.url)
            val bitmap = preview.bitmap!!
            assertTrue(bitmap.width.toLong() * bitmap.height <= 524_288)
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue("Preview must capture the scrolled yellow viewport, not its red top", Color.green(pixel) > Color.blue(pixel) * 1.5f)
            engine.goBack()
        }
        ready("/old")
        main {
            val preview = engine.historyPagePreview(false)!!
            assertEquals(server.origin + "/current", preview.url)
            val bitmap = preview.bitmap!!
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
            assertTrue("Forward entry must retain the departing blue document", Color.blue(pixel) > Color.red(pixel))
            engine.goForward()
        }
        ready("/current")
    }

    @Test fun sameDocumentHistoryDoesNotMislabelTheNextViewport() {
        load("/current")
        js("history.pushState({},'', '/current#edited'); document.body.style.background='#a93cc0'; true")
        ready("/current#edited")
        load("/green")
        main {
            val preview = engine.historyPagePreview(true)!!
            assertEquals(server.origin + "/current#edited", preview.url)
            val bitmap = preview.bitmap!!
            val pixel = bitmap.getPixel(bitmap.width / 2, bitmap.height * 3 / 4)
            assertTrue("Edited same-document entry must be purple", Color.red(pixel) > Color.green(pixel) * 2 && Color.blue(pixel) > Color.green(pixel) * 2)
            engine.destroy()
            assertNull(engine.historyPagePreview(true))
        }
    }

    @Test fun explicitBackToNewTabIsAllowedWhileStaleBlankGuardsRemain() {
        main { engine.loadUrl("about:blank") }
        runBlocking { withTimeout(10_000) { while (true) {
            var blank = false
            main { blank = view.copyBackForwardList().currentItem?.url == "about:blank" && view.progress == 100 }
            if (blank) break
            delay(10)
        } } }
        load("/current")
        main {
            assertEquals("about:blank", engine.historyPagePreview(true)?.url)
            engine.goBack()
        }
        runBlocking { withTimeout(10_000) { engine.state.first { it.url == "about:blank" && !it.isLoading } } }
        main { assertEquals("about:blank", view.url); engine.goForward() }
        ready("/current")
    }

    @Test fun nonTraversableScriptEntryDoesNotOfferAHistoryPreview() {
        load("/old")
        js("location.href='${server.origin}/current'")
        ready("/current")
        var canGoBack = true
        main { canGoBack = view.canGoBack() }
        Assume.assumeFalse("Provider permits this non-interactive script entry", canGoBack)
        main {
            assertTrue("Raw history still includes the script's source", view.copyBackForwardList().currentIndex > 0)
            assertNull("Tab closure must not show a raw, non-traversable history entry", engine.historyPagePreview(true))
        }
    }
}
