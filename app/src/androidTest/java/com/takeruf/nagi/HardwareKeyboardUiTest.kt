package com.takeruf.nagi

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Delivers controlled hardware-keyboard configurations to the real app view tree. */
class HardwareKeyboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String
    private lateinit var server: FixtureServer
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun configureKeyboard(available: Boolean) {
        compose.runOnIdle {
            val config = Configuration(compose.activity.resources.configuration).apply {
                keyboard = if (available) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                hardKeyboardHidden = if (available) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
            }
            compose.activity.window.decorView.dispatchConfigurationChanged(config)
        }
        compose.waitForIdle()
    }
    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun visibleWebViews(): List<WebView> {
        var result = emptyList<WebView>()
        compose.runOnIdle { result = views(compose.activity.window.decorView).filter { it.isShown } }
        return result
    }
    private fun js(web: WebView, code: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { web.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "ui-density-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        val html = "<!doctype html><title>Density fixture</title><meta name='viewport' content='width=device-width'><textarea id='draft'>Unsent 日本語</textarea><script>window.densityToken='live'</script>"
        server = FixtureServer(pages = mapOf("/density-left" to html, "/density-right" to html))
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, bottomSearchBar = true) }
            container.spaces.create("Keyboard UI QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/density-left")
            rightId = container.tabs.create(spaceId, "${server.origin}/density-right", select = false)
            container.tabs.pair(leftId, rightId)
        }
    }
    @After fun cleanup() {
        configureKeyboard(false)
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            container.workspace.dao.observeHistory().first().filter { it.url.startsWith(server.origin) }
                .forEach { container.library.deleteHistory(it.id) }
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        shell("wm size $originalSize")
        server.close()
    }

    @Test fun tabletSettingsAndSubpagesKeepStateAcrossKeyboardChanges() = exercise(1000, ThemeMode.DARK)
    @Test fun phoneTabsSettingsAndHistoryUseKeyboardDensity() = exercise(411, ThemeMode.LIGHT)

    private fun exercise(width: Int, theme: ThemeMode) {
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(theme = theme) } }
        configureKeyboard(false)
        compose.waitUntil(20_000) {
            val pages = visibleWebViews()
            pages.size == (if (width >= 600) 2 else 1) && pages.all { js(it, "document.readyState") == "\"complete\"" }
        }
        val pages = visibleWebViews()
        val bar = compose.onNodeWithTag("browser-address-bar-$leftId")
        bar.assertHeightIsEqualTo(56.dp)
        if (width < 600) compose.onNodeWithTag("compact-tabs-button").performClick()
        val tab = compose.onNodeWithTag("sidebar-tab-$leftId")
        tab.assertHeightIsEqualTo(48.dp)
        configureKeyboard(true)
        tab.assertHeightIsEqualTo(40.dp)
        if (width < 600) {
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_close_1_s, compose.activity.getString(R.string.ui_tabs))).performClick()
        }
        bar.assertHeightIsEqualTo(48.dp)
        capture("$width-browser-keyboard")
        if (width < 600) compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_settings)).performClick()
        val general = compose.onNodeWithTag("settings-category-general")
        general.assertHeightIsEqualTo(40.dp)
        if (width >= 760) compose.onNodeWithTag("settings-categories").assertWidthIsEqualTo(236.dp)
        capture("$width-settings-keyboard")
        configureKeyboard(false)
        general.assertHeightIsEqualTo(56.dp)
        if (width >= 760) compose.onNodeWithTag("settings-categories").assertWidthIsEqualTo(236.dp)
        capture("$width-settings-touch")
        general.performClick()
        val toggle = compose.onNodeWithText(compose.activity.getString(R.string.ui_open_links_in_new_tab))
        toggle.assertHeightIsEqualTo(64.dp).assertIsOff().performClick()
        configureKeyboard(true)
        toggle.assertHeightIsEqualTo(48.dp).assertIsOn()
        assertTrue(runBlocking { container.settings.settings.first().openLinksInNewTab })

        fun category(tag: String) {
            if (width < 760) compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back_to_settings)).performClick()
            compose.onNodeWithTag("settings-category-$tag").performClick()
        }
        category("search")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        val list = compose.onNodeWithTag("search-engines-list")
        list.performScrollToNode(hasTestTag("search-engine-row:google"))
        val engineRow = compose.onNodeWithTag("search-engine-row:google")
        val denseHeight = engineRow.fetchSemanticsNode().boundsInRoot.height
        configureKeyboard(false)
        list.performScrollToNode(hasTestTag("search-engine-row:google"))
        assertTrue(engineRow.fetchSemanticsNode().boundsInRoot.height > denseHeight)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_edit_1_s, "Google")).performClick()
        val name = compose.onNode(hasSetTextAction() and hasText(compose.activity.getString(R.string.ui_name)))
        name.performTextReplacement("日本語 draft")
        configureKeyboard(true)
        name.assertTextContains("日本語 draft").assertIsFocused()
        configureKeyboard(false)
        name.assertTextContains("日本語 draft").assertIsFocused()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).performClick()
        configureKeyboard(true)
        capture("$width-search-keyboard")

        repeat(3) {
            val back = compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_back_to_browser))
            if (back.fetchSemanticsNodes().isNotEmpty()) back.onFirst().performClick()
            else if (compose.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back_to_settings)).performClick()
        }
        compose.onNodeWithTag("browser-address-bar-$leftId").assertIsDisplayed()
        val returned = visibleWebViews()
        assertEquals(pages.size, returned.size)
        pages.forEach { web ->
            assertTrue(returned.any { it === web })
            assertEquals("\"live\"", js(web, "window.densityToken"))
            assertEquals("\"Unsent 日本語\"", js(web, "document.getElementById('draft').value"))
        }
        assertEquals(1, server.requests.count { it.path == "/density-left" })
        if (width >= 600) assertEquals(1, server.requests.count { it.path == "/density-right" })
        if (width < 600) compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_history)).performClick()
        val historyRow = compose.onNodeWithTag("library-row:${server.origin}/density-left")
        val historyHeight = historyRow.fetchSemanticsNode().boundsInRoot.height
        val query = compose.onNode(hasSetTextAction())
        query.performTextReplacement("Density")
        configureKeyboard(false)
        query.assertTextContains("Density")
        assertTrue(historyRow.fetchSemanticsNode().boundsInRoot.height > historyHeight)
        configureKeyboard(true)
        query.assertTextContains("Density")
        capture("$width-history-keyboard")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back_to_browser)).performClick()
        runBlocking { container.tabs.create(spaceId) }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("new-tab-page").fetchSemanticsNodes().isNotEmpty() }
        val shortcut = compose.onNodeWithTag("new-tab-shortcut-$leftId").assertIsDisplayed()
        val shortcutHeight = shortcut.fetchSemanticsNode().boundsInRoot.height
        capture("$width-new-tab-keyboard")
        configureKeyboard(false)
        assertTrue(shortcut.fetchSemanticsNode().boundsInRoot.height > shortcutHeight)
        configureKeyboard(true)
        shortcut.performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$leftId").fetchSemanticsNodes().isNotEmpty()
        }
        pages.forEach { web ->
            assertTrue(visibleWebViews().any { it === web })
            assertEquals("\"live\"", js(web, "window.densityToken"))
        }
    }
}
