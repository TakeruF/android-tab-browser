package com.takeruf.nagi

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

class SiteDisplayModeEngineTest {
    private lateinit var server: FixtureServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var store: SiteDisplayModeStore
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
    private fun awaitMode(url: String, desktop: Boolean) = runBlocking {
        try {
            withTimeout(15_000) {
                engine.state.first {
                    it.url == url && it.desktopMode == desktop && !it.isLoading &&
                        it.title == "Fixture ${if (desktop) "Desktop" else "Mobile"}"
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Expected $url with desktop=$desktop; actual ${engine.state.value}", timeout)
        }
    }
    private fun createEngine(desktop: Boolean = true) {
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, desktop,
                siteDisplayModes = store, defaultDesktopMode = { true })
            it.addContentView(engine.surface, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("site_display_mode_engine_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = SiteDisplayModeStore(context, preferences)
        server = FixtureServer()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        createEngine()
    }
    @After fun cleanup() {
        main { engine.destroy() }
        scenario.close()
        server.close()
    }

    @Test fun mobileChoiceSurvivesLinksNewEnginesAndDesktopReturn() {
        val first = "${server.origin}/ua-first"
        val next = "${server.origin}/ua-next"
        main { engine.loadUrl(first) }
        awaitMode(first, true)
        main { engine.setDesktopMode(false) }
        awaitMode(first, false)
        main { engine.evaluateJavascript("location.href='$next'") }
        awaitMode(next, false)
        main { engine.destroy() }
        createEngine()
        main { engine.loadUrl(next) }
        awaitMode(next, false)
        main { engine.setDesktopMode(true) }
        awaitMode(next, true)
        main { engine.destroy() }
        createEngine(false)
        main { engine.loadUrl(first) }
        awaitMode(first, true)
    }

    @Test fun crossHostLinksAndBackForwardApplyEachHostsChoice() {
        val mobile = "${server.origin}/ua-mobile"
        val desktop = "${server.origin.replace("127.0.0.1", "localhost")}/ua-desktop"
        store.remember(mobile, false)
        main { engine.loadUrl(mobile) }
        awaitMode(mobile, false)
        main { engine.evaluateJavascript("location.href='$desktop'") }
        awaitMode(desktop, true)
        assertEquals(1, server.requests.count { it.path == "/ua-mobile" })
        assertEquals(1, server.requests.count { it.path == "/ua-desktop" })
        main { engine.goBack() }
        awaitMode(mobile, false)
        main { engine.goForward() }
        awaitMode(desktop, true)
    }

    @Test fun providedRefererIsPreservedWithoutCopyingTheOldUserAgent() {
        val source = "${server.origin}/ua-header-source"
        val target = "${server.origin.replace("127.0.0.1", "localhost")}/ua-header-target"
        store.remember(source, false)
        main { engine.loadUrl(source) }
        awaitMode(source, false)
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(target)
            override fun isForMainFrame() = true
            override fun isRedirect() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = mapOf(
                "rEfErEr" to source,
                "User-Agent" to "Android previous mobile UA",
            )
        }
        main {
            val view = engine.surface as WebView
            assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
        }
        awaitMode(target, true)
        val received = server.requests.first { it.path == "/ua-header-target" }
        // WebView still applies its native cross-origin referrer policy to extra headers.
        assertEquals("${server.origin}/", received.referer)
        assertTrue(received.userAgent.contains("X11; Linux x86_64"))
        assertEquals(1, server.requests.count { it.path == "/ua-header-source" })
        assertEquals(1, server.requests.count { it.path == "/ua-header-target" })
    }

    @Test fun crossHostRedirectStartsTheTargetOnceWithItsChosenMode() {
        val source = "${server.origin}/ua-source"
        val target = "${server.origin.replace("127.0.0.1", "localhost")}/ua-redirect-target"
        store.remember(source, false)
        main { engine.loadUrl(source) }
        awaitMode(source, false)
        main { engine.evaluateJavascript("location.href='${server.origin}/mode-redirect'") }
        awaitMode(target, true)
        assertEquals(1, server.requests.count { it.path == "/ua-source" })
        assertEquals(1, server.requests.count { it.path == "/mode-redirect" })
        assertEquals(1, server.requests.count { it.path == "/ua-redirect-target" })
        assertTrue(server.requests.first { it.path == "/ua-source" }.userAgent.contains("Android"))
        assertFalse(server.requests.first { it.path == "/ua-redirect-target" }.userAgent.contains("Android"))
    }

    @Test fun suspendedSnapshotUsesTheLatestSiteChoiceInsteadOfAnOldTabMode() {
        val url = "${server.origin}/ua-snapshot"
        main { engine.loadUrl(url) }
        awaitMode(url, true)
        var snapshot: EngineSnapshot? = null
        main { snapshot = engine.saveState(); engine.destroy() }
        assertNotNull(snapshot)
        store.remember(url, false)
        createEngine()
        main { assertTrue(engine.restoreState(snapshot!!)); engine.reload() }
        awaitMode(url, false)
    }
}
