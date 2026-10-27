package com.takeruf.nagi

import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.translation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PageTranslationEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var web: WebView
    private lateinit var server: FixtureServer
    private var factory: () -> TextTranslationBackend = { FakeTranslator() }
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
    private fun awaitState(predicate: (PageState) -> Boolean) = runBlocking {
        withTimeout(20_000) { engine.state.first(predicate) }
    }
    private fun js(source: String): String {
        val queue = LinkedBlockingQueue<String>()
        main { web.evaluateJavascript(source) { queue.offer(it) } }
        return checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "WebView callback timed out" }
    }
    private fun text(id: String) = JSONArray("[${js("document.getElementById('$id').textContent")}]").getString(0)
    private fun awaitJs(source: String) = runBlocking {
        withTimeout(15_000) { while (js(source) != "true") delay(100) }
    }

    @Before fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, false, translationFactory = { factory() })
            web = engine.surface
            web.id = View.generateViewId()
            activity.addContentView(web, android.view.ViewGroup.LayoutParams(-1, -1))
        }
        load()
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close(); if (::server.isInitialized) server.close() }
    private fun load(title: String = "Translation fixture") {
        val html = """
                <!doctype html><html lang="en"><head><title>$title</title></head><body>
                <h1 id="heading"> Hello world </h1><p id="paragraph">This is a useful page.</p>
                <p id="escape">Quotes &quot; &amp; &lt;script&gt; and 日本語</p>
                <a id="link" href="/other" onclick="window.clicked=true;return false">Read more</a>
                <form><input id="input" value="Keep this value"><textarea id="textarea">Keep draft</textarea>
                <button id="submit">Send message</button></form>
                <div contenteditable="true"><span id="editable">Keep edited text</span></div>
                <p translate="no" id="no">Do not translate</p><code id="code">Code stays intact</code>
                <div style="display:none"><span id="hidden">Hidden text</span></div>
                <script>window.secret = 'script unchanged';</script>
                </body></html>
            """.trimIndent()
        if (!::server.isInitialized) server = FixtureServer(pages = mapOf(
            "/translation" to html, "/translation-next" to html.replace("Translation fixture", "New document")))
        main { engine.loadUrl(server.origin + if (title == "Translation fixture") "/translation" else "/translation-next") }
        try { awaitState { !it.isLoading && it.title == title } }
        catch (error: Exception) { throw AssertionError("Fixture did not load: ${engine.state.value}", error) }
    }
    private fun translate() { main { engine.translatePage(null, "ja", true) }; awaitState { it.translation.stage == TranslationStage.TRANSLATED } }

    @Test fun translatesDynamicTextAndRestoresWithoutChangingFormsLinksOrMarkup() {
        translate()
        assertEquals(" 訳:Hello world ", text("heading"))
        assertEquals("\"ja\"", js("document.documentElement.lang"))
        assertEquals("訳:Quotes \" & <script> and 日本語", text("escape"))
        assertEquals("Keep draft", text("textarea"))
        for (id in listOf("editable", "no", "code", "hidden")) assertFalse(text(id).startsWith("訳:"))
        assertEquals("\"Keep this value\"", js("document.getElementById('input').value"))
        assertEquals("\"/other\"", js("document.getElementById('link').getAttribute('href')"))
        assertEquals("true", js("document.getElementById('link').click(); window.clicked === true"))
        js("const p=document.createElement('p');p.id='dynamic';p.textContent='More content';document.body.appendChild(p)")
        awaitJs("document.getElementById('dynamic').textContent === '訳:More content'")
        js("document.getElementById('paragraph').textContent='Updated by website'")
        awaitJs("document.getElementById('paragraph').textContent === '訳:Updated by website'")
        main { engine.restoreOriginalPage() }
        awaitState { !it.translation.active }
        awaitJs("document.getElementById('heading').textContent === ' Hello world '")
        assertEquals("Updated by website", text("paragraph"))
        assertEquals("More content", text("dynamic"))
        assertEquals("\"script unchanged\"", js("window.secret"))
        assertEquals("null", js("window.__nagiTranslation || null"))
        assertEquals("\"en\"", js("document.documentElement.lang"))
    }

    @Test fun cancelsPendingResultsOnRestoreAndNavigationAndKeepsTabsIndependent() {
        val pending = CompletableDeferred<Unit>()
        var closed = false
        factory = { object : FakeTranslator() {
            override suspend fun translate(text: String): String { pending.await(); return super.translate(text) }
            override fun close() { closed = true }
        } }
        main { engine.translatePage("en", "ja") }
        awaitState { it.translation.stage == TranslationStage.TRANSLATING }
        main { engine.restoreOriginalPage() }
        pending.complete(Unit)
        awaitState { !it.translation.active }
        assertEquals(" Hello world ", text("heading"))
        assertTrue(closed)
        val waitForModel = CompletableDeferred<Unit>()
        factory = { object : FakeTranslator() {
            override suspend fun prepare(source: String, target: String, wifiOnly: Boolean) { waitForModel.await() }
        } }
        main { engine.translatePage("en", "ja") }
        awaitState { it.translation.stage == TranslationStage.DOWNLOADING }
        load("New document")
        waitForModel.complete(Unit)
        assertFalse(engine.state.value.translation.active)
        assertEquals(" Hello world ", text("heading"))
        factory = { FakeTranslator() }
        translate()
        var other: WebViewBrowserEngine? = null
        main { other = WebViewBrowserEngine(web.context, host, host, { false }, false) }
        try { assertFalse(other!!.state.value.translation.active) } finally { main { other!!.destroy() } }
    }

    @Test fun reportsDetectionAndModelFailuresAndCanRetry() {
        factory = { object : FakeTranslator() { override suspend fun identify(text: String): String? = null } }
        main { engine.translatePage(null, "ja") }
        assertEquals(TranslationError.LANGUAGE, awaitState { it.translation.stage == TranslationStage.ERROR }.translation.error)
        factory = { object : FakeTranslator() {
            override suspend fun prepare(source: String, target: String, wifiOnly: Boolean) { error("offline") }
        } }
        main { engine.translatePage("en", "ja") }
        assertEquals(TranslationError.MODEL, awaitState { it.translation.stage == TranslationStage.ERROR }.translation.error)
        factory = { FakeTranslator() }
        translate()
        assertTrue(text("heading").contains("訳:"))
    }

    @Test fun reportsSameLanguageAndPagesWithoutTranslatableText() {
        main { engine.translatePage("en", "en") }
        assertEquals(TranslationError.SAME_LANGUAGE, awaitState { it.translation.stage == TranslationStage.ERROR }.translation.error)
        main { engine.restoreOriginalPage() }
        js("document.body.innerHTML='<input value=hello><code>Code only</code>'")
        main { engine.translatePage("en", "ja") }
        assertEquals(TranslationError.NO_TEXT, awaitState { it.translation.stage == TranslationStage.ERROR }.translation.error)
    }

    @Test fun neverOverwritesANodeChangedDuringTranslation() {
        val pending = CompletableDeferred<Unit>()
        factory = { object : FakeTranslator() {
            override suspend fun translate(text: String): String { pending.await(); return super.translate(text) }
        } }
        main { engine.translatePage("en", "ja") }
        awaitState { it.translation.stage == TranslationStage.TRANSLATING }
        js("document.getElementById('heading').textContent='New live heading'")
        pending.complete(Unit)
        awaitJs("document.getElementById('heading').textContent === '訳:New live heading'")
        js("document.getElementById('heading').textContent='Latest website value'")
        main { engine.restoreOriginalPage() }
        assertEquals("Latest website value", text("heading"))
    }

    /** Explicit opt-in: requires network for the initial Google language-model download. */
    @Test fun realModelTranslatesPageOnDevice() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("translationModelSmoke") == "true")
        factory = { MlKitTextTranslationBackend() }
        main { engine.translatePage(null, "ja", wifiOnly = false) }
        val result = runBlocking { withTimeout(200_000) { engine.state.first {
            it.translation.stage == TranslationStage.TRANSLATED || it.translation.stage == TranslationStage.ERROR
        } } }
        assertEquals(result.translation.toString(), TranslationStage.TRANSLATED, result.translation.stage)
        assertEquals("en", result.translation.source)
        assertTrue(text("heading"), text("heading").any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' })
        main { engine.restoreOriginalPage() }
        awaitJs("document.getElementById('heading').textContent === ' Hello world '")
    }

    private open class FakeTranslator : TextTranslationBackend {
        override suspend fun identify(text: String): String? = "en"
        override suspend fun prepare(source: String, target: String, wifiOnly: Boolean) {}
        override suspend fun translate(text: String): String = "訳:$text"
        override fun close() {}
    }
}
