package com.takeruf.nagi

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.InputDevice
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.room.withTransaction
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Drives the Activity back dispatcher, then checks actual native/Compose pixels and history. */
class PredictiveHistoryBackUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var targetId: String
    private val density get() = compose.activity.resources.displayMetrics.density
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun page(color: String) = """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>History preview $color</title><style>html,body{margin:0;min-height:100%;background:$color}textarea{margin:24px}</style>
        <textarea id="draft">Unsent</textarea>"""

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        server = FixtureServer(pages = mapOf("/old" to page("#e65a45"), "/current" to page("#2879b2"), "/other" to page("#4b9860")))
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false) }
            container.spaces.create("Back preview QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
    }

    @After fun cleanup() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            container.workspace.dao.observeHistory().first().filter { it.url.startsWith(server.origin) }
                .forEach { container.library.deleteHistory(it.id) }
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        shell("wm size $originalSize")
        server.close()
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
    private fun js(view: WebView, code: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun ready(view: WebView, path: String): Boolean {
        var ready = false
        compose.runOnIdle { ready = view.url == server.origin + path && view.progress == 100 }
        return ready && js(view, "document.readyState") == "\"complete\""
    }
    private fun visualReady(view: WebView) {
        val painted = CompletableDeferred<Unit>()
        compose.runOnIdle { view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { painted.complete(Unit) }
        }) }
        runBlocking { withTimeout(5_000) { painted.await() } }
        SystemClock.sleep(100)
    }
    private fun capture(name: String): Bitmap {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "history-preview-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }
    private fun event(progress: Float, edge: Int) = BackEventCompat(200f * progress, 500f, progress, edge)
    private fun begin(edge: Int) {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(event(0f, edge)) }
        compose.waitForIdle()
    }
    private fun move(progress: Float, edge: Int) {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(event(progress, edge)) }
        compose.waitForIdle()
    }

    @Test fun phoneLeftEdgeFollowsDistanceAndCancelsWithoutNavigating() = exercise(411, ThemeMode.LIGHT, false, BackEventCompat.EDGE_LEFT)
    @Test fun tabletRightEdgeOnlyMovesFocusedSplitPane() = exercise(1000, ThemeMode.DARK, true, BackEventCompat.EDGE_RIGHT)

    @Test fun phoneTabClosurePreviewsItsLiveOpenerAndCancelsWithoutLosingDrafts() = closeTabPreview(411, false, false)
    @Test fun tabletSplitTabClosurePreviewsItsPeerAndKeepsTheOtherPaneLive() = closeTabPreview(1000, true, false)
    @Test fun phoneTabClosureCanPreviewTheNewTabScreen() = closeTabPreview(411, false, true)

    private fun closeTabPreview(width: Int, split: Boolean, newTab: Boolean) {
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        val parentId = runBlocking {
            container.settings.update { it.copy(theme = if (split) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.tabs.create(spaceId, if (newTab) "about:blank" else server.origin + "/old")
        }
        val parent = if (newTab) null else {
            compose.waitUntil(20_000) { views().any { ready(it, "/old") } }
            views().single { ready(it, "/old") }.also {
                visualReady(it)
                js(it, "document.querySelector('#draft').value='Opener 日本語'; true")
            }
        }
        runBlocking {
            targetId = container.tabs.create(spaceId, server.origin + "/current", select = !split, parentTabId = parentId)
            if (split) container.tabs.pair(parentId, targetId)
        }
        compose.waitUntil(20_000) { views().any { ready(it, "/current") } }
        val current = views().single { ready(it, "/current") }
        compose.onNodeWithTag("browser-content-$targetId").performTouchInput { click(center) }
        visualReady(current)
        js(current, "window.previewToken='retained'; document.querySelector('#draft').value='Child 日本語'; true")
        compose.runOnIdle { assertFalse("This gesture must close a tab, not traverse page history", current.canGoBack()) }
        val requests = server.requests.count { it.path in listOf("/old", "/current") }
        val position = IntArray(2)
        compose.runOnIdle { current.getLocationOnScreen(position) }
        val x = position[0] + (current.width * 0.25f).toInt()
        val y = position[1] + (current.height * 0.7f).toInt()
        val original = capture("close-$width-$newTab-before")
        begin(BackEventCompat.EDGE_LEFT)
        move(0.5f, BackEventCompat.EDGE_LEFT)
        compose.onNodeWithTag("history-back-preview").assertExists()
        val halfway = capture("close-$width-$newTab-halfway")
        val pixel = halfway.getPixel(x, y)
        if (!newTab) assertTrue("The opener's red page must be visible", Color.red(pixel) > Color.blue(pixel) * 1.5f)
        else assertNotEquals("The new-tab surface must replace the current blue page", original.getPixel(x, y), pixel)
        original.recycle(); halfway.recycle()
        assertNull(runBlocking { container.workspace.dao.tab(targetId)!!.closedAt })
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertTrue(views().any { it === current })
        assertEquals("\"retained:Child 日本語\"", js(current, "window.previewToken+':'+document.querySelector('#draft').value"))
        assertEquals(requests, server.requests.count { it.path in listOf("/old", "/current") })
        begin(BackEventCompat.EDGE_LEFT)
        move(0.5f, BackEventCompat.EDGE_LEFT)
        val releaseTransaction = CompletableDeferred<Unit>()
        val transactionReady = CompletableDeferred<Unit>()
        val transactionScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
        val transaction = transactionScope.launch {
            container.database.withTransaction { transactionReady.complete(Unit); releaseTransaction.await() }
        }
        runBlocking { withTimeout(5_000) { transactionReady.await() } }
        try {
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            // The destination must stay over the source while Room commits the close.
            compose.onNodeWithTag("history-back-preview").assertExists()
        } finally {
            releaseTransaction.complete(Unit)
            runBlocking { withTimeout(5_000) { transaction.join() } }
            transactionScope.cancel()
        }
        compose.waitUntil(15_000) { runBlocking {
            container.workspace.dao.tab(targetId)!!.closedAt != null &&
                container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == parentId
        } }
        compose.waitForIdle()
        compose.onNodeWithTag("history-back-preview").assertDoesNotExist()
        if (parent != null) {
            assertTrue(views().any { it === parent })
            assertEquals("\"Opener 日本語\"", js(parent, "document.querySelector('#draft').value"))
        }
        if (split) assertNull(runBlocking { container.workspace.dao.tab(parentId)!!.splitRightTabId })
        assertEquals(requests, server.requests.count { it.path in listOf("/old", "/current") })
        capture("close-$width-$newTab-committed").recycle()
    }

    @Test fun systemEdgeGestureShowsPreviewAndCanCancelOrCommit() {
        Assume.assumeTrue("Requires Android gesture navigation", shell("settings get secure navigation_mode").trim() == "2")
        shell("wm size ${(411 * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { compose.activity.resources.configuration.screenWidthDp < 600 }
        runBlocking { targetId = container.tabs.create(spaceId, server.origin + "/old") }
        compose.waitUntil(20_000) { views().any { ready(it, "/old") } }
        val view = views().single { ready(it, "/old") }
        compose.onNodeWithTag("browser-content-$targetId").performTouchInput { click(center) }
        visualReady(view)
        js(view, "location.href='${server.origin}/current'")
        compose.waitUntil(15_000) { ready(view, "/current") }
        visualReady(view)
        val screenWidth = compose.activity.resources.displayMetrics.widthPixels.toFloat()
        compose.runOnIdle { assertTrue("Fixture must have traversable native history", view.canGoBack()) }
        val y = compose.activity.resources.displayMetrics.heightPixels * 0.6f
        var downTime = 0L
        fun inject(action: Int, x: Float) {
            if (action == MotionEvent.ACTION_DOWN) downTime = SystemClock.uptimeMillis()
            val motion = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            motion.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(motion, true)) }
            finally { motion.recycle() }
        }
        fun drag() {
            inject(MotionEvent.ACTION_DOWN, 1f)
            repeat(12) { i ->
                inject(MotionEvent.ACTION_MOVE, 1f + screenWidth * 0.65f * (i + 1) / 12f)
                SystemClock.sleep(16)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("history-back-preview").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(ready(view, "/current"))
        }
        drag()
        capture("system-edge-progress").recycle()
        // Drag back to the edge before releasing to let Android cancel the gesture.
        repeat(12) { i -> inject(MotionEvent.ACTION_MOVE, 1f + screenWidth * 0.65f * (11 - i) / 12f); SystemClock.sleep(16) }
        inject(MotionEvent.ACTION_UP, 1f)
        compose.waitForIdle()
        compose.onNodeWithTag("history-back-preview").assertDoesNotExist()
        assertTrue(ready(view, "/current"))
        drag()
        // End with an unambiguously outward stroke, after the idle/JS observations.
        repeat(5) { i -> inject(MotionEvent.ACTION_MOVE, 1f + screenWidth * (0.65f + 0.04f * (i + 1))); SystemClock.sleep(16) }
        inject(MotionEvent.ACTION_UP, 1f + screenWidth * 0.85f)
        try { compose.waitUntil(15_000) { ready(view, "/old") } }
        catch (failure: Throwable) {
            capture("system-edge-failure").recycle()
            var native = ""
            compose.runOnIdle { native = "url=${view.url}, progress=${view.progress}, back=${view.canGoBack()}, forward=${view.canGoForward()}" }
            throw AssertionError("System back did not commit: $native", failure)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("history-back-preview").assertDoesNotExist()
    }

    private fun exercise(width: Int, theme: ThemeMode, split: Boolean, edge: Int) {
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking {
            container.settings.update { it.copy(theme = theme, bottomSearchBar = !split) }
            val left = container.tabs.create(spaceId, server.origin + if (split) "/other" else "/old")
            targetId = if (split) container.tabs.create(spaceId, server.origin + "/old", select = false).also {
                container.tabs.pair(left, it)
            } else left
        }
        compose.waitUntil(20_000) { views().size == (if (split) 2 else 1) && views().any { ready(it, "/old") } }
        val view = views().single { ready(it, "/old") }
        val other = views().firstOrNull { it !== view }
        compose.onNodeWithTag("browser-content-$targetId").performTouchInput { click(center) }
        visualReady(view)
        js(view, "location.href='${server.origin}/current'")
        compose.waitUntil(15_000) { ready(view, "/current") }
        visualReady(view)
        js(view, "window.previewToken='retained'; document.querySelector('#draft').value='Unsent 日本語'; true")
        val requests = server.requests.size
        val location = IntArray(2)
        var paneWidth = 0
        var paneHeight = 0
        compose.runOnIdle { view.getLocationOnScreen(location); paneWidth = view.width; paneHeight = view.height }
        val sampleX = location[0] + (paneWidth * (if (edge == BackEventCompat.EDGE_LEFT) 0.3f else 0.7f)).toInt()
        val sampleY = location[1] + (paneHeight * 0.7f).toInt()
        begin(edge)
        move(0.15f, edge)
        val early = capture("$width-15")
        assertTrue("Current blue page should still cover this pixel", Color.blue(early.getPixel(sampleX, sampleY)) > Color.red(early.getPixel(sampleX, sampleY)))
        early.recycle()
        move(0.45f, edge)
        val later = capture("$width-45")
        assertTrue("Old red document should be revealed at the same pixel", Color.red(later.getPixel(sampleX, sampleY)) > Color.blue(later.getPixel(sampleX, sampleY)) * 1.5f)
        if (other != null) {
            val position = IntArray(2)
            compose.runOnIdle { other.getLocationOnScreen(position) }
            val pixel = later.getPixel(position[0] + other.width / 2, position[1] + (other.height * 0.7f).toInt())
            assertTrue("Unfocused Split pane must stay green", Color.green(pixel) > Color.red(pixel))
        }
        later.recycle()
        assertTrue(ready(view, "/current"))
        assertEquals(requests, server.requests.size)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.onNodeWithTag("history-back-preview").assertDoesNotExist()
        assertTrue(views().any { it === view })
        assertEquals("\"retained:Unsent 日本語\"", js(view, "window.previewToken+':'+document.querySelector('#draft').value"))
        assertEquals(requests, server.requests.size)
        capture("$width-cancelled").recycle()
        // A second gesture may start before the cancelled gesture's settling animation ends.
        begin(edge)
        move(0.3f, edge)
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(event(0f, edge))
        }
        compose.waitForIdle()
        move(0.45f, edge)
        compose.onNodeWithTag("history-back-preview").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertTrue(ready(view, "/current"))
        begin(edge)
        move(0.4f, edge)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15_000) { ready(view, "/old") }
        compose.waitForIdle()
        compose.onNodeWithTag("history-back-preview").assertDoesNotExist()
        assertTrue(views().any { it === view })
        compose.runOnIdle { assertTrue("Native forward history must remain available", view.canGoForward()) }
        if (other != null) assertTrue(ready(other, "/other"))
        capture("$width-committed").recycle()
    }
}
