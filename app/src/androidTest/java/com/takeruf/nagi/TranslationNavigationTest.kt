package com.takeruf.nagi

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TranslationNavigationTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var server: FixtureServer
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var modes: SiteDisplayModeStore
    private val engines = mutableListOf<WebViewBrowserEngine>()
    private var rules = emptyMap<String, String>()
    private var linksInNewTab = false
    private var realModels = false
    private var prepareGate: CompletableDeferred<Unit>? = null
    private val languages = CopyOnWriteArrayList<String>()
    private val preparations = CopyOnWriteArrayList<Triple<String, String, Boolean>>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun page(title: String) = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>$title</title></head>
        <body><h1 id="heading">$title</h1><p>This page has enough readable text to translate.</p>
        <p><a id="next" href="/two">Next page</a></p><p><a id="third" href="/three">Third page</a></p>
        <p><a id="foreign" href="/fr">French page</a></p><p><a id="redirect" href="/redirect">Redirected page</a></p>
        <p><a id="new-tab" target="_blank" href="/two">Open another tab</a></p>
        <p><a id="unknown" href="/unknown">Unknown language</a></p></body></html>
    """.trimIndent()

    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/one" to page("English first"), "/two" to page("English second"),
            "/three" to page("English third"), "/fr" to page("Français nouvelle page"), "/unknown" to page("undetected")),
            redirects = mapOf("/redirect" to "/two"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            activity = it
            modes = SiteDisplayModeStore(it, inMemory = true)
            engine = createEngine()
        }
        load("/one")
    }
    @After fun cleanup() { main { engines.forEach { it.destroy() } }; scenario.close(); server.close() }
    private fun createEngine(): WebViewBrowserEngine = WebViewBrowserEngine(activity, host, host, { linksInNewTab }, false,
        siteDisplayModes = modes, alwaysTranslateRules = { rules },
        rememberAlwaysTranslate = { source, target -> rules = rules + (source to target) },
        translationFactory = { if (realModels) MlKitTextTranslationBackend() else object : TextTranslationBackend {
            private var target = ""
            override suspend fun identify(text: String): String? {
                val language = when { "Français" in text -> "fr"; "undetected" in text -> "und"; else -> "en" }
                languages.add(language)
                return language.takeUnless { it == "und" }
            }
            override suspend fun prepare(source: String, target: String, wifiOnly: Boolean) {
                this.target = target; preparations.add(Triple(source, target, wifiOnly)); prepareGate?.await()
            }
            override suspend fun translate(text: String) = "$target:$text"
            override fun close() {}
        } }).also {
            engines.add(it)
            it.surface.id = View.generateViewId()
            activity.addContentView(it.surface, android.view.ViewGroup.LayoutParams(-1, -1))
        }
    private fun awaitState(target: WebViewBrowserEngine = engine, timeout: Long = 20_000, condition: (PageState) -> Boolean): PageState {
        val callSite = AssertionError("Translation navigation did not reach its expected state")
        return try { runBlocking { withTimeout(timeout) { target.state.first(condition) } } }
        catch (failure: TimeoutCancellationException) {
            callSite.addSuppressed(AssertionError("Actual fixture state: ${target.state.value}"))
            callSite.initCause(failure)
            throw callSite
        }
    }
    private fun load(path: String, target: WebViewBrowserEngine = engine) {
        main { target.loadUrl(server.origin + path) }
        awaitState(target) { it.url == server.origin + path && !it.isLoading }
    }
    private fun translated(path: String, target: WebViewBrowserEngine = engine) = awaitState(target, if (realModels) 200_000 else 20_000) {
        it.url == server.origin + path && it.translation.stage == TranslationStage.TRANSLATED
    }
    private fun js(source: String, target: WebViewBrowserEngine = engine): String {
        val result = LinkedBlockingQueue<String>()
        main { target.surface.evaluateJavascript(source) { result.offer(it) } }
        return checkNotNull(result.poll(10, TimeUnit.SECONDS))
    }
    /** Real touch input is required: a JavaScript .click() has no WebView user gesture. */
    private fun click(id: String) {
        js("document.getElementById('$id').scrollIntoView({block:'center',inline:'center'}); true")
        // Translation/restoration can change layout before WebView's compositor has
        // committed it. Inject the touch only after the new DOM is ready for drawing.
        val ready = LinkedBlockingQueue<Boolean>()
        main {
            engine.surface.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.offer(true) }
            })
            engine.surface.invalidate()
        }
        assertEquals("Translated link layout was not committed", true, ready.poll(10, TimeUnit.SECONDS))
        val rect = JSONObject(js("""(() => { const r = document.getElementById('$id').getBoundingClientRect();
            return {x:r.x+r.width/2, y:r.y+r.height/2, width:innerWidth}; })()"""))
        main {
            val scale = engine.surface.width / rect.getDouble("width")
            val x = (rect.getDouble("x") * scale).toFloat()
            val y = (rect.getDouble("y") * scale).toFloat()
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0)
            engine.surface.dispatchTouchEvent(down); engine.surface.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
    }
    private fun manual(always: Boolean = false) {
        main { engine.translatePage(null, "ja", wifiOnly = false, alwaysTranslate = always) }
        translated("/one")
    }
    private fun rejected(language: String) {
        runBlocking { withTimeout(15_000) {
            while (language !in languages || js("window.__nagiTranslation == null") != "true") delay(50)
        } }
        assertFalse(engine.state.value.translation.active)
    }

    @Test fun linksContinueThroughTheSameLanguageAndStopAfterChangingLanguage() {
        manual()
        click("next")
        assertEquals("en", translated("/two").translation.source)
        assertTrue(js("document.getElementById('heading').textContent").contains("ja:English second"))
        assertEquals(Triple("en", "ja", false), preparations.last())
        click("foreign")
        awaitState { it.url.endsWith("/fr") && !it.isLoading }
        rejected("fr")
        click("third")
        awaitState { it.url.endsWith("/three") && !it.isLoading }
        assertFalse(engine.state.value.translation.active)
        assertEquals("\"English third\"", js("document.getElementById('heading').textContent"))
    }

    @Test fun redirectsAndPerSiteUserAgentReloadsKeepLinkContinuation() {
        manual()
        main { modes.remember(server.origin, true) }
        click("redirect")
        translated("/two")
        assertTrue(engine.state.value.desktopMode)
        assertTrue(server.requests.any { it.path == "/redirect" })
    }

    @Test fun reloadContinuesButDirectEntryAndShowOriginalEndTheManualChain() {
        manual()
        val count = preparations.size
        main { engine.reload() }
        runBlocking { withTimeout(15_000) { while (preparations.size <= count) delay(50) } }
        translated("/one")
        main { engine.restoreOriginalPage() }
        click("next")
        awaitState { it.url.endsWith("/two") && !it.isLoading }
        assertFalse(engine.state.value.translation.active)
        load("/one"); manual()
        load("/three")
        assertFalse(engine.state.value.translation.active)
        click("next")
        awaitState { it.url.endsWith("/two") && !it.isLoading }
        assertFalse(engine.state.value.translation.active)
    }

    @Test fun newTabsDoNotInheritManualTranslationButAlwaysRulesApplyAcrossTabsAndDirectEntry() {
        manual()
        val opened = runBlocking {
            val event = async(Dispatchers.Default) {
                withTimeout(15_000) { engine.events.first { it is EngineEvent.OpenTab } as EngineEvent.OpenTab }
            }
            click("new-tab")
            event.await()
        }
        assertEquals(server.origin + "/two", opened.url)
        assertEquals(TranslationStage.TRANSLATED, engine.state.value.translation.stage)
        lateinit var child: WebViewBrowserEngine
        main { child = createEngine() }
        load("/two", child)
        assertFalse(child.state.value.translation.active)
        main { child.destroy(); engines.remove(child) }
        main { engine.restoreOriginalPage() }
        manual(always = true)
        assertEquals(mapOf("en" to "ja"), rules)
        main { child = createEngine() }
        load("/two", child)
        translated("/two", child)
        main { child.destroy(); engines.remove(child) }
        load("/three")
        translated("/three")
        main { engine.restoreOriginalPage(); rules = emptyMap() }
        load("/one")
        assertFalse(engine.state.value.translation.active)
    }

    @Test fun restoringDuringAutomaticPreparationCancelsLateResultsAndContinuation() {
        manual()
        prepareGate = CompletableDeferred()
        click("next")
        awaitState { it.url.endsWith("/two") && it.translation.stage == TranslationStage.DOWNLOADING }
        main { engine.restoreOriginalPage() }
        prepareGate!!.complete(Unit)
        assertEquals("\"English second\"", js("document.getElementById('heading').textContent"))
        assertFalse(engine.state.value.translation.active)
        click("third")
        awaitState { it.url.endsWith("/three") && !it.isLoading }
        assertFalse(engine.state.value.translation.active)
    }

    @Test fun anUnknownSourceLanguageStopsContinuationWithoutAnErrorBanner() {
        manual()
        click("unknown")
        awaitState { it.url.endsWith("/unknown") && !it.isLoading }
        rejected("und")
        assertEquals(TranslationStage.IDLE, engine.state.value.translation.stage)
    }

    @Test fun realModelContinuesOnALinkedEnglishPage() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("translationModelSmoke") == "true")
        realModels = true
        manual()
        click("next")
        assertEquals("en", translated("/two").translation.source)
        val heading = js("document.getElementById('heading').textContent")
        assertTrue(heading, heading.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' })
    }
}
