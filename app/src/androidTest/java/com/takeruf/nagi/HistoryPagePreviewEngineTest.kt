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
        try {
            withTimeout(15_000) { engine.state.first { !it.isLoading && it.url == server.origin + path && it.progress == 100 } }
        } catch (failure: Throwable) {
            var native = ""
            main { native = "url=${view.url} history=${view.copyBackForwardList().currentIndex}/${view.copyBackForwardList().size}" }
            throw AssertionError("Expected $path; ${engine.state.value}; $native; dom=${js("JSON.stringify({events:window.inputEvents,error:window.scriptError,button:document.querySelector('button')?.outerHTML,width:innerWidth,visual:visualViewport.width})")}", failure)
        }
        val painted = CompletableDeferred<Unit>()
        main { view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { painted.complete(Unit) }
        }) }
        withTimeout(5_000) { painted.await() }
        delay(100) // Let the compositor present the hit-test data, not only acknowledge DOM state.
    }
    private fun load(path: String) { main { engine.loadUrl(server.origin + path) }; ready(path) }
    private fun tapNext() {
        val point = org.json.JSONArray(js("(()=>{const r=document.querySelector('button').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})()"))
        main {
            val x = (point.getDouble(0) * view.width / point.getDouble(2)).toFloat()
            val y = (point.getDouble(1) * view.width / point.getDouble(2)).toFloat()
            val time = android.os.SystemClock.uptimeMillis()
            for ((action, offset) in listOf(android.view.MotionEvent.ACTION_DOWN to 0L, android.view.MotionEvent.ACTION_UP to 60L)) {
                val event = android.view.MotionEvent.obtain(time, time + offset, action, x, y, 0)
                try { view.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
    }

    @Before fun setup() {
        val header = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0}</style>"
        server = FixtureServer(pages = mapOf(
            "/old" to "$header<div style='height:1200px;background:#e65a45'></div><div style='height:4000px;background:#dcbc45'></div>",
            "/current" to "$header<body style='background:#2879b2'><textarea id='draft'>Unsent</textarea></body>",
            "/green" to "$header<body style='background:#4b9860'></body>",
            "/details" to """$header<body style="min-height:100vh;background:repeating-linear-gradient(90deg,black 0,black .5px,white .5px,white 1px)"></body>""",
            "/spa" to """$header<body><button style="margin:64px;padding:16px" onclick="nextVideo()">Next video</button>
                <textarea id='draft'>Unsent</textarea><script>
                function nextVideo(){history.pushState({},'', '/spa?v='+(Number(new URL(location.href).searchParams.get('v')||0)+1));render()}
                function render(){const n=Number(new URL(location.href).searchParams.get('v')||0);
                  document.body.style.background=['#e65a45','#2879b2','#4b9860','#a93cc0'][n];document.title='Video '+n}
                window.inputEvents=[];for(const type of ['touchstart','touchend','click'])addEventListener(type,e=>inputEvents.push(type+':'+e.target.tagName));
                addEventListener('error',e=>window.scriptError=e.message);addEventListener('popstate',render);render();</script></body>"""))
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
            val cache = HistoryPreviewImageCache.shared(view.context)
            assertTrue(bitmap.width.toLong() * bitmap.height <= cache.maxImagePixels)
            assertEquals(cache.dimensions(view.width, view.height), bitmap.width to bitmap.height)
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

    @Test fun nativeResolutionPreviewKeepsSinglePixelDetailsForBackAndForward() {
        load("/old")
        var expected = 0 to 0
        main {
            val bounded = HistoryPreviewImageCache.shared(view.context).dimensions(view.width, view.height)
            // A real viewport inside the byte budget, aligned to the QA device's native pixels.
            expected = (bounded.first / 4 * 4) to (bounded.second / 4 * 4)
            view.layoutParams = view.layoutParams.apply { width = expected.first; height = expected.second }
        }
        runBlocking { withTimeout(5_000) { while (true) {
            var laidOut = false
            main { laidOut = view.width == expected.first && view.height == expected.second }
            if (laidOut) break
            delay(20)
        } } }
        load("/details")
        val surfaceWidth = expected.first
        js("document.body.style.backgroundImage='repeating-linear-gradient(90deg,black 0,black '+(innerWidth/$surfaceWidth)+'px,white '+(innerWidth/$surfaceWidth)+'px,white '+(innerWidth/$surfaceWidth*2)+'px)'; true")
        ready("/details")
        val sourceScreenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val origin = IntArray(2)
        main { view.getLocationOnScreen(origin) }
        val sourceContrast = (expected.first / 4 until expected.first / 4 + 200).map { x ->
            kotlin.math.abs(Color.red(sourceScreenshot.getPixel(origin[0] + x, origin[1] + expected.second * 3 / 4)) -
                Color.red(sourceScreenshot.getPixel(origin[0] + x + 1, origin[1] + expected.second * 3 / 4)))
        }.average()
        sourceScreenshot.recycle()
        assertTrue("The fixture itself must render one native pixel per stripe; contrast=$sourceContrast", sourceContrast > 170)
        fun check(preview: HistoryPagePreview) {
            val image = preview.bitmap ?: error("Missing detail preview")
            assertEquals(expected, image.width to image.height)
            val y = image.height * 3 / 4
            val start = image.width / 4
            val contrast = (start until start + 200).map { x ->
                kotlin.math.abs(Color.red(image.getPixel(x, y)) - Color.red(image.getPixel(x + 1, y)))
            }.average()
            val directory = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "preview-quality").apply { mkdirs() }
            val stats = org.json.JSONObject().put("width", image.width).put("height", image.height)
                .put("contrast", contrast).put("sourceContrast", sourceContrast).put("sampleColors", org.json.JSONArray((start until start + 16).map { image.getPixel(it, y) }))
            java.io.File(directory, "detail-stats.json").writeText(stats.toString(2))
            android.util.Log.i("NagiPreviewQuality", stats.toString())
            java.io.File(directory, "detail-preview.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("Single-pixel detail must survive capture; contrast=$contrast colors=${stats.getJSONArray("sampleColors")}", contrast > 170)
        }
        load("/current")
        main { check(engine.historyPagePreview(true)!!); engine.goBack() }
        ready("/details")
        main { engine.goBack() }
        ready("/old")
        main { check(engine.historyPagePreview(false)!!); engine.goForward() }
        ready("/details")
        main { check(engine.currentPagePreview()!!); engine.goForward() }
        ready("/current")
        main { check(engine.historyPagePreview(true)!!) }
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

    @Test fun spaVisitsAndThreeBackForwardPreviewsRetainEveryDestination() = runBlocking {
        val visits = java.util.concurrent.CopyOnWriteArrayList<PageState>()
        val collector = launch(Dispatchers.Main.immediate) { engine.events.collect {
            if (it is EngineEvent.Visited) visits.add(it.page)
        } }
        try {
            load("/spa")
            for (index in 1..3) { tapNext(); ready("/spa?v=$index") }
            withTimeout(5_000) { while (visits.none { it.url == server.origin + "/spa?v=3" && it.title == "Video 3" }) delay(10) }
            assertTrue((0..3).all { index -> visits.any { it.url == server.origin + if (index == 0) "/spa" else "/spa?v=$index" } })
            fun checkPreview(back: Boolean, index: Int) {
                val path = if (index == 0) "/spa" else "/spa?v=$index"
                main {
                    val preview = engine.historyPagePreview(back) ?: error("Missing destination $path back=$back")
                    assertEquals(server.origin + path, preview.url)
                    val image = preview.bitmap ?: error("Missing saved image $path back=$back")
                    val pixel = image.getPixel(image.width / 2, image.height * 3 / 4)
                    val expected = intArrayOf(Color.rgb(230,90,69), Color.rgb(40,121,178), Color.rgb(75,152,96), Color.rgb(169,60,192))[index]
                    assertTrue("Wrong saved viewport for $path: ${Integer.toHexString(pixel)}", kotlin.math.abs(Color.red(pixel)-Color.red(expected)) < 12 &&
                        kotlin.math.abs(Color.green(pixel)-Color.green(expected)) < 12 && kotlin.math.abs(Color.blue(pixel)-Color.blue(expected)) < 12)
                    if (back) engine.goBack() else engine.goForward()
                }
                ready(path)
            }
            for (index in 2 downTo 0) checkPreview(true, index)
            for (index in 1..3) checkPreview(false, index)
            assertEquals("\"Unsent\"", js("document.getElementById('draft').value"))
        } finally { collector.cancelAndJoin() }
    }
}
