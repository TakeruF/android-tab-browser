package com.takeruf.nagi

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.CompactUiMode
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Settings choices override hardware availability without discarding live page drafts. */
class CompactUiSettingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var server: FixtureServer

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun configureKeyboard(available: Boolean) {
        compose.runOnIdle {
            val configuration = Configuration(compose.activity.resources.configuration).apply {
                keyboard = if (available) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                hardKeyboardHidden = if (available) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
            }
            compose.activity.window.decorView.dispatchConfigurationChanged(configuration)
        }
        compose.waitForIdle()
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

    private fun js(web: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        val html = "<!doctype html><title>Compact interface fixture</title><meta name='viewport' content='width=device-width'><textarea id='draft'>Unsent 日本語</textarea><script>window.compactToken='live'</script>"
        server = FixtureServer(pages = mapOf("/compact-left" to html, "/compact-right" to html))
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, bottomSearchBar = true) }
            container.spaces.create("Compact setting QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            val left = container.tabs.create(spaceId, "${server.origin}/compact-left")
            val right = container.tabs.create(spaceId, "${server.origin}/compact-right", select = false)
            container.tabs.pair(left, right)
        }
    }

    @After fun cleanup() {
        configureKeyboard(false)
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::server.isInitialized) container.workspace.dao.observeHistory().first()
                .filter { it.url.startsWith(server.origin) }.forEach { container.library.deleteHistory(it.id) }
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    private fun openAppearance() {
        if (compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_settings)).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_settings)).performClick()
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("compact-ui-setting"))
    }

    private fun choose(mode: CompactUiMode) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("compact-ui-setting-select"))
        compose.onNodeWithTag("compact-ui-setting-select").performClick()
        CompactUiMode.entries.forEach { option ->
            val item = compose.onNodeWithTag("compact-ui-setting-${option.name.lowercase()}").assertIsDisplayed()
            if (runBlocking { container.settings.settings.first().compactUiMode } == option) item.assertIsSelected()
            else item.assertIsNotSelected()
        }
        compose.onNodeWithTag("compact-ui-setting-${mode.name.lowercase()}").performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().compactUiMode == mode } }
        assertCompactLabel(mode)
    }

    private fun assertCompactLabel(mode: CompactUiMode) {
        val resource = when (mode) {
            CompactUiMode.OFF -> R.string.ui_compact_display_off
            CompactUiMode.AUTO -> R.string.ui_compact_display_auto
            CompactUiMode.ON -> R.string.ui_compact_display_on
        }
        compose.onNodeWithTag("compact-ui-setting-select").assertTextContains(compose.activity.getString(resource))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, compose.activity.getString(resource)))
    }

    private fun chooseTheme(mode: ThemeMode) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-theme-select"))
        compose.onNodeWithTag("settings-theme-select").performClick()
        ThemeMode.entries.forEach { option ->
            val item = compose.onNodeWithTag("settings-theme-${option.name.lowercase()}").assertIsDisplayed()
            if (runBlocking { container.settings.settings.first().theme } == option) item.assertIsSelected()
            else item.assertIsNotSelected()
        }
        compose.onNodeWithTag("settings-theme-${mode.name.lowercase()}").performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().theme == mode } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "compact-ui-setting-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun tabletDarkChoicesOverrideHardwareAndRetainLiveSplit() = exercise(1000, ThemeMode.DARK)
    @Test fun phoneLightChoicesOverrideHardwareAndRetainLivePage() = exercise(411, ThemeMode.LIGHT)

    private fun exercise(width: Int, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(theme = theme) } }
        configureKeyboard(false)
        compose.waitUntil(20_000) {
            val views = pages()
            views.size == (if (width >= 600) 2 else 1) && views.all { js(it, "document.readyState") == "\"complete\"" }
        }
        val originalPages = pages()
        openAppearance()
        assertCompactLabel(CompactUiMode.AUTO)
        val standardHeight = compose.onNodeWithTag("settings-bottom-search-bar").fetchSemanticsNode().boundsInRoot.height
        fun assertCompact(compact: Boolean) {
            val height = compose.onNodeWithTag("settings-bottom-search-bar").fetchSemanticsNode().boundsInRoot.height
            if (compact) assertTrue("Compact controls use less vertical spacing", height < standardHeight - 4 * density)
            else assertEquals("Standard spacing must ignore an attached keyboard in OFF mode", standardHeight, height, 2f)
            if (width >= 760) {
                compose.onNodeWithTag("settings-categories").assertWidthIsEqualTo(236.dp)
                compose.onNodeWithTag("settings-category-appearance").assertHeightIsEqualTo(if (compact) 40.dp else 56.dp)
            }
        }

        configureKeyboard(true)
        assertCompactLabel(CompactUiMode.AUTO)
        assertCompact(true)
        configureKeyboard(false)
        assertCompact(false)
        configureKeyboard(true)
        choose(CompactUiMode.OFF)
        assertCompact(false)
        capture("$width-off-keyboard")
        choose(CompactUiMode.ON)
        configureKeyboard(false)
        assertCompact(true)
        capture("$width-on-touch")
        // The automatic option remains selectable after manual overrides.
        choose(CompactUiMode.AUTO)
        assertCompact(false)
        configureKeyboard(true)
        assertCompact(true)
        compose.onNodeWithTag("compact-ui-setting-select").performClick()
        capture("$width-compact-menu")
        compose.onNodeWithTag("compact-ui-setting-on").performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().compactUiMode == CompactUiMode.ON } }
        assertCompact(true)
        chooseTheme(ThemeMode.SYSTEM)
        chooseTheme(ThemeMode.LIGHT)
        chooseTheme(ThemeMode.DARK)
        chooseTheme(theme)
        compose.onNodeWithTag("settings-theme-select").performClick()
        capture("$width-theme-menu")
        compose.onNodeWithTag("settings-theme-${theme.name.lowercase()}").performClick()
        compose.returnToBrowserFromSettings()

        val returned = pages()
        assertEquals(originalPages.size, returned.size)
        originalPages.forEach { page ->
            assertTrue("The same live WebView must return", returned.any { it === page })
            assertEquals("\"live\"", js(page, "window.compactToken"))
            assertEquals("\"Unsent 日本語\"", js(page, "document.getElementById('draft').value"))
        }
        assertEquals(1, server.requests.count { it.path == "/compact-left" })
        if (width >= 600) assertEquals(1, server.requests.count { it.path == "/compact-right" })
        openAppearance()
        assertCompactLabel(CompactUiMode.ON)
        assertEquals(CompactUiMode.ON, runBlocking { container.settings.settings.first().compactUiMode })
        assertEquals(theme, runBlocking { container.settings.settings.first().theme })
        capture("$width-restored-choice")
        choose(CompactUiMode.OFF)
        configureKeyboard(true)
        assertCompactLabel(CompactUiMode.OFF)
        assertCompact(false)
    }
}
