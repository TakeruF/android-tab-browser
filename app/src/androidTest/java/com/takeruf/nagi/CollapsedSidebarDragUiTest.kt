package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real rail gestures use the same transactional destinations as expanded rows. */
@OptIn(ExperimentalTestApi::class)
class CollapsedSidebarDragUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var alpha: String
    private lateinit var beta: String
    private lateinit var gamma: String
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        compose.waitForIdle()
    }
    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        server = FixtureServer(pages = listOf("alpha", "beta", "gamma").associate { name ->
            "/collapsed-$name" to "<!doctype html><title>$name</title><meta name='viewport' content='width=device-width'><textarea id='draft'>Unsent 日本語</textarea><script>window.token='$name'</script>"
        })
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, compactUiMode = CompactUiMode.OFF) }
            container.spaces.create("Collapsed drag QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            val blank = container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!!
            alpha = container.tabs.create(spaceId, "${server.origin}/collapsed-alpha")
            beta = container.tabs.create(spaceId, "${server.origin}/collapsed-beta", select = false)
            gamma = container.tabs.create(spaceId, "${server.origin}/collapsed-gamma", select = false)
            container.tabs.close(blank)
        }
        resize(1000)
    }
    @After fun cleanup() {
        compose.runOnIdle { cancelMouseDragTouchStream(compose.activity) }
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        shell("wm size $originalSize")
        server.close()
    }
    private fun pages(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown } }
        return result
    }
    private fun js(page: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { page.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun signature() = runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }
        .map { Triple(it.id, it.isPinned, it.splitRightTabId) } }
    private fun tile(id: String) = compose.onNodeWithTag("collapsed-tab-$id")
    private fun beginDrag(id: String, target: Offset, mouse: Boolean) {
        val source = tile(id)
        val from = source.fetchSemanticsNode().boundsInRoot
        if (mouse) source.performMouseInput { moveTo(center); press(); moveTo(target - from.topLeft, delayMillis = 120) }
        else source.performTouchInput { down(center); advanceEventTime(650); moveTo(target - from.topLeft, delayMillis = 120) }
        compose.onNodeWithTag("tab-drag-preview").assertIsDisplayed()
    }
    private fun finishDrag(id: String, mouse: Boolean, cancel: Boolean = false) {
        if (mouse) {
            tile(id).performMouseInput { if (cancel) cancel() else release() }
            if (cancel) compose.runOnIdle { cancelMouseDragTouchStream(compose.activity) }
        }
        else tile(id).performTouchInput { if (cancel) cancel() else up() }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-drag-preview").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "collapsed-drag-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun touchCompactRailCreatesAndReplacesSplitAndPreservesPairs() = exercise(CompactUiMode.ON, false)
    @Test fun mouseStandardRailCreatesAndReplacesSplitAndPreservesPairs() = exercise(CompactUiMode.OFF, true)
    private fun exercise(mode: CompactUiMode, mouse: Boolean) {
        runBlocking { container.settings.update { it.copy(sidebarCollapsed = true, compactUiMode = mode,
            theme = if (mouse) ThemeMode.LIGHT else ThemeMode.DARK) } }
        compose.onNodeWithTag("sidebar").assertWidthIsEqualTo(if (mode == CompactUiMode.ON) 52.dp else 72.dp)
        compose.waitUntil(15_000) { pages().singleOrNull()?.let { js(it, "!!document.getElementById('draft')") == "true" } == true }
        val originalPage = pages().single()
        js(originalPage, "document.getElementById('draft').value='未送信 draft';document.getElementById('draft').dispatchEvent(new Event('input',{bubbles:true}));true")
        val initial = signature()
        val edge = tile(gamma).fetchSemanticsNode().boundsInRoot.let { Offset(it.center.x, it.bottom - 2f) }
        beginDrag(beta, edge, mouse)
        compose.onNodeWithTag("collapsed-tab-insertion-$gamma").assertIsDisplayed()
        compose.onNodeWithTag("collapsed-tab-split-drop-$gamma").assertDoesNotExist()
        finishDrag(beta, mouse, cancel = true)
        assertEquals(initial, signature())
        beginDrag(beta, edge, mouse)
        finishDrag(beta, mouse)
        compose.waitUntil(5_000) { signature().map { it.first } == listOf(alpha, gamma, beta) }
        val reordered = signature()
        beginDrag(beta, tile(alpha).fetchSemanticsNode().boundsInRoot.center, mouse)
        compose.onNodeWithTag("collapsed-tab-split-drop-$alpha").assertIsDisplayed()
        compose.onNodeWithTag("collapsed-tab-insertion-$alpha").assertDoesNotExist()
        finishDrag(beta, mouse, cancel = true)
        assertEquals(reordered, signature())
        val firstSide = if (mouse) "right" else "left"
        beginDrag(beta, compose.onNodeWithTag("split-drop-$firstSide").fetchSemanticsNode().boundsInRoot.center, mouse)
        capture("${mode.name.lowercase()}-page-hover")
        finishDrag(beta, mouse)
        val pairLeft = if (mouse) alpha else beta
        val pairRight = if (mouse) beta else alpha
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.tab(pairLeft)?.splitRightTabId == pairRight } }
        compose.onNodeWithTag("browser-pane-left-$pairLeft").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$pairRight").assertIsDisplayed()
        val nextSide = if (mouse) "left" else "right"
        beginDrag(gamma, compose.onNodeWithTag("split-drop-$nextSide").fetchSemanticsNode().boundsInRoot.center, mouse)
        finishDrag(gamma, mouse)
        val finalLeft = if (mouse) gamma else beta
        val finalRight = if (mouse) beta else gamma
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.tab(finalLeft)?.splitRightTabId == finalRight } }
        compose.onNodeWithTag("browser-pane-left-$finalLeft").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$finalRight").assertIsDisplayed()
        val beforeGroup = signature()
        // A grouped icon is a whole-pair drag and cannot re-split over a browser pane.
        beginDrag(finalLeft, compose.onNodeWithTag("split-drop-left").fetchSemanticsNode().boundsInRoot.center, mouse)
        finishDrag(finalLeft, mouse, cancel = true)
        assertEquals(beforeGroup, signature())
        runBlocking { container.tabs.togglePin(alpha) }
        beginDrag(finalLeft, tile(alpha).fetchSemanticsNode().boundsInRoot.let { Offset(it.center.x, it.top + 2f) }, mouse)
        compose.onNodeWithTag("collapsed-tab-insertion-$alpha").assertIsDisplayed()
        compose.onNodeWithTag("collapsed-tab-split-drop-$alpha").assertDoesNotExist()
        finishDrag(finalLeft, mouse)
        compose.waitUntil(5_000) { runBlocking { container.workspace.dao.tab(finalLeft)!!.isPinned && container.workspace.dao.tab(finalRight)!!.isPinned } }
        assertEquals(finalRight, runBlocking { container.workspace.dao.tab(finalLeft)!!.splitRightTabId })
        capture("${mode.name.lowercase()}-split-pair")
        val beforeResize = signature()
        beginDrag(alpha, compose.onNodeWithTag("split-drop-right").fetchSemanticsNode().boundsInRoot.center, mouse)
        resize(411)
        if (mouse) {
            compose.onRoot().performMouseInput { cancel() }
            compose.runOnIdle { cancelMouseDragTouchStream(compose.activity) }
        } else compose.onRoot().performTouchInput { cancel() }
        compose.onNodeWithTag("tab-drag-preview").assertDoesNotExist()
        assertEquals(beforeResize, signature())
        resize(1000)
        assertEquals("\"alpha:未送信 draft\"", js(originalPage, "window.token+':'+document.getElementById('draft').value"))
        assertEquals(1, server.requests.count { it.path == "/collapsed-alpha" })
        assertEquals(finalRight, runBlocking { container.workspace.dao.tab(finalLeft)!!.splitRightTabId })
    }
}
