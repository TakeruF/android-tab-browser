package com.takeruf.nagi

import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/** Reload must follow the requested document, not a previous/initial native history entry. */
class ReloadNavigationEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var server: FixtureServer
    private lateinit var engine: BrowserEngine
    private lateinit var view: WebView
    private lateinit var siteDisplayModes: SiteDisplayModeStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val states = CopyOnWriteArrayList<PageState>()
    private val metadata = CopyOnWriteArrayList<PageState>()
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
    private fun await(predicate: (PageState) -> Boolean) = runBlocking {
        try { withTimeout(15_000) { engine.state.first(predicate) } }
        catch (failure: TimeoutCancellationException) {
            throw AssertionError("Reload state=${engine.state.value}; states=$states; requests=${server.requests}", failure)
        }
    }
    private fun requestCount(path: String) = server.requests.count { it.path == path }
    private fun awaitRequest(path: String, count: Int = 1) = runBlocking {
        withTimeout(10_000) { while (requestCount(path) < count) delay(10) }
    }
    private fun load(path: String, title: String) {
        main { engine.loadUrl(server.origin + path) }
        await { !it.isLoading && it.url == server.origin + path && it.title == title }
    }
    private fun assertNoHome() {
        // Initial helper resets may emit a blank state before loadUrl publishes its
        // first HTTP target. Once that target exists, returning to blank is a bug.
        val firstPage = states.indexOfFirst { it.url.startsWith(server.origin) }
        val firstMetadata = metadata.indexOfFirst { it.url.startsWith(server.origin) }
        assertTrue("The HTTP target was never published", firstPage >= 0)
        assertTrue("The HTTP target was never persisted", firstMetadata >= 0)
        assertFalse("A reload emitted the New tab page: $states", states.drop(firstPage).any { it.url == "about:blank" })
        assertFalse("A reload persisted New tab metadata: $metadata", metadata.drop(firstMetadata).any { it.url == "about:blank" })
    }

    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/reload-pending" to
            "<!doctype html><title>Reload target</title><h1>Pending target</h1>"),
            redirects = mapOf("/reload-redirect" to "/reload-pending"),
            pageDelaysMs = mapOf("/reload-pending" to 1_000))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            val preferences = activity.getSharedPreferences("reload_test_site_modes", android.content.Context.MODE_PRIVATE)
            preferences.edit().clear().commit()
            siteDisplayModes = SiteDisplayModeStore(activity, preferences)
            engine = WebViewBrowserEngine(activity, host, host, { false }, false,
                siteDisplayModes = siteDisplayModes)
            view = (engine as AndroidEngineSurface).surface as WebView
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
            scope.launch { engine.state.collect { states += it } }
            scope.launch { engine.events.collect { if (it is EngineEvent.Metadata) metadata += it.page } }
        }
        main { engine.loadUrl("about:blank") }
        // Give the native blank page a real history entry before the first HTTP request.
        runBlocking { withTimeout(10_000) { while (true) {
            var loaded = false
            main { loaded = view.copyBackForwardList().currentItem?.url == "about:blank" && view.progress == 100 }
            if (loaded) break
            delay(10)
        } } }
        main { states.clear(); metadata.clear() }
    }

    @After fun cleanup() {
        scope.cancel()
        if (::engine.isInitialized) main { engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
        if (::server.isInitialized) server.close()
    }

    @Test fun reloadBeforeFirstCommitDoesNotReturnToNewTab() {
        main {
            states.clear(); metadata.clear()
            engine.loadUrl("${server.origin}/reload-pending")
        }
        awaitRequest("/reload-pending")
        main {
            assertEquals("about:blank", view.copyBackForwardList().currentItem?.url)
            engine.reload()
        }
        await { !it.isLoading && it.title == "Reload target" }
        assertEquals("${server.origin}/reload-pending", engine.state.value.url)
        assertNoHome()
    }

    @Test fun reloadDuringNavigationDoesNotReloadThePreviousDocument() {
        load("/one", "Fixture One")
        main { states.clear(); metadata.clear(); engine.loadUrl("${server.origin}/reload-pending") }
        awaitRequest("/reload-pending")
        main {
            assertEquals("${server.origin}/one", view.copyBackForwardList().currentItem?.url)
            engine.reload()
        }
        await { !it.isLoading && it.title == "Reload target" }
        assertEquals("${server.origin}/reload-pending", engine.state.value.url)
        assertEquals("Reload must not request the old document again", 1, requestCount("/one"))
        assertNoHome()
    }

    @Test fun staleBlankHistoryCallbackCannotReplaceTheReloadedPage() {
        load("/one", "Fixture One")
        main {
            states.clear(); metadata.clear()
            engine.reload()
            // Providers can deliver history callbacks for a cancelled initial blank load.
            view.webViewClient.doUpdateVisitedHistory(view, "about:blank", true)
            assertEquals("${server.origin}/one", engine.state.value.url)
        }
        awaitRequest("/one", 2)
        await { !it.isLoading && it.title == "Fixture One" }
        assertNoHome()
    }

    @Test fun reloadDuringAnExplicitGetRedirectKeepsTheRedirectTarget() {
        load("/one", "Fixture One")
        main { states.clear(); metadata.clear(); engine.loadUrl("${server.origin}/reload-redirect") }
        awaitRequest("/reload-pending")
        main { engine.reload() }
        await { !it.isLoading && it.title == "Reload target" }
        assertEquals("${server.origin}/reload-pending", engine.state.value.url)
        assertEquals("Redirect reload must not return to the previous document", 1, requestCount("/one"))
        assertNoHome()
    }

    @Test fun committedPostReloadUsesNativeResubmissionInsteadOfNavigatingWithGet() {
        main { view.postUrl("${server.origin}/two", "answer=retained".toByteArray()) }
        await { !it.isLoading && it.title == "Fixture Two" }
        var resubmission = false
        main {
            view.webViewClient = object : WebViewClient() {
                override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
                    resubmission = true
                    dontResend.sendToTarget()
                }
            }
            engine.reload()
        }
        runBlocking { withTimeout(5_000) { while (true) {
            var done = false
            main { done = resubmission }
            if (done) break
            delay(10)
        } } }
        assertEquals(1, requestCount("/two"))
        assertEquals("${server.origin}/two", engine.state.value.url)
    }

    @Test fun retryingAnExplicitDisplayModeNavigationRetainsItsReferrer() {
        load("/one", "Fixture One")
        main {
            // A saved display-mode change makes the native GET link continuation
            // re-enter loadUrl. Supply the request headers deterministically because
            // some providers omit Referer from the shouldOverrideUrlLoading callback.
            siteDisplayModes.remember(server.origin, true)
            val request = object : WebResourceRequest {
                override fun getUrl(): Uri = Uri.parse("${server.origin}/reload-pending")
                override fun isForMainFrame() = true
                override fun isRedirect() = false
                override fun hasGesture() = false
                override fun getMethod() = "GET"
                override fun getRequestHeaders() = mapOf("Referer" to "${server.origin}/one")
            }
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
        }
        awaitRequest("/reload-pending")
        val first = server.requests.first { it.path == "/reload-pending" }
        assertEquals("${server.origin}/one", first.referer)
        main { engine.reload() }
        awaitRequest("/reload-pending", 2)
        await { !it.isLoading && it.title == "Reload target" }
        assertTrue(server.requests.filter { it.path == "/reload-pending" }.all { it.referer == first.referer })
        assertEquals("${server.origin}/reload-pending", engine.state.value.url)
    }
}
