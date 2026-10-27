package com.takeruf.nagi

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Google-shaped HTTPS fixtures run only in an isolated, disposable WebView profile. */
class GoogleSignInRecoveryEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var store: SiteDisplayModeStore
    private lateinit var profile: String
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private val rejected = "https://accounts.google.com/v3/signin/rejected?continue=https%3A%2F%2Fdocs.google.com%2F&service=wise&hl=ja&dsh=obsolete"
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
    private fun await(url: String, desktop: Boolean) = runBlocking {
        withTimeout(15_000) { engine.state.first { it.url == url && !it.isLoading && it.progress == 100 && it.desktopMode == desktop } }
    }
    private fun createEngine(desktop: Boolean) {
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, desktop,
                siteDisplayModes = store, privateProfileName = profile)
            val view = engine.surface
            val original = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val isRejected = GoogleSignInRecovery.isRejectedPage(request.url.toString())
                    if (request.isForMainFrame) requests.add(request.url.toString() to
                        request.requestHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value.orEmpty())
                    val title = if (isRejected) "Rejected fixture" else "Sign-in fixture"
                    return WebResourceResponse("text/html", "UTF-8", if (isRejected) 403 else 200,
                        if (isRejected) "Forbidden" else "OK", mapOf("Cache-Control" to "no-store"),
                        "<!doctype html><title>$title</title><body>$title</body>".byteInputStream())
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = original.shouldOverrideUrlLoading(view, request)
                override fun onPageStarted(view: WebView, url: String, icon: Bitmap?) = original.onPageStarted(view, url, icon)
                override fun onPageFinished(view: WebView, url: String) = original.onPageFinished(view, url)
                override fun doUpdateVisitedHistory(view: WebView, url: String, reload: Boolean) = original.doUpdateVisitedHistory(view, url, reload)
                override fun onPageCommitVisible(view: WebView, url: String) = original.onPageCommitVisible(view, url)
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) = original.onReceivedHttpError(view, request, response)
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = original.onReceivedError(view, request, error)
            }
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @Before fun setup() {
        Assume.assumeTrue(PrivateProfiles.supported())
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { profile = PrivateProfiles.create(); store = SiteDisplayModeStore(it, inMemory = true) }
        createEngine(true)
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::profile.isInitialized) {
            val done = CountDownLatch(1)
            main { PrivateProfiles.clear(profile) { done.countDown() } }
            assertTrue(done.await(10, TimeUnit.SECONDS))
        }
        if (::scenario.isInitialized) scenario.close()
    }

    @Test fun retryRestartsOnceWithMobileUaAndRemembersOnlyTheGoogleHost() {
        main { engine.loadUrl(rejected) }; await(rejected, true)
        assertEquals("Rejected fixture", engine.state.value.title)
        assertNull(engine.state.value.error) // A server-rendered HTTP 403 must still allow the notice.
        requests.clear()
        val retry = GoogleSignInRecovery.retryUrl(rejected)!!
        main { engine.retryGoogleSignInInMobileMode() }; await(retry, false)
        assertEquals(listOf(retry), requests.map { it.first })
        assertTrue(requests.single().second.contains("Mobile"))
        assertTrue(requests.single().second.contains("; wv)"))
        assertEquals(false, store.desktopMode(rejected))
        assertNull(store.desktopMode("https://docs.google.com/"))
        main { engine.loadUrl("https://docs.google.com/") }; await("https://docs.google.com/", true)
        main { engine.destroy() }; createEngine(true)
        val next = "https://accounts.google.com/v3/signin/identifier"
        main { engine.loadUrl(next) }; await(next, false)
    }

    @Test fun staleActionCannotChangeAnOrdinaryPageOrAMobilePage() {
        val ordinary = "https://example.com/v3/signin/rejected"
        main { engine.loadUrl(ordinary) }; await(ordinary, true)
        requests.clear()
        main { engine.retryGoogleSignInInMobileMode() }
        assertTrue(requests.isEmpty())
        assertEquals(ordinary, engine.state.value.url)
        assertTrue(engine.state.value.desktopMode)
        assertNull(store.desktopMode(ordinary))
        main { engine.destroy() }; createEngine(false)
        main { engine.loadUrl(rejected) }; await(rejected, false)
        requests.clear()
        main { engine.retryGoogleSignInInMobileMode() }
        assertTrue(requests.isEmpty())
        assertEquals(rejected, engine.state.value.url)
        assertNull(store.desktopMode(rejected))
    }
}
