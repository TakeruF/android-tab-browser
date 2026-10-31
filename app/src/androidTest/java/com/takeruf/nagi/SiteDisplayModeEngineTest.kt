package com.takeruf.nagi

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
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
    private var automaticDesktop = true
    private val downloads = java.util.concurrent.CopyOnWriteArrayList<DownloadRequest>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) { downloads.add(request) }
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
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            engine = WebViewBrowserEngine(it, host, host, { false }, desktop,
                siteDisplayModes = store, defaultDesktopMode = { automaticDesktop })
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

    @Test fun snapshotKeepsItsDocumentUntilReloadAppliesTheLatestSiteChoice() {
        val url = "${server.origin}/ua-snapshot"
        main { engine.loadUrl(url) }
        awaitMode(url, true)
        var snapshot: EngineSnapshot? = null
        main { snapshot = engine.saveState(); engine.destroy() }
        assertNotNull(snapshot)
        store.remember(url, false)
        createEngine()
        main { assertTrue(engine.restoreState(snapshot!!)) }
        awaitMode(url, true)
        main { engine.reload() }
        awaitMode(url, false)
    }

    @Test fun automaticDefaultsApplyOnTheNextLinkAndReloadWithoutSavingASiteChoice() {
        val url = "${server.origin}/ua-automatic"
        val next = "${server.origin}/ua-automatic-next"
        main { engine.loadUrl(url) }
        awaitMode(url, true)
        main { automaticDesktop = false }
        awaitMode(url, true)
        assertNull(store.desktopMode(url))
        main { engine.evaluateJavascript("location.href='$next'") }
        awaitMode(next, false)
        main { automaticDesktop = true }
        awaitMode(next, false)
        main { engine.reload() }
        awaitMode(next, true)
        assertNull(store.desktopMode(next))
        assertEquals(1, server.requests.count { it.path == "/ua-automatic" })
        assertEquals(listOf(true, false), server.requests.filter { it.path == "/ua-automatic-next" }
            .map { it.userAgent.contains("Mobile") })
        main { assertEquals(2, (engine.surface as WebView).copyBackForwardList().size) }
    }

    @Test fun explicitChoicesOverrideBothAutomaticDefaultsAndEngineRecreation() {
        val url = "${server.origin}/ua-override"
        main { automaticDesktop = false; engine.loadUrl(url) }
        awaitMode(url, false)
        main { engine.setDesktopMode(true) }
        awaitMode(url, true)
        main { automaticDesktop = true; engine.reload() }
        awaitMode(url, true)
        main { engine.setDesktopMode(false) }
        awaitMode(url, false)
        main { automaticDesktop = false; engine.reload() }
        awaitMode(url, false)
        main { automaticDesktop = true; engine.destroy() }
        createEngine()
        main { engine.loadUrl(url) }
        awaitMode(url, false)
        assertEquals(false, store.desktopMode(url))
    }

    @Test fun snapshotKeepsItsDocumentUntilReloadAppliesTheCurrentAutomaticDefault() {
        val url = "${server.origin}/ua-auto-snapshot"
        main { engine.loadUrl(url) }
        awaitMode(url, true)
        var snapshot: EngineSnapshot? = null
        main { snapshot = engine.saveState(); engine.destroy(); automaticDesktop = false }
        createEngine()
        main { assertTrue(engine.restoreState(snapshot!!)) }
        awaitMode(url, true)
        main { engine.reload() }
        awaitMode(url, false)
        assertNull(store.desktopMode(url))
    }

    private fun awaitDeviceMode(url: String, minimumRequests: Int = 1) = runBlocking {
        withTimeout(15_000) {
            engine.state.first {
                it.url == url && it.deviceDefaultMode && !it.desktopMode && !it.isLoading &&
                    it.title.startsWith("Fixture") && server.requests.count { r -> r.path == Uri.parse(url).path } >= minimumRequests
            }
        }
    }

    private fun assertNativeIdentity(url: String) = main {
        val view = engine.surface as WebView
        val native = WebView(view.context)
        try {
            assertEquals(WebSettings.getDefaultUserAgent(view.context), view.settings.userAgentString)
            assertEquals(view.settings.userAgentString, server.requests.last { it.path == Uri.parse(url).path }.userAgent)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
                assertEquals(WebSettingsCompat.getUserAgentMetadata(native.settings), WebSettingsCompat.getUserAgentMetadata(view.settings))
        } finally { native.destroy() }
    }

    @Test fun crossHostAttachmentKeepsIdentityFlagsCoherentAndReloadRestoresSourceChoice() {
        val cases = listOf(
            SiteDisplayMode.DEVICE_DEFAULT to SiteDisplayMode.DESKTOP,
            SiteDisplayMode.DESKTOP to SiteDisplayMode.DEVICE_DEFAULT,
            SiteDisplayMode.DEVICE_DEFAULT to SiteDisplayMode.MOBILE,
            SiteDisplayMode.MOBILE to SiteDisplayMode.DEVICE_DEFAULT,
        )
        cases.forEachIndexed { index, (sourceMode, targetMode) ->
            val source = "${server.origin}/ua-download-source-$index"
            val attachment = "${server.origin.replace("127.0.0.1", "localhost")}/download-mode-$index"
            fun remember(url: String, mode: SiteDisplayMode) {
                if (mode == SiteDisplayMode.DEVICE_DEFAULT) store.rememberDeviceDefault(url)
                else store.remember(url, mode == SiteDisplayMode.DESKTOP)
            }
            remember(source, sourceMode)
            remember(attachment, targetMode)
            main { engine.loadUrl(source) }
            if (sourceMode == SiteDisplayMode.DEVICE_DEFAULT) awaitDeviceMode(source)
            else awaitMode(source, sourceMode == SiteDisplayMode.DESKTOP)
            val previousDownloads = downloads.size
            main { engine.evaluateJavascript("location.href='$attachment'") }
            runBlocking { withTimeout(15_000) {
                while (downloads.size == previousDownloads) kotlinx.coroutines.delay(25)
            } }
            val previousRequests = server.requests.count { it.path == Uri.parse(source).path }
            main {
                val state = engine.state.value
                assertEquals("An attachment must retain the visible source URL", source, state.url)
                assertFalse(state.isLoading)
                assertEquals(targetMode == SiteDisplayMode.DESKTOP, state.desktopMode)
                assertEquals(targetMode == SiteDisplayMode.DEVICE_DEFAULT, state.deviceDefaultMode)
                assertEquals(downloads.last().userAgent, (engine.surface as WebView).settings.userAgentString)
                engine.reload()
            }
            runBlocking { withTimeout(15_000) {
                while (server.requests.count { it.path == Uri.parse(source).path } <= previousRequests)
                    kotlinx.coroutines.delay(25)
            } }
            if (sourceMode == SiteDisplayMode.DEVICE_DEFAULT) {
                awaitDeviceMode(source, previousRequests + 1)
                assertNativeIdentity(source)
            } else {
                awaitMode(source, sourceMode == SiteDisplayMode.DESKTOP)
                assertFalse(engine.state.value.deviceDefaultMode)
            }
        }
    }

    @Test fun deviceDefaultChoiceKeepsNativeIdentityAcrossLinksAndNewEngines() {
        val first = "${server.origin}/ua-device-first"
        val next = "${server.origin}/ua-device-next"
        main { engine.loadUrl(first) }
        awaitMode(first, true)
        main { engine.setDeviceDefaultMode() }
        awaitDeviceMode(first, 2)
        assertNativeIdentity(first)
        assertEquals(SiteDisplayMode.DEVICE_DEFAULT, store.mode(first))
        main { engine.evaluateJavascript("location.href='$next'") }
        awaitDeviceMode(next)
        assertNativeIdentity(next)
        assertEquals(2, server.requests.count { it.path == "/ua-device-first" })
        main { engine.destroy() }
        createEngine(false)
        main { engine.loadUrl(next) }
        awaitDeviceMode(next, 2)
        assertNativeIdentity(next)
        // Mobile and device-default are distinct even though desktopMode is false for both.
        main { engine.setDesktopMode(false) }
        awaitMode(next, false)
        assertFalse(engine.state.value.deviceDefaultMode)
        assertEquals(SiteDisplayMode.MOBILE, store.mode(next))
        main { engine.setDesktopMode(true) }
        awaitMode(next, true)
        assertFalse(engine.state.value.deviceDefaultMode)
        assertEquals(4, server.requests.count { it.path == "/ua-device-next" })
    }

    @Test fun deviceDefaultChoiceSurvivesCrossHostBackAndForward() {
        val device = "${server.origin}/ua-device-back"
        val mobile = "${server.origin.replace("127.0.0.1", "localhost")}/ua-device-other"
        store.rememberDeviceDefault(device)
        store.remember(mobile, false)
        main { engine.loadUrl(device) }
        awaitDeviceMode(device)
        main { engine.evaluateJavascript("location.href='$mobile'") }
        awaitMode(mobile, false)
        main { engine.goBack() }
        awaitDeviceMode(device)
        assertNativeIdentity(device)
        main { engine.goForward() }
        awaitMode(mobile, false)
        assertFalse(engine.state.value.deviceDefaultMode)
    }

    @Test fun deviceDefaultSnapshotKeepsItsIdentityUntilReloadUsesTheNewChoice() {
        val url = "${server.origin}/ua-device-snapshot"
        store.rememberDeviceDefault(url)
        main { engine.loadUrl(url) }
        awaitDeviceMode(url)
        var snapshot: EngineSnapshot? = null
        main { snapshot = engine.saveState(); engine.destroy() }
        assertNotNull(snapshot)
        store.remember(url, true)
        createEngine()
        main { assertTrue(engine.restoreState(snapshot!!)) }
        awaitDeviceMode(url)
        main { assertEquals(WebSettings.getDefaultUserAgent((engine.surface as WebView).context), (engine.surface as WebView).settings.userAgentString) }
        main { engine.reload() }
        awaitMode(url, true)
        assertFalse(engine.state.value.deviceDefaultMode)
    }
}
