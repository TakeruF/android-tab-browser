package com.takeruf.nagi

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.net.ServerSocket

/** Real failed navigations, native replacement screens, retry/history and unaffected site content. */
@OptIn(ExperimentalTestApi::class)
class PageErrorUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var tabId: String
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 15 }
        compose.waitForIdle()
    }
    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, bottomSearchBar = true) }
            container.spaces.create("Error QA ${System.nanoTime()}")
            val space = container.settings.settings.first().selectedSpaceId
            tabId = container.workspace.dao.spaces().first { it.id == space }.activeTabId!!
        }
        server = FixtureServer(statusCodes = mapOf("/missing" to 404), pages = mapOf(
            "/missing" to "<title>Site 404</title><h1>The site supplied this error page</h1>"))
        resize(360)
    }
    @After fun cleanup() {
        if (::server.isInitialized) server.close()
        if (::original.isInitialized) runBlocking { container.settings.update { original } }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
    }
    private fun open(url: String) {
        compose.onNodeWithTag("url-drop-$tabId").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(url)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
    }
    private fun views(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown } }
        return result
    }
    private fun awaitPage(url: String, title: String) {
        compose.waitUntil(15_000) { runBlocking {
            container.workspace.dao.tab(tabId)?.let { it.url == url && it.title == title } == true
        } }
        compose.waitUntil(15_000) {
            val view = views().singleOrNull()
            var loaded = false
            if (view != null) compose.runOnIdle { loaded = view.progress == 100 }
            loaded
        }
        compose.onNodeWithTag("browser-error-page").assertDoesNotExist()
    }
    private fun awaitError(kind: String, timeout: Long = 15_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithTag("browser-error-icon-$kind").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser-error-retry").assertIsEnabled()
        assertTrue("Provider error WebView must be detached, including its accessibility tree", views().isEmpty())
        compose.onAllNodes(hasText("net::", substring = true)).assertCountEquals(0)
    }
    private fun capture(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(compose.activity.getExternalFilesDir(null), "page-error-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun connectionErrorAdaptsAcrossThemesAndWidthsAndRetryKeepsHistory() {
        val first = "${server.origin}/one"
        open(first); awaitPage(first, "Fixture One")
        val view = views().single()
        val port = ServerSocket(0).use { it.localPort }
        val failed = "http://127.0.0.1:$port/retry"
        open(failed); awaitError("CONNECTION")
        for ((width, theme, bottom) in listOf(Triple(360, ThemeMode.LIGHT, true), Triple(411, ThemeMode.DARK, false),
            Triple(1000, ThemeMode.LIGHT, false), Triple(1000, ThemeMode.DARK, true))) {
            runBlocking { container.settings.update { it.copy(theme = theme, bottomSearchBar = bottom) } }
            resize(width); awaitError("CONNECTION")
            capture("connection-$width-${theme.name.lowercase()}-$bottom")
        }
        FixtureServer(port = port).use {
            compose.onNodeWithTag("browser-error-retry").performClick()
            awaitPage(failed, "Fixture One")
            assertSame("Retry reuses the existing native engine", view, views().single())
            compose.runOnIdle {
                val history = view.copyBackForwardList()
                assertEquals(first, history.getItemAtIndex(history.currentIndex - 1).url)
                assertEquals(failed, history.currentItem!!.url)
            }
            compose.onNodeWithContentDescription("Back").performClick()
            awaitPage(first, "Fixture One")
        }
    }
    @Test fun certificateErrorsUseSecurityDesignButSubresourcesAndSite404RemainVisible() {
        TlsErrorFixture().use { tls ->
            open("${tls.origin}/certificate")
            try { awaitError("CERTIFICATE") }
            catch (error: Throwable) {
                capture("certificate-failure")
                throw AssertionError("TLS attempts=${tls.attempts.get()}; failures=${tls.failures}; ${compose.onRoot().printToString()}", error)
            }
            capture("certificate-light")
            FixtureServer(pages = mapOf("/image" to "<title>Subresource ready</title><img src='${tls.origin}/image'>")).use { site ->
                val previousAttempts = tls.attempts.get()
                open("${site.origin}/image"); awaitPage("${site.origin}/image", "Subresource ready")
                compose.waitUntil(10_000) { tls.attempts.get() > previousAttempts }
                compose.onNodeWithTag("browser-error-page").assertDoesNotExist()
            }
        }
        open("${server.origin}/missing"); awaitPage("${server.origin}/missing", "Site 404")
        val result = CompletableDeferred<String>()
        val view = views().single()
        compose.runOnIdle { view.evaluateJavascript("document.body.innerText") { result.complete(it) } }
        assertTrue(runBlocking { withTimeout(5_000) { result.await() } }.contains("The site supplied this error page"))
    }
    @Test fun failedSplitPanePreservesThePartnersLivePageAndDraft() {
        val first = "${server.origin}/one"
        open(first); awaitPage(first, "Fixture One")
        val left = views().single()
        resize(1000)
        FixtureServer(pages = mapOf("/draft" to "<title>Partner</title><input id='draft' value='unfinished'>")).use { partner ->
            val second = runBlocking {
                val space = container.settings.settings.first().selectedSpaceId
                val second = container.tabs.create(space, "${partner.origin}/draft")
                container.tabs.pair(tabId, second); container.tabs.select(tabId)
                second
            }
            compose.waitUntil(15_000) { views().size == 2 }
            val right = views().first { it !== left }
            compose.waitUntil(15_000) { var ready = false; compose.runOnIdle { ready = right.progress == 100 }; ready }
            compose.runOnIdle { right.evaluateJavascript("document.getElementById('draft').value='Keep this draft';window.errorTestToken=42", null) }
            val port = ServerSocket(0).use { it.localPort }
            compose.runOnIdle { left.loadUrl("http://127.0.0.1:$port/split-error") }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("browser-error-icon-CONNECTION").fetchSemanticsNodes().size == 1 }
            runBlocking { container.tabs.select(second) }
            compose.waitForIdle()
            compose.onNodeWithTag("browser-error-page").performTouchInput { click(center) }
            compose.waitUntil(5_000) { runBlocking {
                container.workspace.dao.spaces().first { it.id == container.settings.settings.first().selectedSpaceId }.activeTabId == tabId
            } }
            for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                runBlocking { container.settings.update { it.copy(theme = theme) } }
                compose.waitForIdle()
                assertSame(right, views().single())
                val result = CompletableDeferred<String>()
                compose.runOnIdle { right.evaluateJavascript("JSON.stringify([document.getElementById('draft').value,window.errorTestToken])") { result.complete(it) } }
                val data = runBlocking { withTimeout(5_000) { result.await() } }
                assertTrue(data, data.contains("Keep this draft") && data.contains("42"))
                capture("split-${theme.name.lowercase()}")
            }
        }
    }
    @Test fun loadingTimeoutAndOtherMainFrameErrorsUseTheSameNativeDesign() {
        open("${server.origin}/stall"); awaitError("TIMEOUT", 40_000); capture("timeout-light")
        open("${server.origin}/one"); awaitPage("${server.origin}/one", "Fixture One")
        val view = views().single()
        // Direct provider coverage of a denied/missing local file, without enabling file access.
        // Nagi's public navigation API continues to accept only HTTP(S) and about:blank.
        compose.runOnIdle { view.loadUrl("file:///nagi-page-error-fixture-missing.html") }
        awaitError("OTHER"); capture("other-light")
    }
}
