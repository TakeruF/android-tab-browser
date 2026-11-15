package com.takeruf.nagi

import android.graphics.Color
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Native pixels, rather than computed CSS, establish WebView's algorithmic darkening. */
class PageDarkeningEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: BrowserEngine
    private lateinit var server: FixtureServer
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    @Before fun setup() {
        Assume.assumeTrue(PageDarkening.supported)
        val light = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;background:#fff;color:#111}body{min-height:2500px}
            input{display:block;margin:24px}#photo{position:fixed;right:0;top:0;width:80px;height:80px}</style>
            <input id="draft"><a id="next" href="#">Next page</a><p>A light page without a dark theme.</p>
            <script>window.documentToken=String(Math.random());document.title='Light ready'</script>
        """.trimIndent()
        server = FixtureServer(pages = mapOf(
            "/light" to light,
            "/other" to light,
            "/native-dark" to """<!doctype html><meta name="color-scheme" content="light dark">
                <style>html,body{margin:0;background:#fff;color:#111}@media(prefers-color-scheme:dark){html,body{background:#102030;color:#eee}}</style><p>Native dark theme</p>""",
            "/opt-out" to """<!doctype html><meta name="color-scheme" content="only light">
                <style>html,body{margin:0;background:#fff;color:#111}</style><p>Author opts out</p>"""
        ), redirects = mapOf("/redirect" to "/other"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, false,
                siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true))
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) server.close()
    }
    private fun load(url: String) {
        main { engine.loadUrl(url) }
        awaitPage("load $url") { it.url == url && !it.isLoading }
    }
    private fun awaitPage(stage: String, predicate: (PageState) -> Boolean) {
        try { runBlocking { withTimeout(15_000) { engine.state.first(predicate) } } }
        catch (error: TimeoutCancellationException) {
            throw AssertionError("$stage: ${engine.state.value}; requests=${server.requests.map { it.path }}; location=${js("location.href")}", error)
        }
    }
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { ((engine as AndroidEngineSurface).surface as android.webkit.WebView).evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun pixel(): Int {
        val point = IntArray(2)
        main {
            val view = (engine as AndroidEngineSurface).surface
            view.getLocationOnScreen(point)
            point[0] += view.width * 3 / 4; point[1] += view.height / 2
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        return try { bitmap.getPixel(point[0], point[1]) } finally { bitmap.recycle() }
    }
    private fun awaitPixel(label: String, matches: (Int) -> Boolean) {
        var actual = pixel()
        try {
            runBlocking { withTimeout(7_000) { while (!matches(actual)) { delay(60); actual = pixel() } } }
        } catch (error: TimeoutCancellationException) {
            throw AssertionError("$label: pixel ${Integer.toHexString(actual)}", error)
        }
    }
    private fun white() = awaitPixel("Light canvas") { it == Color.WHITE }
    private fun dark() = awaitPixel("Darkened canvas") { Color.red(it) < 100 && Color.green(it) < 100 && Color.blue(it) < 100 }

    @Test fun optInOnlyDarkensDarkModeAndSiteSwitchPreservesLiveDocument() {
        main { engine.setDarkTheme(true) }
        load("${server.origin}/light")
        white() // Missing preference remains off, including after a theme change.
        val token = js("document.getElementById('draft').value='unfinished 日本語';window.scrollTo(0,250);documentToken")
        main { engine.configurePageDarkening(true, emptySet()) }
        dark()
        main { engine.configurePageDarkening(true, setOf("127.0.0.1")) }
        white()
        assertEquals(token, js("documentToken"))
        assertEquals("\"unfinished 日本語\"", js("document.getElementById('draft').value"))
        assertEquals("250", js("Math.round(scrollY)"))
        main { engine.configurePageDarkening(true, emptySet()) }
        dark()
        main { engine.setDarkTheme(false) }
        white()
        main { engine.setDarkTheme(true) }
        dark()
        main { engine.configurePageDarkening(false, emptySet()) }
        white()
        assertEquals(token, js("documentToken"))
        assertEquals(1, server.requests.count { it.path == "/light" })
    }

    @Test fun siteExceptionsFollowNavigationRedirectAndHistoryByHost() {
        main { engine.setDarkTheme(true); engine.configurePageDarkening(true, setOf("localhost")) }
        load("${server.origin}/light"); dark()
        val excluded = server.origin.replace("127.0.0.1", "localhost") + "/other"
        // A page-initiated link must use the destination's policy rather than the opener's.
        js("document.getElementById('next').href='${excluded.replace("/other", "/redirect")}';true")
        val rect = org.json.JSONArray(js("JSON.stringify((()=>{const r=document.getElementById('next').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})())").let { org.json.JSONTokener(it).nextValue() as String })
        main {
            val view = (engine as AndroidEngineSurface).surface
            val scale = view.width / rect.getDouble(2)
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN,
                (rect.getDouble(0)*scale).toFloat(), (rect.getDouble(1)*scale).toFloat(), 0)
            val up = android.view.MotionEvent.obtain(now, now+60, android.view.MotionEvent.ACTION_UP,
                (rect.getDouble(0)*scale).toFloat(), (rect.getDouble(1)*scale).toFloat(), 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
        awaitPage("excluded navigation") { it.url == excluded && !it.isLoading }
        white()
        main { engine.goBack() }
        awaitPage("history back") { it.url.endsWith("/light") && !it.isLoading }
        dark()
        main { engine.goForward() }
        awaitPage("history forward") { it.url == excluded && !it.isLoading }
        white()
        load("${server.origin}/other"); dark()
    }

    @Test fun authoredDarkThemeAndAuthorOptOutAreRespected() {
        main { engine.setDarkTheme(true); engine.configurePageDarkening(true, emptySet()) }
        load("${server.origin}/native-dark")
        awaitPixel("Authored dark color") { it == Color.rgb(16,32,48) }
        main { engine.configurePageDarkening(true, setOf("127.0.0.1")) }
        // A site exception disables Nagi's adaptation, preserving the site's native theme.
        awaitPixel("Authored dark color after exclusion") { it == Color.rgb(16,32,48) }
        main { engine.configurePageDarkening(true, emptySet()) }
        load("${server.origin}/opt-out"); white()
    }
}
