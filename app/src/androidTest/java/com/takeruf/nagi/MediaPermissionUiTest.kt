package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real WebView getUserMedia -> app consent -> Android permission -> live audio track. */
class MediaPermissionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var spaceId: String
    private lateinit var web: WebView
    private val fixture = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Microphone fixture</title><body style="padding:24px"><textarea id="draft">Keep my draft</textarea>
        <script>window.captureState='idle';window.stream=null;
        async function startCapture(){window.captureState='pending';try{window.stream=await navigator.mediaDevices.getUserMedia({audio:true});window.captureState='live'}catch(e){window.captureState='error:'+e.name+':'+e.message}}
        function stopCapture(){window.stream?.getTracks().forEach(t=>t.stop())}</script></body>"""

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun js(code: String): String {
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun waitUntil(test: () -> Boolean) = compose.waitUntil(20_000, test)
    private fun promptVisible() = compose.onAllNodesWithTag("site-permission-dialog").fetchSemanticsNodes().isNotEmpty()
    private fun loadFixture() {
        main { web.loadDataWithBaseURL("https://microphone.fixture.test/", fixture, "text/html", "UTF-8", null) }
        waitUntil { js("window.captureState") == "\"idle\"" }
        assertEquals("true", js("isSecureContext"))
    }
    private fun beginCapture() {
        js("startCapture();true")
        waitUntil { promptVisible() }
        compose.onNodeWithTag("site-permission-allow").assertIsDisplayed()
        compose.onNodeWithTag("site-permission-block").assertIsDisplayed()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { screenshot.recycle() }
    }
    private fun allowAndAwaitAudio() {
        compose.onNodeWithTag("site-permission-allow").performClick()
        // The OS dialog belongs to Android, not Compose, and may be absent when
        // this dedicated QA app already has RECORD_AUDIO from a prior case.
        val deadline = SystemClock.uptimeMillis() + 20_000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val allow = listOf("permission_allow_foreground_only_button", "permission_allow_one_time_button", "permission_allow_button")
                .asSequence().flatMap { id -> root?.findAccessibilityNodeInfosByViewId("com.android.permissioncontroller:id/$id").orEmpty().asSequence() }
                .firstOrNull { it.isEnabled }
            allow?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val state = js("window.captureState")
            if (state == "\"live\"") break
            if (state.startsWith("\"error:")) fail("getUserMedia failed after Allow: $state")
            SystemClock.sleep(100)
        }
        assertEquals("\"live\"", js("window.captureState"))
        assertEquals("1", js("window.stream.getAudioTracks().length"))
        assertEquals("\"live\"", js("window.stream.getAudioTracks()[0].readyState"))
        assertEquals("true", js("window.stream.getAudioTracks()[0].enabled"))
    }

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            val dark = InstrumentationRegistry.getArguments().getString("mediaTheme") == "dark"
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false,
                theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.spaces.create("Microphone QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.tabs.create(spaceId, server.origin + "/one")
        }
        waitUntil {
            var result: WebView? = null
            main { result = views(compose.activity.window.decorView).firstOrNull { it.isShown && it.url == server.origin + "/one" && it.progress == 100 } }
            if (result != null) web = result!!
            result != null
        }
        loadFixture()
    }
    @After fun cleanup() {
        if (::web.isInitialized) runCatching { js("stopCapture();true") }
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        if (::server.isInitialized) server.close()
    }

    @Test fun allowMicrophoneProducesALiveAudioTrackAndKeepsDraft() {
        beginCapture()
        capture("microphone-consent.png")
        allowAndAwaitAudio()
        assertEquals("\"Keep my draft\"", js("document.getElementById('draft').value"))
        js("stopCapture();true")
        assertEquals("\"ended\"", js("window.stream.getAudioTracks()[0].readyState"))
    }
    @Test fun denialCanRetryAndThenAcquireTheMicrophone() {
        beginCapture()
        compose.onNodeWithTag("site-permission-block").performClick()
        waitUntil { js("window.captureState").contains("NotAllowedError") }
        beginCapture()
        allowAndAwaitAudio()
    }
    @Test fun navigatingAwayCancelsConsentAndDoesNotBlockTheNextRequest() {
        beginCapture()
        main { web.loadUrl(server.origin + "/two") }
        waitUntil { !promptVisible() }
        waitUntil { js("document.title") == "\"Fixture Two\"" }
        loadFixture()
        beginCapture()
        allowAndAwaitAudio()
    }

    @Test fun geolocationHideWithdrawsItsConsentAndAllowsTheNextPrompt() {
        val decisions = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val callback = android.webkit.GeolocationPermissions.Callback { _, allow, _ -> decisions.add(allow) }
        main { web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://location.fixture.test", callback) }
        waitUntil { promptVisible() }
        main { web.webChromeClient!!.onGeolocationPermissionsHidePrompt() }
        waitUntil { !promptVisible() }
        main { web.webChromeClient!!.onGeolocationPermissionsShowPrompt("https://next-location.fixture.test", callback) }
        waitUntil { promptVisible() }
        compose.onNodeWithTag("site-permission-block").performClick()
        waitUntil { !promptVisible() }
        assertEquals(listOf(false), decisions)
    }
}
