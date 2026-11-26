package com.takeruf.nagi

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*

/** Opt-in live-provider evidence, separate from the repeatable SPA fixture tests. */
class YouTubeLiveHistoryTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
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
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun js(code: String): String {
        val result = CompletableDeferred<String>()
        main { view.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    @Before fun setup() {
        Assume.assumeTrue("Explicit live-site opt-in required", InstrumentationRegistry.getArguments().getString("nagiLiveYouTube") == "true")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, true)
            view = engine.surface as WebView
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
    }
    @Test fun desktopSearchToVideoRecordsTheVisitAndBothPreviewImages() = runBlocking {
        val visits = java.util.concurrent.CopyOnWriteArrayList<PageState>()
        val collector = launch(Dispatchers.Main.immediate) { engine.events.collect { if (it is EngineEvent.Visited) visits.add(it.page) } }
        val evidence = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "youtube-history-live.txt")
        try {
            main { engine.loadUrl("https://www.youtube.com/results?search_query=Android+Developers") }
            withTimeout(45_000) { engine.state.first { it.error != null || (!it.isLoading && it.progress == 100 && it.url.contains("/results")) } }
            assertNull(engine.state.value.error)
            withTimeout(30_000) { while (js("!!document.querySelector('a[href*=\"/watch?v=\"]')") != "true") delay(200) }
            val searchTitle = org.json.JSONTokener(js("document.title")).nextValue() as String
            js("document.querySelector('a[href*=\"/watch?v=\"]').scrollIntoView({block:'center'});true")
            delay(500)
            val point = JSONArray(js("(()=>{const r=document.querySelector('a[href*=\"/watch?v=\"]').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})()"))
            main {
                val x = (point.getDouble(0) * view.width / point.getDouble(2)).toFloat()
                val y = (point.getDouble(1) * view.width / point.getDouble(2)).toFloat()
                val time = SystemClock.uptimeMillis()
                for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0L, MotionEvent.ACTION_UP to 60L)) {
                    val event = MotionEvent.obtain(time, time + offset, action, x, y, 0)
                    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
                }
            }
            withTimeout(30_000) { engine.state.first { it.url.contains("/watch?v=") && !it.isLoading } }
            withTimeout(10_000) { while (visits.none { it.url.contains("/watch?v=") }) delay(100) }
            withTimeout(25_000) { while (visits.none { it.url.contains("/watch?v=") && it.title != searchTitle }) delay(200) }
            var backImage = false
            main { backImage = engine.historyPagePreview(true)?.bitmap != null; engine.goBack() }
            withTimeout(30_000) { engine.state.first { it.url.contains("/results") && !it.isLoading } }
            var forwardImage = false
            main { forwardImage = engine.historyPagePreview(false)?.bitmap != null }
            evidence.writeText("provider=${js("navigator.userAgent")}\nvisits=${visits.map { it.url to it.title }}\nbackImage=$backImage\nforwardImage=$forwardImage\n")
            assertTrue("Search viewport was saved before SPA video navigation", backImage)
            assertTrue("Video viewport was saved before Back", forwardImage)
        } catch (failure: Throwable) {
            evidence.writeText("url=${engine.state.value.url}\nerror=${engine.state.value.error}\ntitle=${engine.state.value.title}\nvisits=${visits.map { it.url to it.title }}\nfailure=$failure\n")
            throw failure
        } finally { collector.cancelAndJoin() }
    }
}
