package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CommandBarSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        compose.waitForIdle()
    }

    private fun shortcut(code: Int, modifiers: Int = KeyEvent.META_CTRL_ON) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP))
            instrumentation.sendKeySync(KeyEvent(now, now, action, code, 0, modifiers,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        instrumentation.waitForIdleSync()
    }

    private fun visibleWebViews(): List<WebView> {
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
        val value = CompletableDeferred<String>()
        compose.runOnIdle { web.evaluateJavascript(script) { value.complete(it) } }
        return runBlocking { withTimeout(5_000) { value.await() } }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300) // Allow the rendered frame to catch up with Compose semantics.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "command-bar-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun scrollTo(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    private fun prefs() = runBlocking { container.settings.settings.first() }
    private fun waitForSettings(condition: (BrowserSettings) -> Boolean) = compose.waitUntil(5_000) { condition(prefs()) }
    private fun openSettings() {
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isNotEmpty() }
        compose.selectSettingsCategory("command_bar")
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = prefs()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Command Bar QA ${System.nanoTime()}")
            spaceId = prefs().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/ua-command-left")
            rightId = container.tabs.create(spaceId, "${server.origin}/ua-command-right", select = false)
            container.tabs.pair(leftId, rightId)
        }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    @Test fun phoneLightSettingsAndEmptySuggestionsKeepNavigationUsable() = exercise(411, ThemeMode.LIGHT)
    @Test fun tabletDarkSettingsPreserveLiveSplitAndCommandChoices() = exercise(1000, ThemeMode.DARK)

    private fun exercise(width: Int, theme: ThemeMode) {
        resize(width)
        runBlocking { container.settings.update { it.copy(theme = theme) } }
        compose.waitUntil(20_000) {
            val views = visibleWebViews()
            views.size == (if (width >= 600) 2 else 1) &&
                views.all { js(it, "document.readyState") == "\"complete\"" }
        }
        val before = visibleWebViews()
        before.forEach { js(it, "window.commandBarToken='retained';document.getElementById('draft').value='未送信 日本語';true") }
        openSettings()
        scrollTo("command-bar-show-search").assertIsOn()
        assertSpacesSuggestionFollowsFeatureSetting(width, theme)
        capture("$width-categories-$theme")
        // Commands are shown or hidden together; there are no per-command switches.
        compose.onAllNodesWithTag("command-bar-command-new_tab").assertCountEquals(0)
        scrollTo("command-bar-show-commands").assertIsOn().performClick()
        waitForSettings { !it.commandBar.showCommands }
        capture("$width-commands-$theme")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("command-bar-show-commands").assertIsOff()
        // Activity recreation can recreate WebViews. Check retained live sessions across
        // the subsequent Settings round trip separately from saved-preference recovery.
        compose.returnToBrowserFromSettings()
        compose.waitUntil(20_000) { visibleWebViews().size == before.size }
        val live = visibleWebViews()
        live.forEach { js(it, "window.commandBarToken='live';document.getElementById('draft').value='未送信 日本語';true") }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNodeWithTag("command-input").performTextReplacement(">")
        compose.onNodeWithTag("suggestion:command:NEW_TAB").assertDoesNotExist()
        compose.onNodeWithTag("suggestion:command:RESTORE_TAB").assertDoesNotExist()
        capture("$width-filtered-$theme")
        shortcut(KeyEvent.KEYCODE_ESCAPE, 0)
        openSettings()
        for (category in listOf("search", "tabs", "history", "spaces")) {
            scrollTo("command-bar-show-$category").performClick()
        }
        waitForSettings { with(it.commandBar) { !showSearch && !showTabs && !showHistory && !showSpaces && !showCommands } }
        capture("$width-hidden-$theme")
        compose.returnToBrowserFromSettings()
        compose.waitUntil(10_000) { visibleWebViews().size == live.size }
        val after = visibleWebViews()
        assertTrue(live.all { old -> after.any { it === old } })
        after.forEach {
            assertEquals("\"live\"", js(it, "window.commandBarToken"))
            assertEquals("\"未送信 日本語\"", js(it, "document.getElementById('draft').value"))
        }
        assertEquals(rightId, runBlocking { container.workspace.dao.tab(leftId)!!.splitRightTabId })
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        shortcut(KeyEvent.KEYCODE_L)
        // Nothing to suggest: only the input remains, with no empty-state message.
        compose.onNodeWithTag("command-input").performTextReplacement(">")
        compose.onNodeWithTag("command-suggestions").assertDoesNotExist()
        compose.onNodeWithText("No matches").assertDoesNotExist()
        capture("$width-empty-$theme")
        // Hidden command suggestions do not disable their keyboard bindings.
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.onNodeWithTag("settings-screen").assertExists()
        compose.returnToBrowserFromSettings()
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNodeWithTag("command-input").performTextReplacement("${server.origin}/two")
        compose.onNodeWithTag("command-suggestions").assertDoesNotExist()
        compose.onNodeWithTag("command-input").performImeAction()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("command-input").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15_000) { server.requests.any { it.path == "/two" } }
        assertFalse(prefs().commandBar.showCommands)
    }

    private fun assertSpacesSuggestionFollowsFeatureSetting(width: Int, theme: ThemeMode) {
        fun setSpacesEnabled(enabled: Boolean) {
            compose.selectSettingsCategory("general")
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Use Spaces"))
            compose.onNodeWithText("Use Spaces").performClick()
            compose.onNodeWithTag("confirm-spaces-change").performClick()
            waitForSettings { it.spacesEnabled == enabled }
            compose.selectSettingsCategory("command_bar")
        }

        scrollTo("command-bar-show-spaces").assertIsOn().assertIsEnabled()
        setSpacesEnabled(false)
        scrollTo("command-bar-show-spaces").assertIsOff().assertIsNotEnabled()
            .performTouchInput { click() }
        assertTrue(prefs().commandBar.showSpaces)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        scrollTo("command-bar-show-spaces").assertIsOff().assertIsNotEnabled()
        capture("$width-spaces-disabled-$theme")
        setSpacesEnabled(true)
        scrollTo("command-bar-show-spaces").assertIsOn().assertIsEnabled().performClick()
        waitForSettings { !it.commandBar.showSpaces }
        setSpacesEnabled(false)
        scrollTo("command-bar-show-spaces").assertIsOff().assertIsNotEnabled()
        setSpacesEnabled(true)
        scrollTo("command-bar-show-spaces").assertIsOff().assertIsEnabled().performClick()
        waitForSettings { it.commandBar.showSpaces }
    }
}
