package com.takeruf.nagi

import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import com.takeruf.nagi.browser.tabs.BrowserSessionController
import com.takeruf.nagi.data.room.model
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.junit.*
import org.junit.Assert.*
import org.mlm.adblock.AdblockEngine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class PrivacyReaderEngineTest {
    private lateinit var server: FixtureServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private val engines = mutableListOf<WebViewBrowserEngine>()
    private val profiles = mutableListOf<String>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) { result(null) }
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) { result(emptySet()) }
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) { exit() }
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) block()
        else InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
    private val normal get() = (activity.application as NagiApplication).container
    @Before fun setup() {
        server = FixtureServer()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it; it.setContent {} }
    }
    @After fun cleanup() {
        main { engines.forEach { it.destroy() } }
        profiles.forEach { name ->
            val done = CompletableFuture<Unit>()
            main { PrivateProfiles.clear(name) { done.complete(Unit) } }
            done.get(15, TimeUnit.SECONDS)
        }
        scenario.close(); server.close()
    }
    private fun engine(profile: String? = null): WebViewBrowserEngine {
        lateinit var result: WebViewBrowserEngine
        main { result = WebViewBrowserEngine(activity, host, host, { false }, false,
            adBlocker = normal.adBlocker, privateProfileName = profile) }
        engines.add(result); return result
    }
    private fun load(engine: BrowserEngine, path: String) {
        main { engine.loadUrl(server.origin + path) }
        runBlocking { withTimeout(20_000) { engine.state.first { it.url == server.origin + path && !it.isLoading && it.progress == 100 } } }
    }
    private fun js(engine: WebViewBrowserEngine, script: String): String {
        val result = CompletableFuture<String>()
        main { (engine.surface as WebView).evaluateJavascript(script) { result.complete(it) } }
        return result.get(10, TimeUnit.SECONDS)
    }
    @Test fun bundledListsBlockActualWebViewRequestsAndSiteExclusionWorks() {
        val browser = engine()
        assertEquals("ready", runBlocking { withTimeout(20_000) { normal.adBlocker.status.first { it != "loading" } } })
        load(browser, "/article?local-ad")
        assertFalse("Blocked ad reached the local server", server.requests.any { it.path == "/ads/custom_ads.js" })
        runBlocking { withTimeout(5_000) { browser.state.first { it.blockedRequests > 0 } } }
        main { browser.configureContentBlocking(true, setOf("127.0.0.1")) }
        load(browser, "/article?local-ad&excluded")
        assertEquals(0, browser.state.value.blockedRequests)
        assertTrue("Site exception did not allow the advertisement", server.requests.any { it.path == "/ads/custom_ads.js" })
        main { browser.configureContentBlocking(false, emptySet()) }
        load(browser, "/article?local-ad&disabled")
        assertEquals(0, browser.state.value.blockedRequests)
    }
    @Test fun siteVisitExceptionSharesArticleTabsAndEndsAfterLastTabLeaves() {
        assertEquals("ready", runBlocking { withTimeout(20_000) { normal.adBlocker.status.first { it != "loading" } } })
        val observer = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        lateinit var pool: com.takeruf.nagi.browser.tabs.EnginePool
        pool = com.takeruf.nagi.browser.tabs.EnginePool(factory = { _, _ -> engine() }, onCreate = { id, browser ->
            observer.launch { browser.state.collect { pool.onPageChanged(id, it.url) } }
        })
        try {
            lateinit var browser: WebViewBrowserEngine
            main { browser = pool.acquire("home", server.origin + "/article?local-ad&first", false) as WebViewBrowserEngine }
            runBlocking { withTimeout(20_000) { browser.state.first { !it.isLoading && it.progress == 100 && it.blockedRequests > 0 } } }
            main { pool.setVisitBlockingPaused(browser.state.value.url, true); browser.reload() }
            runBlocking { withTimeout(20_000) { browser.state.first { !it.isLoading && it.progress == 100 } } }
            assertTrue(server.requests.any { it.path == "/ads/custom_ads.js" })
            assertEquals(0, browser.state.value.blockedRequests)
            assertEquals("127.0.0.1", browser.state.value.visitBlockingExceptionSite)
            val adsBefore = server.requests.count { it.path == "/ads/custom_ads.js" }
            lateinit var article: WebViewBrowserEngine
            main { article = pool.acquire("article", server.origin + "/article?local-ad&new-article", false) as WebViewBrowserEngine }
            runBlocking { withTimeout(20_000) { article.state.first { !it.isLoading && it.progress == 100 } } }
            assertEquals("127.0.0.1", article.state.value.visitBlockingExceptionSite)
            assertEquals(adsBefore + 1, server.requests.count { it.path == "/ads/custom_ads.js" })
            // A separate/private workspace has no shared exception.
            val independent = engine()
            load(independent, "/article?local-ad&independent")
            assertNull(independent.state.value.visitBlockingExceptionSite)
            assertTrue(independent.state.value.blockedRequests > 0)
            val otherUrl = server.origin.replace("127.0.0.1", "localhost") + "/article?local-ad&other-host"
            main { browser.loadUrl(otherUrl) }
            runBlocking { withTimeout(20_000) { browser.state.first { it.url == otherUrl && !it.isLoading && it.progress == 100 } } }
            assertNull(browser.state.value.visitBlockingExceptionSite)
            assertTrue(browser.state.value.blockedRequests > 0)
            assertEquals("127.0.0.1", article.state.value.visitBlockingExceptionSite)
            main { browser.goBack() }
            runBlocking { withTimeout(20_000) { browser.state.first { it.url.contains("first") && !it.isLoading && it.progress == 100 } } }
            assertEquals("127.0.0.1", browser.state.value.visitBlockingExceptionSite)
            main { browser.loadUrl(otherUrl) }
            runBlocking { withTimeout(20_000) { browser.state.first { it.url == otherUrl && !it.isLoading && it.progress == 100 } } }
            main { pool.retainTabIds(setOf("home")) }
            load(browser, "/article?local-ad&fresh-visit")
            assertNull(browser.state.value.visitBlockingExceptionSite)
            assertTrue(browser.state.value.blockedRequests > 0)
        } finally { main { observer.cancel(); pool.destroyAll() } }
    }
    @Test fun bundledRuleHandlesLoopbackScripts() {
        val rules = activity.assets.open("adblock/easylist.txt").bufferedReader().use { it.readText() }
        AdblockEngine.create().use { engine ->
            assertTrue(engine.loadFilterList(rules))
            val url = server.origin + "/ads/custom_ads.js"
            val decision = engine.checkNetworkRequest(url, server.origin + "/article", "script")
            assertTrue("Loopback decision: $decision", decision.matched)
            val request = object : android.webkit.WebResourceRequest {
                override fun getUrl() = android.net.Uri.parse(url)
                override fun isForMainFrame() = false
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = mapOf("Accept" to "*/*")
            }
            assertNotNull(normal.adBlocker.intercept(request, server.origin + "/article"))
        }
    }
    @Test fun japaneseCosmeticRulesCollapseAdFramesAndRespectVisitPause() {
        assertEquals("ready", runBlocking { withTimeout(20_000) { normal.adBlocker.status.first { it != "loading" } } })
        val browser = engine()
        val url = "https://www.asahi.com/"
        // Match the site's actual outer/inner ad wrapper and its reserved height.
        // The similarly empty editorial spacing must remain intact.
        main {
            (browser.surface as WebView).loadDataWithBaseURL(url, """
            <html><head><title>Cosmetic fixture</title></head><body style="margin:0">
            <div id="HometopAdOuter"><div id="HometopAd" style="min-height:110px"></div></div>
            <header id="masthead" style="height:60px">朝日新聞</header>
            <div id="editorial-space" style="height:120px"></div><article>Article content</article>
            </body></html>
        """.trimIndent(), "text/html", "UTF-8", null) }
        runBlocking { withTimeout(10_000) {
            while (js(browser, "!!document.getElementById('masthead')") != "true") delay(50)
        } }
        main {
            // loadDataWithBaseURL supplies about:blank navigation metadata. Feed
            // its actual base URL through the regular history callback, then use
            // the real policy controls to install/remove cosmetics without network.
            val view = browser.surface as WebView
            view.webViewClient.doUpdateVisitedHistory(view, url, false)
            browser.configureContentBlocking(false, emptySet())
            browser.configureContentBlocking(true, emptySet())
        }
        assertEquals("0", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
        assertEquals("0", js(browser, "document.getElementById('masthead').getBoundingClientRect().top"))
        assertEquals("120", js(browser, "document.getElementById('editorial-space').getBoundingClientRect().height"))
        // CSS also handles ad frames added after initial page load, without a DOM polling loop.
        js(browser, "document.body.insertAdjacentHTML('beforeend','<div class=\"p-ad-area\" style=\"height:200px\">Late ad</div>');true")
        assertEquals("0", js(browser, "document.querySelector('.p-ad-area').getBoundingClientRect().height"))
        main { browser.configureVisitBlockingExceptions(setOf("asahi.com")) }
        assertEquals("110", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
        assertEquals("200", js(browser, "document.querySelector('.p-ad-area').getBoundingClientRect().height"))
        main { browser.configureVisitBlockingExceptions(emptySet()) }
        assertEquals("0", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
        main { browser.configureContentBlocking(true, setOf("www.asahi.com")) }
        assertEquals("110", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
        main { browser.configureContentBlocking(false, emptySet()) }
        assertEquals("110", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
        main { browser.configureContentBlocking(true, emptySet()) }
        assertEquals("0", js(browser, "document.getElementById('HometopAdOuter').getBoundingClientRect().height"))
    }
    @Test fun nativeEngineHonorsExceptionsAndResourceTypes() {
        AdblockEngine.create().use { engine ->
            assertTrue(engine.loadFilterList("||ads.example^\n@@||ads.example/allowed.js\n||typed.example^\$image"))
            assertTrue(engine.shouldBlock("https://ads.example/ad.js", "https://news.example/", "script"))
            assertFalse(engine.shouldBlock("https://ads.example/allowed.js", "https://news.example/", "script"))
            assertTrue(engine.shouldBlock("https://typed.example/a.png", "https://news.example/", "image"))
            assertFalse(engine.shouldBlock("https://typed.example/a.js", "https://news.example/", "script"))
        }
    }
    @Test fun livedoorCompatibilityRuleCollapsesOnlyTheAdvertisingParent() {
        assertEquals("ready", runBlocking { withTimeout(20_000) { normal.adBlocker.status.first { it != "loading" } } })
        val browser = engine()
        val url = "https://news.livedoor.com/"
        main { (browser.surface as WebView).loadDataWithBaseURL(url, """
            <html><head><title>Livedoor fixture</title></head><body>
            <div class="subInner">
              <aside id="advertising-parent" class="subSec sbuFirst" style="min-height:300px">
                <div id="div-gpt-ad-1751421318936-0" style="min-height:250px"></div>
              </aside>
              <aside id="news" class="subSec" style="height:100px">Today's news</aside>
            </div>
            <div><aside id="editorial" class="subSec" style="height:150px">Editorial</aside></div>
            </body></html>
        """.trimIndent(), "text/html", "UTF-8", null) }
        runBlocking { withTimeout(10_000) {
            while (js(browser, "!!document.getElementById('news')") != "true") delay(50)
        } }
        main {
            val view = browser.surface as WebView
            view.webViewClient.doUpdateVisitedHistory(view, url, false)
            browser.configureContentBlocking(false, emptySet())
            browser.configureContentBlocking(true, emptySet())
        }
        assertEquals("0", js(browser, "document.getElementById('advertising-parent').getBoundingClientRect().height"))
        assertEquals("100", js(browser, "document.getElementById('news').getBoundingClientRect().height"))
        assertEquals("150", js(browser, "document.getElementById('editorial').getBoundingClientRect().height"))
        main { browser.configureVisitBlockingExceptions(setOf("livedoor.com")) }
        assertEquals("300", js(browser, "document.getElementById('advertising-parent').getBoundingClientRect().height"))
        main { browser.configureVisitBlockingExceptions(emptySet()) }
        assertEquals("0", js(browser, "document.getElementById('advertising-parent').getBoundingClientRect().height"))
    }
    @Test fun readabilityExtractsArticleAndLeavesLiveFormAndHistoryIntact() {
        val browser = engine()
        load(browser, "/article")
        js(browser, "document.getElementById('draft').value='retain this draft';window.scrollTo(0,200);true")
        val previous = browser.state.value
        main { browser.toggleReader() }
        val reader = runBlocking { withTimeout(15_000) { browser.state.first { it.readerArticle != null } } }.readerArticle!!
        assertTrue(reader.content.contains("日本語と中文と한국어"))
        assertFalse(reader.content.contains("Navigation should disappear"))
        assertFalse(reader.content.contains("<script"))
        assertFalse(reader.content.contains("<input"))
        main { browser.goBack() }
        assertNull(browser.state.value.readerArticle)
        assertEquals(previous.url, browser.state.value.url)
        assertEquals("\"retain this draft\"", js(browser, "document.getElementById('draft').value"))
        assertEquals(previous.canGoBack, browser.state.value.canGoBack)
    }
    @Test fun privateProfileIsolatesCookiesAndStorageAndClearsWithoutTouchingNormal() {
        main { assertTrue("Test WebView must support profile isolation and deletion", PrivateProfiles.supported()) }
        lateinit var profile: String
        main { profile = PrivateProfiles.create(); profiles.add(profile) }
        val regular = engine(); val private = engine(profile)
        load(regular, "/privacy")
        js(regular, "document.cookie='normal=keep;path=/';localStorage.setItem('normal','keep');true")
        load(private, "/privacy")
        assertEquals("\"\"", js(private, "document.cookie"))
        assertEquals("null", js(private, "localStorage.getItem('normal')"))
        js(private, "document.cookie='private=secret;path=/';localStorage.setItem('private','secret');true")
        assertFalse(js(regular, "document.cookie").contains("secret"))
        main { private.destroy() }
        val done = CompletableFuture<Unit>()
        main { PrivateProfiles.clear(profile) { done.complete(Unit) } }
        done.get(15, TimeUnit.SECONDS)
        val fresh = engine(profile)
        load(fresh, "/privacy")
        assertEquals("\"\"", js(fresh, "document.cookie"))
        assertEquals("null", js(fresh, "localStorage.getItem('private')"))
        assertTrue(js(regular, "document.cookie").contains("normal=keep"))
        assertEquals("\"keep\"", js(regular, "localStorage.getItem('normal')"))
    }
    @Test fun privateSessionDoesNotWriteNormalWorkspaceHistoryOrFavicons() {
        lateinit var profile: String
        main { assertTrue(PrivateProfiles.supported()); profile = PrivateProfiles.create(); profiles.add(profile) }
        val container = AppContainer(activity.application, profile, normal.settings, normal.adBlocker)
        runBlocking { withTimeout(15_000) { container.workspace.ready.await() } }
        val tab = runBlocking { container.workspace.dao.tabs("personal").first().model() }
        lateinit var sessions: BrowserSessionController
        main { sessions = BrowserSessionController(container) { _, _ -> engine(profile) } }
        lateinit var browser: BrowserEngine
        main { browser = sessions.acquire(tab, false) }
        load(browser, "/privacy?private-only")
        runBlocking { withTimeout(10_000) { container.workspace.snapshot.first { it.tabs.any { t -> t.url.contains("private-only") } } } }
        val privateSnapshot = runBlocking { container.workspace.snapshot.first() }
        val normalSnapshot = runBlocking { normal.workspace.snapshot.first() }
        assertTrue(privateSnapshot.history.isEmpty())
        assertFalse(normalSnapshot.tabs.any { it.url.contains("private-only") })
        assertFalse(normalSnapshot.history.any { it.url.contains("private-only") })
        assertTrue(privateSnapshot.tabs.all { it.faviconUrl == null })
        main { sessions.dispose() }
        container.closePrivateWorkspace()
    }
}
