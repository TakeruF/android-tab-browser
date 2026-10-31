package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Provider-origin messages are served by a local interceptor. No public CAPTCHA is contacted/solved. */
class HumanVerificationRecoveryEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var server: FixtureServer
    private lateinit var store: SiteDisplayModeStore
    private lateinit var profile: String
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
    private fun evaluate(script: String): String = runBlocking {
        val result = CompletableDeferred<String>()
        main { (engine.surface as WebView).evaluateJavascript(script) { result.complete(it) } }
        withTimeout(5000) { result.await() }
    }
    private fun awaitHint(failed: Boolean) = runBlocking {
        withTimeout(8000) { engine.state.first { it.humanVerification.turnstileFailed == failed } }
    }
    private fun load(path: String) {
        main { engine.loadUrl(server.origin + path) }
        runBlocking { withTimeout(15_000) { engine.state.first { it.url == server.origin + path && !it.isLoading } } }
    }
    private fun signal(event: String, origin: String = "https://challenges.cloudflare.com", source: String = "cloudflare-challenge") {
        val url = "$origin/nagi-human-verification-fixture?event=$event&source=$source"
        evaluate("(() => {const f=document.createElement('iframe');f.src=${JSONObject.quote(url)};document.body.append(f);return true})()")
        // Await an actual child-frame message even when the detector should ignore it.
        runBlocking { withTimeout(5000) {
            while (evaluate("window.fixtureMessages || 0").toInt() == 0) delay(50)
        } }
        evaluate("window.fixtureMessages=0")
    }

    @Before fun setup() {
        Assume.assumeTrue(PrivateProfiles.supported())
        server = FixtureServer(pages = mapOf("/notice" to "<html><title>Notice</title><body>Fixture<script>window.fixtureMessages=0;addEventListener('message',()=>fixtureMessages++);</script></body></html>",
            "/other" to "<html><title>Other</title><body>Other</body></html>"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            profile = PrivateProfiles.create()
            store = SiteDisplayModeStore(activity, inMemory = true)
            engine = WebViewBrowserEngine(activity, host, host, { false }, true,
                siteDisplayModes = store, privateProfileName = profile)
            val view = engine.surface as WebView
            val delegate = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                    if (r.url.path == "/nagi-human-verification-fixture") {
                        val event = r.url.getQueryParameter("event")
                        val source = r.url.getQueryParameter("source")
                        require(event in setOf("fail", "complete", "refreshRequest", "other"))
                        require(source in setOf("cloudflare-challenge", "other"))
                        val html = "<script>parent.postMessage({source:${JSONObject.quote(source)},event:${JSONObject.quote(event)}},'*')</script>"
                        return WebResourceResponse("text/html", "UTF-8", html.byteInputStream())
                    }
                    return delegate.shouldInterceptRequest(v, r)
                }
                override fun onPageStarted(v: WebView, url: String, favicon: android.graphics.Bitmap?) = delegate.onPageStarted(v, url, favicon)
                override fun onPageFinished(v: WebView, url: String) = delegate.onPageFinished(v, url)
                override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) = delegate.doUpdateVisitedHistory(v, url, isReload)
            }
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
        load("/notice")
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::profile.isInitialized) runBlocking { withTimeout(15_000) {
            val cleared = CompletableDeferred<Unit>()
            main { PrivateProfiles.clear(profile) { cleared.complete(Unit) } }
            cleared.await()
        } }
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun failureSuggestsButDoesNotChangeIdentityAndCompletionClearsIt() {
        val ua = evaluate("navigator.userAgent")
        signal("fail")
        awaitHint(true)
        assertTrue(engine.state.value.desktopMode)
        assertEquals(ua, evaluate("navigator.userAgent"))
        assertNull(store.mode(server.origin))
        signal("complete")
        awaitHint(false)
    }

    @Test fun forgedEventsWrongOriginsWrongSourcesAndUnknownEventsAreIgnored() {
        evaluate("dispatchEvent(new MessageEvent('message',{origin:'https://challenges.cloudflare.com',data:{source:'cloudflare-challenge',event:'fail'}}))")
        signal("fail", "https://challenges.cloudflare.com.evil.test")
        signal("fail", source = "other")
        signal("other")
        runBlocking { delay(1500) }
        assertFalse(engine.state.value.humanVerification.turnstileFailed)
    }

    @Test fun sameUrlReloadAndNavigationResetTheFailedDocument() {
        signal("fail"); awaitHint(true)
        val document = engine.state.value.humanVerification.documentId
        main { engine.reload() }
        runBlocking { withTimeout(15_000) { engine.state.first { !it.isLoading && it.humanVerification.documentId > document } } }
        awaitHint(false)
        signal("fail"); awaitHint(true)
        load("/other"); awaitHint(false)
    }

    @Test fun explicitRecoveryUsesTheExistingSiteChoiceAndHidesTheHint() {
        signal("fail"); awaitHint(true)
        main { engine.setDeviceDefaultMode() }
        runBlocking { withTimeout(15_000) { engine.state.first { !it.isLoading && it.deviceDefaultMode } } }
        assertFalse(engine.state.value.desktopMode)
        assertFalse(engine.state.value.humanVerification.turnstileFailed)
        assertEquals(SiteDisplayMode.DEVICE_DEFAULT, store.mode(server.origin))
        assertTrue(evaluate("navigator.userAgent.includes('; wv)')") == "true")
    }

    @Test fun sameDocumentRouteChangeClearsOldFailureAndKeepsObservingNewFailures() {
        signal("fail"); awaitHint(true)
        val document = engine.state.value.humanVerification.documentId
        evaluate("history.pushState({}, '', '/notice?route=next')")
        runBlocking { withTimeout(8000) {
            engine.state.first { it.url.endsWith("?route=next") && it.humanVerification.documentId > document }
        } }
        awaitHint(false)
        runBlocking { delay(1500) }
        assertFalse(engine.state.value.humanVerification.turnstileFailed)
        signal("fail"); awaitHint(true)
        signal("refreshRequest"); awaitHint(false)
    }

    @Test fun hiddenPanePausesObservationAndMobileModeDoesNotSuggestRecovery() {
        main { engine.setVisible(false) }
        signal("fail")
        runBlocking { delay(1500) }
        assertFalse(engine.state.value.humanVerification.turnstileFailed)
        main { engine.setVisible(true) }
        awaitHint(true)
        main { engine.setDesktopMode(false) }
        runBlocking { withTimeout(15_000) { engine.state.first { !it.isLoading && !it.desktopMode } } }
        signal("fail")
        runBlocking { delay(1500) }
        assertFalse(engine.state.value.humanVerification.turnstileFailed)
    }
}
