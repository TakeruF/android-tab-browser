package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 29)
class CommandBarLayoutUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var server: FixtureServer
    private lateinit var leftId: String
    private lateinit var rightId: String

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun shortcut(key: Int) = compose.runOnIdle {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP))
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, action, key, 0, KeyEvent.META_CTRL_ON))
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
        val html = "<!doctype html><title>Layout fixture</title><meta name='viewport' content='width=device-width'><textarea id='draft'>未送信 日本語</textarea><script>window.layoutToken='live'</script>"
        server = FixtureServer(pages = mapOf("/layout-left" to html, "/layout-right" to html))
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Command layout QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/layout-left")
            rightId = container.tabs.create(spaceId, "${server.origin}/layout-right", select = false)
        }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::server.isInitialized) container.workspace.dao.observeHistory().first()
                .filter { it.url.startsWith(server.origin) }.forEach { container.library.deleteHistory(it.id) }
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "command-layout-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun open() {
        shortcut(KeyEvent.KEYCODE_L)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun close() {
        compose.onNodeWithTag("command-input").performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithTag("command-panel").assertDoesNotExist()
    }

    private fun choose(layout: CommandBarLayout) {
        val toggle = compose.onNodeWithTag("command-bar-layout-setting")
        val current = runBlocking { container.settings.settings.first().commandBarLayout }
        if (current != layout) toggle.performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().commandBarLayout == layout } }
        if (layout == CommandBarLayout.ANCHORED) toggle.assertIsOn() else toggle.assertIsOff()
    }

    @Test fun phoneLightFooterSwitchesTheBottomEditorAndSavesTheChoice() = exercise(411, ThemeMode.LIGHT)
    @Test fun tabletDarkFooterAndSettingsRetainLiveSplit() = exercise(1000, ThemeMode.DARK)

    private fun exercise(width: Int, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking {
            container.settings.update { it.copy(theme = theme, bottomSearchBar = width < 600) }
            if (width >= 600) container.tabs.pair(leftId, rightId)
        }
        compose.waitUntil(20_000) {
            val live = pages()
            live.size == (if (width >= 600) 2 else 1) && live.all { js(it, "document.readyState") == "\"complete\"" }
        }
        val live = pages()
        open()
        val input = compose.onNodeWithTag("command-input")
        input.performTextReplacement("日本語 draft")
        input.performTextInputSelection(TextRange(0, 2))
        assertEquals(TextRange(0, 2), input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        input.performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsSelected()
        compose.onNodeWithTag("command-use-centered").assertIsDisplayed()
        capture("$width-anchored-footer")
        var selectionBeforeSwitch = input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        if (width >= 600) {
            instrumentation.setInTouchMode(false)
            repeat(20) {
                if (compose.onAllNodes(hasTestTag("command-use-centered") and isFocused()).fetchSemanticsNodes().isEmpty())
                    input.performKeyInput { pressKey(Key.Tab) }
            }
            compose.onNodeWithTag("command-use-centered").assertIsFocused()
            selectionBeforeSwitch = input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
            compose.onNodeWithTag("command-use-centered").performKeyInput { pressKey(Key.Enter) }
        } else compose.onNodeWithTag("command-use-centered").performTouchInput { click() }
        compose.waitUntil(5_000) {
            runBlocking { container.settings.settings.first().commandBarLayout == CommandBarLayout.CENTERED } &&
                compose.onAllNodesWithTag("command-use-centered").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        input.assertTextEquals("日本語 draft").assertIsFocused()
        assertEquals("Switch must retain the selection at activation", selectionBeforeSwitch,
            input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsSelected()
        compose.onNodeWithTag("command-input-row").assertHeightIsEqualTo(80.dp)
        val panel = compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow
        assertEquals(compose.activity.window.decorView.width / 2f, panel.center.x, 2 * density)
        if (width >= 600) compose.onNodeWithTag("command-panel").assertWidthIsEqualTo(720.dp)
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews().mapNotNull { it.layoutParams as? WindowManager.LayoutParams }
                .filter { it.type == WindowManager.LayoutParams.TYPE_APPLICATION }
            assertTrue(windows.isNotEmpty())
            assertTrue(windows.all { it.windowAnimations == 0 })
            assertTrue(windows.any { kotlin.math.abs(it.dimAmount - 0.32f) < 0.001f })
        }
        capture("$width-centered")
        runBlocking { container.settings.update { it.copy(compactUiMode = CompactUiMode.ON) } }
        compose.waitUntil(5_000) {
            kotlin.math.abs(compose.onNodeWithTag("command-input-row").fetchSemanticsNode().boundsInRoot.height - 64 * density) < density
        }
        compose.onNodeWithTag("command-input-row").assertHeightIsEqualTo(64.dp)
        input.assertTextEquals("日本語 draft").assertIsFocused()
        close()
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.selectSettingsCategory("command_bar")
        choose(CommandBarLayout.ANCHORED)
        capture("$width-setting-new-on")
        choose(CommandBarLayout.CENTERED)
        capture("$width-setting-new-off")
        compose.returnToBrowserFromSettings()
        val returned = pages()
        live.forEach { page ->
            assertTrue(returned.any { it === page })
            assertEquals("\"live\"", js(page, "window.layoutToken"))
            assertEquals("\"未送信 日本語\"", js(page, "document.getElementById('draft').value"))
        }
        assertEquals(1, server.requests.count { it.path == "/layout-left" })
        if (width >= 600) assertEquals(1, server.requests.count { it.path == "/layout-right" })

        compose.activityRule.scenario.recreate()
        open()
        compose.onNodeWithTag("command-use-centered").assertDoesNotExist()
        assertEquals(CommandBarLayout.CENTERED, runBlocking { container.settings.settings.first().commandBarLayout })
        close()
        // The migration action remains available even if every suggestion category is hidden.
        runBlocking { container.settings.update { it.copy(commandBarLayout = CommandBarLayout.ANCHORED,
            commandBarLayoutNoticeDismissed = false, commandBar = CommandBarSettings(false, false, false, false, false)) } }
        open()
        input.performTextReplacement("保留中")
        compose.onNodeWithTag("command-suggestions").assertDoesNotExist()
        compose.onNodeWithTag("command-use-centered").assertIsDisplayed()
        compose.onNodeWithTag("command-dismiss-layout-notice").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("command-use-centered").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        input.assertTextEquals("保留中")
        assertEquals(CommandBarLayout.ANCHORED, runBlocking { container.settings.settings.first().commandBarLayout })
        assertTrue(runBlocking { container.settings.settings.first().commandBarLayoutNoticeDismissed })
        close()
        compose.activityRule.scenario.recreate()
        open()
        compose.onNodeWithTag("command-use-centered").assertDoesNotExist()
        compose.onNodeWithTag("command-dismiss-layout-notice").assertDoesNotExist()
        close()
    }
}
