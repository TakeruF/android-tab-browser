package com.takeruf.nagi

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.search.DefaultSearchEngines
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.io.File

/** Controlled Android configurations; actual keyboard hardware is a separate check. */
@OptIn(ExperimentalTestApi::class)
class CommandBarKeyboardLayoutUiTest {
    @get:Rule val compose = createComposeRule()
    private var originalSize: String? = null

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @After fun restoreSize() {
        originalSize?.let { shell("wm size $it") }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300) // Wait for the rendered frame after configuration recomposition.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "keyboard-layout-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun wideKeyboardDensityChangesKeepTheEditorAndSelectedSuggestion() = exercise(1000)
    @Test fun phoneKeyboardDensityChangesKeepTheEditorAndSelectedSuggestion() = exercise(411)

    private fun exercise(widthDp: Int) {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        shell("wm size ${(widthDp * density).toInt()}x${(820 * density).toInt()}")
        var windowWidth by mutableIntStateOf(0)
        var keyboardType by mutableIntStateOf(Configuration.KEYBOARD_NOKEYS)
        var hidden by mutableIntStateOf(Configuration.HARDKEYBOARDHIDDEN_YES)
        var compactUiMode by mutableStateOf(CompactUiMode.AUTO)
        compose.setContent {
            val window = LocalWindowInfo.current
            SideEffect { windowWidth = window.containerSize.width }
            val configuration = Configuration(LocalConfiguration.current).apply {
                keyboard = keyboardType
                hardKeyboardHidden = hidden
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                NagiTheme(ThemeMode.LIGHT, compactUiMode = compactUiMode) {
                    CommandBar("", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all), BrowserSettings(),
                        onDismiss = {}, onExecute = {}, animateEntrance = false)
                }
            }
        }
        compose.waitUntil(5_000) {
            kotlin.math.abs(windowWidth / density - widthDp) < 10 &&
                compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        val input = compose.onNodeWithTag("command-input")
        input.performTextReplacement("日本語 draft")
        input.performKeyInput { pressKey(Key.DirectionDown) }
        val suggestion = compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsSelected()
        val originalInput = input.fetchSemanticsNode().boundsInWindow
        val originalRow = compose.onNodeWithTag("command-input-row").fetchSemanticsNode().boundsInWindow
        val selection = input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]
        val touchHeight = suggestion.fetchSemanticsNode().boundsInWindow.height
        val touchPanelHeight = compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow.height
        fun assertEditorGeometry(compact: Boolean) {
            val row = compose.onNodeWithTag("command-input-row").fetchSemanticsNode().boundsInWindow
            assertEquals("Changing density keeps the input at the top", originalRow.top, row.top, 1f)
            assertEquals(originalRow.left, row.left, 1f)
            assertEquals(originalRow.right, row.right, 1f)
            assertEquals((if (widthDp < 600) (if (compact) 40 else 48) else 40) * density, row.height, 1f)
            assertEquals(selection, input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
            if (widthDp < 600) {
                compose.onNodeWithTag("command-phone-screen").assertIsDisplayed()
                assertEquals("Phone search fills the available viewport",
                    compose.onNodeWithTag("command-phone-screen").fetchSemanticsNode().boundsInWindow.height,
                    compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow.height, 1f)
            } else {
                compose.onNodeWithTag("command-phone-screen").assertDoesNotExist()
                assertEquals(originalInput, input.fetchSemanticsNode().boundsInWindow)
            }
        }
        assertEditorGeometry(false)
        capture("$widthDp-touch")

        compose.runOnIdle {
            keyboardType = Configuration.KEYBOARD_QWERTY
            hidden = Configuration.HARDKEYBOARDHIDDEN_NO
        }
        input.assertTextEquals("日本語 draft").assertIsFocused()
        suggestion.assertIsSelected()
        assertEditorGeometry(true)
        assertTrue("Physical keyboard uses denser rows", suggestion.fetchSemanticsNode().boundsInWindow.height < touchHeight)
        val denseHeight = suggestion.fetchSemanticsNode().boundsInWindow.height
        if (widthDp >= 600) assertTrue("The suggestion panel becomes shorter", compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow.height < touchPanelHeight)
        capture("$widthDp-physical-keyboard")

        // A folded/hidden hardware keyboard is unavailable for typing, just like disconnection.
        compose.runOnIdle { hidden = Configuration.HARDKEYBOARDHIDDEN_YES }
        assertEquals(touchHeight, suggestion.fetchSemanticsNode().boundsInWindow.height, 1f)
        assertEditorGeometry(false)
        input.assertTextEquals("日本語 draft").assertIsFocused()
        suggestion.assertIsSelected()
        compose.runOnIdle { keyboardType = Configuration.KEYBOARD_NOKEYS }
        assertEquals(touchHeight, suggestion.fetchSemanticsNode().boundsInWindow.height, 1f)
        compose.runOnIdle { compactUiMode = CompactUiMode.ON }
        assertEquals(denseHeight, suggestion.fetchSemanticsNode().boundsInWindow.height, 1f)
        assertEditorGeometry(true)
        input.assertTextEquals("日本語 draft").assertIsFocused()
        suggestion.assertIsSelected()
        compose.runOnIdle {
            keyboardType = Configuration.KEYBOARD_QWERTY
            hidden = Configuration.HARDKEYBOARDHIDDEN_NO
            compactUiMode = CompactUiMode.OFF
        }
        assertEquals(touchHeight, suggestion.fetchSemanticsNode().boundsInWindow.height, 1f)
        assertEditorGeometry(false)
        input.assertTextEquals("日本語 draft").assertIsFocused()
        suggestion.assertIsSelected()
        compose.runOnIdle { keyboardType = Configuration.KEYBOARD_NOKEYS }
        assertEquals(touchHeight, suggestion.fetchSemanticsNode().boundsInWindow.height, 1f)
        assertEditorGeometry(false)
        input.assertTextEquals("日本語 draft").assertIsFocused()
        suggestion.assertIsSelected()
    }
}
