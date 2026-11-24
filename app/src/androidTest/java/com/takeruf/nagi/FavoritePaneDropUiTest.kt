package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.data.room.entity
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class FavoritePaneDropUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String
    private val favoriteId = "pane-drop-favorite-${System.nanoTime()}"
    private val favoriteTitle = "Pane drop shortcut"

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
    }
    private fun views(): List<WebView> {
        fun collect(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = collect(compose.activity.window.decorView).filter { it.isShown } }
        return result
    }
    private fun js(view: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    // Ordinary page navigation enriches the icon asynchronously. Every persisted
    // shortcut field other than that cache reference must remain unchanged.
    private fun favorites() = runBlocking { container.workspace.dao.observeBookmarks().first()
        .filter { it.isFavorite }.map { it.copy(faviconUrl = null) } }
    private fun shortcut(key: Int, ctrl: Boolean = false) = compose.runOnIdle {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0,
            if (ctrl) KeyEvent.META_CTRL_ON else 0))
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        fun page(title: String) = "<!doctype html><title>$title</title><meta name='viewport' content='width=device-width'><textarea id='draft'>$title draft</textarea><script>window.keepToken='$title'</script>"
        server = FixtureServer(pages = mapOf("/favorite-pane-left" to page("Left"),
            "/favorite-pane-right" to page("Right"), "/favorite-pane-open" to page("Shortcut")))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false, sidebarCollapsed = false,
                sidebarWidth = 280f, compactUiMode = CompactUiMode.OFF) }
            container.spaces.create("Favorite pane QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, server.origin + "/favorite-pane-left")
            rightId = container.tabs.create(spaceId, server.origin + "/favorite-pane-right", select = false)
            container.workspace.dao.putBookmark(Bookmark(favoriteId, server.origin + "/favorite-pane-open",
                favoriteTitle, isFavorite = true, createdAt = Long.MIN_VALUE + 20).entity())
        }
        resize(1000)
    }
    @After fun cleanup() {
        compose.runOnIdle { cancelMouseDragTouchStream(compose.activity) }
        runBlocking {
            container.library.removeBookmark(favoriteId)
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }
    private fun awaitUrl(id: String, path: String) {
        try {
            compose.waitUntil(15_000) { runBlocking {
                container.workspace.dao.tab(id)?.url == server.origin + path
            } && views().any { js(it, "location.href") == "\"${server.origin + path}\"" &&
                js(it, "document.readyState") == "\"complete\"" } }
        } catch (failure: ComposeTimeoutException) {
            val tab = runBlocking { container.workspace.dao.tab(id) }
            val visible = views().map { js(it, "location.href") }
            val dragging = compose.onAllNodesWithTag("favorite-drag-preview").fetchSemanticsNodes().size
            throw AssertionError("Favorite pane navigation did not complete: tab=$tab, visible=$visible, dragPreviews=$dragging", failure)
        }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "favorite-pane-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun drag(point: Offset, mouse: Boolean = false, cancel: Boolean = false,
        captureName: String? = null, singlePaneId: String? = null) {
        val source = compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_favorite_1_s, favoriteTitle))
        val from = source.fetchSemanticsNode().boundsInRoot
        fun sourcePixel() = compose.onNodeWithTag("sidebar").let { sidebar ->
            val host = sidebar.fetchSemanticsNode().boundsInRoot
            val tile = source.fetchSemanticsNode().boundsInRoot
            sidebar.captureToImage().toPixelMap()[(tile.center.x - host.left).toInt(),
                (tile.bottom - host.top - 8f).toInt()]
        }
        val originalPixel = sourcePixel()
        val end = point - from.topLeft
        if (mouse) {
            source.performMouseInput { moveTo(center); press(); moveTo(center + Offset(8f, 0f), delayMillis = 100) }
        } else {
            source.performTouchInput { down(center); advanceEventTime(650); moveTo(center + Offset(8f, 0f)) }
        }
        compose.waitForIdle()
        if (mouse) source.performMouseInput {
            for (i in 1..16) moveTo(center + (end - center) * (i / 16f), delayMillis = 25)
        } else source.performTouchInput {
            for (i in 1..16) moveTo(center + (end - center) * (i / 16f), delayMillis = 25)
        }
        compose.waitForIdle()
        source.assertIsDisplayed()
        assertEquals("The original Favorite tile stays painted during a page drag", originalPixel, sourcePixel())
        compose.onNodeWithTag("favorite-pane-drop-center").assertDoesNotExist()
        if (singlePaneId != null) {
            val left = compose.onNodeWithTag("split-drop-left").fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("split-drop-right").fetchSemanticsNode().boundsInRoot
            val pane = compose.onNodeWithTag("browser-content-$singlePaneId").fetchSemanticsNode().boundsInRoot
            assertEquals(pane.width / 2f, left.width, 1f)
            assertEquals(pane.width / 2f, right.width, 1f)
            assertEquals(left.right, right.left, 1f)
        }
        if (captureName != null) capture(captureName)
        if (mouse) source.performMouseInput { if (cancel) cancel() else release() }
        else source.performTouchInput { if (cancel) cancel() else up() }
        if (mouse && cancel) compose.runOnIdle { cancelMouseDragTouchStream(compose.activity) }
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-drag-preview").assertDoesNotExist()
    }

    @Test fun touchDropsIntoBothUnfocusedSplitPanesKeepTheirPartnerAndShortcut() {
        runBlocking { container.tabs.pair(leftId, rightId); container.tabs.select(rightId) }
        awaitUrl(leftId, "/favorite-pane-left"); awaitUrl(rightId, "/favorite-pane-right")
        val left = views().first { js(it, "document.title") == "\"Left\"" }
        val right = views().first { js(it, "document.title") == "\"Right\"" }
        js(right, "document.getElementById('draft').value='Unsent right 日本語'; window.keepToken='right-live'")
        val saved = favorites()
        drag(compose.onNodeWithTag("browser-content-$leftId").fetchSemanticsNode().boundsInRoot.center)
        awaitUrl(leftId, "/favorite-pane-open")
        assertTrue(views().any { it === left }); assertTrue(views().any { it === right })
        assertEquals("\"Unsent right 日本語\"", js(right, "document.getElementById('draft').value"))
        assertEquals("\"right-live\"", js(right, "window.keepToken"))
        compose.onNodeWithTag("split-title-$leftId").assertIsSelected()
        compose.onNodeWithTag("split-title-$rightId").assertIsNotSelected()
        shortcut(KeyEvent.KEYCODE_L, ctrl = true)
        compose.onNodeWithTag("command-input").assertTextEquals(server.origin + "/favorite-pane-open")
        shortcut(KeyEvent.KEYCODE_ESCAPE)
        js(left, "document.getElementById('draft').value='Unsent left 日本語'; window.keepToken='left-live'")
        drag(compose.onNodeWithTag("browser-content-$rightId").fetchSemanticsNode().boundsInRoot.center)
        awaitUrl(rightId, "/favorite-pane-open")
        assertTrue(views().any { it === left }); assertTrue(views().any { it === right })
        assertEquals("\"Unsent left 日本語\"", js(left, "document.getElementById('draft').value"))
        assertEquals("\"left-live\"", js(left, "window.keepToken"))
        compose.onNodeWithTag("split-title-$rightId").assertIsSelected()
        assertEquals(saved, favorites())
        assertEquals(rightId, runBlocking { container.workspace.dao.tab(leftId)!!.splitRightTabId })
        assertEquals(1, server.requests.count { it.path == "/favorite-pane-left" })
        assertEquals(1, server.requests.count { it.path == "/favorite-pane-right" })
        capture("split-both-sides-light")
    }

    @Test fun mouseEqualHalvesCreateSplitAndKeepFavoriteVisible() = equalHalves(mouse = true)
    @Test fun touchEqualHalvesCreateSplitAndKeepFavoriteVisible() = equalHalves(mouse = false)
    private fun equalHalves(mouse: Boolean) {
        val screen = if (mouse) "mouse-dark" else "touch-light"
        runBlocking { container.settings.update { it.copy(theme = if (mouse) ThemeMode.DARK else ThemeMode.LIGHT) } }
        awaitUrl(leftId, "/favorite-pane-left")
        val saved = favorites()
        val initial = runBlocking { container.workspace.dao.tabs(spaceId).map { it.id } }
        val content = compose.onNodeWithTag("browser-content-$leftId").fetchSemanticsNode().boundsInRoot
        drag(content.center, mouse = mouse, cancel = true)
        assertEquals(initial, runBlocking { container.workspace.dao.tabs(spaceId).map { it.id } })
        assertEquals(server.origin + "/favorite-pane-left", runBlocking { container.workspace.dao.tab(leftId)!!.url })
        assertEquals(saved, favorites())
        for (toRight in listOf(false, true)) {
            val anchor = if (toRight) rightId else leftId
            runBlocking { container.tabs.detachSplit(leftId); container.tabs.select(anchor) }
            val pane = compose.onNodeWithTag("browser-content-$anchor").fetchSemanticsNode().boundsInRoot
            // These quarter/three-quarter points used to land in the unwanted central region.
            drag(Offset(pane.left + pane.width * (if (toRight) 0.75f else 0.25f), pane.center.y),
                mouse = mouse, captureName = "two-halves-${if (toRight) "right" else "left"}-$screen", singlePaneId = anchor)
            compose.waitUntil(10_000) { runBlocking {
                container.workspace.dao.tabs(spaceId).any { it.splitRightTabId != null &&
                    (it.id == anchor || it.splitRightTabId == anchor) }
            } }
            val pair = runBlocking { container.workspace.dao.tabs(spaceId).first { it.splitRightTabId != null &&
                (it.id == anchor || it.splitRightTabId == anchor) } }
            if (toRight) assertEquals(anchor, pair.id) else assertEquals(anchor, pair.splitRightTabId)
            val added = if (toRight) pair.splitRightTabId!! else pair.id
            awaitUrl(added, "/favorite-pane-open")
            assertEquals(saved, favorites())
            runBlocking { container.tabs.detachSplit(pair.id) }
        }
        capture("equal-halves-result-$screen")
        resize(599)
        compose.onNodeWithTag("split-drop-left").assertDoesNotExist()
        compose.onNodeWithTag("split-drop-right").assertDoesNotExist()
        assertEquals(saved, favorites())
    }
}
