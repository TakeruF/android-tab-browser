package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlin.math.abs

/** Real toolbar placement, keyboard clearance, pane targeting and live-page retention. */
@SdkSuppress(minSdkVersion = 29)
class CommandBarDropdownUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun resize(width: Int) {
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        compose.waitForIdle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        server = FixtureServer()
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Dropdown QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/ua-dropdown-left")
            rightId = container.tabs.create(spaceId, "${server.origin}/ua-dropdown-right", select = false)
        }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        server.close()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private fun webViews(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown && it.width > 0 } }
        return result
    }

    private fun js(web: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "dropdown-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun open(tabId: String) {
        compose.onNodeWithTag("url-drop-$tabId").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun close() {
        compose.onNodeWithTag("command-close").performClick()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
        }
    }

    private fun assertAtToolbar(toolbar: Rect) {
        val row = bounds("command-input-row")
        val panel = bounds("command-panel")
        assertEquals("Input stays at the address field", toolbar.top, row.top, density)
        assertEquals("Input retains the address field height", toolbar.height, row.height, density)
        assertEquals("Dropdown retains the address field's left edge", toolbar.left, panel.left, density)
        assertEquals("Dropdown retains the address field's right edge", toolbar.right, panel.right, density)
        assertTrue("Suggestions follow the input", bounds("command-suggestions").top >= row.bottom)
    }

    private fun assertPhoneScreen(hasSuggestions: Boolean = true) {
        val screen = bounds("command-phone-screen")
        val row = bounds("command-input-row")
        val panel = bounds("command-panel")
        assertEquals("The phone panel fills the safe viewport", screen.left, panel.left, density)
        assertEquals(screen.top, panel.top, density)
        assertEquals(screen.right, panel.right, density)
        assertEquals(screen.bottom, panel.bottom, density)
        assertEquals("Phone input starts at the top", screen.top + 4 * density, row.top, density)
        assertEquals(screen.left + 8 * density, row.left, density)
        assertEquals(screen.right - 8 * density, row.right, density)
        assertEquals("Touch input keeps its 48dp height", 48 * density, row.height, density)
        if (hasSuggestions) assertTrue("Phone results follow the input", bounds("command-suggestions").top >= row.bottom)
    }

    @Test fun phoneLightDropdownStaysAtTopAsResultsChange() = exerciseTop(411, ThemeMode.LIGHT)
    @Test fun tabletLightDropdownTargetsEitherSplitPane() = exerciseTop(1000, ThemeMode.LIGHT)
    @Test fun tabletDarkDropdownTargetsEitherSplitPane() = exerciseTop(1000, ThemeMode.DARK)

    @Test fun phoneNewTabSearchTravelsFromTheHomeFieldAndRetainsTheOtherLivePage() {
        resize(411)
        compose.waitUntil(20_000) { webViews().singleOrNull()?.let { js(it, "!!document.getElementById('draft')") == "true" } == true }
        val live = webViews().single()
        js(live, "window.homeSearchToken='retained';document.getElementById('draft').value='未送信 日本語';true")
        val home = runBlocking { container.tabs.create(spaceId, "about:blank") }
        compose.onNodeWithTag("new-tab-search").assertIsDisplayed()
        val source = bounds("new-tab-search")
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("new-tab-search").performClick()
            repeat(2) { compose.mainClock.advanceTimeByFrame() }
            compose.waitForIdle()
            val movingTop = bounds("command-input-row").top
            assertTrue("The home search starts below its final toolbar location", source.top > 150 * density)
            compose.mainClock.advanceTimeBy(320)
            compose.waitForIdle()
            assertPhoneScreen()
            assertTrue("The actual tapped home field supplies the motion origin", movingTop > bounds("command-input-row").top + 20 * density)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.onNodeWithTag("command-input").performTextReplacement("未送信 draft")
        capture("411-home-search-light")
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK) } }
        compose.onNodeWithTag("command-input").assertTextEquals("未送信 draft").assertIsFocused()
        capture("411-home-search-dark")
        close()
        assertEquals(home, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
        runBlocking { container.tabs.select(leftId) }
        compose.waitUntil(10_000) { webViews().any { it === live } }
        assertEquals("\"retained\"", js(live, "window.homeSearchToken"))
        assertEquals("\"未送信 日本語\"", js(live, "document.getElementById('draft').value"))
        assertEquals(1, server.requests.count { it.path == "/ua-dropdown-left" })
    }

    private fun exerciseTop(width: Int, theme: ThemeMode) {
        resize(width)
        runBlocking {
            container.settings.update { it.copy(theme = theme) }
            if (width >= 600) container.tabs.pair(leftId, rightId)
        }
        compose.waitUntil(20_000) {
            val views = webViews()
            views.size == (if (width >= 600) 2 else 1) && views.all { js(it, "!!document.getElementById('draft')") == "true" }
        }
        val live = webViews()
        live.forEach { js(it, "window.dropdownToken='retained';document.getElementById('draft').value='未送信 日本語';true") }
        for (tabId in if (width >= 600) listOf(rightId, leftId) else listOf(leftId)) {
            val toolbar = bounds("url-drop-$tabId")
            open(tabId)
            // Clicking the inactive Split pane must select that pane's complete URL.
            compose.onNodeWithTag("command-input").assertTextEquals("${server.origin}/ua-dropdown-${if (tabId == leftId) "left" else "right"}")
            compose.onNodeWithTag("command-input").performTextReplacement("browser")
            if (width < 600) assertPhoneScreen() else assertAtToolbar(toolbar)
            capture("$width-$theme-${if (tabId == leftId) "left" else "right"}")
            compose.onNodeWithTag("command-input").performTextReplacement(">no-such-command")
            compose.onNodeWithTag("command-suggestions").assertDoesNotExist()
            if (width < 600) assertPhoneScreen(hasSuggestions = false)
            else {
                assertEquals(toolbar.top, bounds("command-input-row").top, 2 * density)
                assertEquals(toolbar.height, bounds("command-input-row").height, density)
            }
            close()
        }
        assertTrue(live.all { old -> webViews().any { it === old } })
        live.forEach {
            assertEquals("\"retained\"", js(it, "window.dropdownToken"))
            assertEquals("\"未送信 日本語\"", js(it, "document.getElementById('draft').value"))
        }
        // A shortcut uses the same toolbar, and resizing an open editor retains its text.
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0,
            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON)) }
        compose.onNodeWithTag("command-input").performTextReplacement("日本語 draft")
        resize(if (width < 600) 1000 else 411)
        compose.onNodeWithTag("command-input").assertTextEquals("日本語 draft")
        if (width < 600) assertAtToolbar(bounds("url-drop-$leftId")) else assertPhoneScreen()
        capture("${if (width < 600) 1000 else 411}-$theme-resized")
        close()
    }

    @Test fun phoneDarkBottomAddressOpensTopSearchScreenAboveSoftwareKeyboard() {
        resize(411)
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK, bottomSearchBar = true) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("url-drop-$leftId").fetchSemanticsNodes().isNotEmpty() }
        val restingAddress = bounds("url-drop-$leftId")
        open(leftId)
        compose.onNodeWithTag("command-input").performTextReplacement("browser")
        compose.onNodeWithTag("command-input").performClick()
        var keyboardTop = 0f
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                val window = WindowInspector.getGlobalWindowViews().firstOrNull { it.hasWindowFocus() }
                val ime = window?.let { ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom } ?: 0
                keyboardTop = if (ime > 100 * density) window!!.height - ime.toFloat() else 0f
            }
            keyboardTop > 0
        }
        assertPhoneScreen()
        assertTrue("The full search screen clears the keyboard", bounds("command-panel").bottom <= keyboardTop + 2 * density)
        assertTrue("The results stay above the keyboard", bounds("command-suggestions").bottom <= keyboardTop + 2 * density)
        capture("411-DARK-bottom-ime")
        close()
        assertTrue(runBlocking { container.settings.settings.first().bottomSearchBar })
        assertEquals("Closing search restores the resting bottom address field", restingAddress, bounds("url-drop-$leftId"))
    }
}
