package com.takeruf.nagi

import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.input.key.Key
import androidx.test.filters.SdkSuppress
import com.takeruf.nagi.browser.search.DefaultSearchEngines
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry

private fun motionShell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

private fun originalMotionSize() = Regex("Override size: (\\d+x\\d+)").find(motionShell("wm size"))?.groupValues?.get(1) ?: "reset"

private fun resizeMotionWindow(width: Int) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val density = context.resources.displayMetrics.density
    motionShell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
}

@SdkSuppress(minSdkVersion = 29)
@OptIn(ExperimentalTestApi::class)
class CommandBarMotionUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var originalSize: String
    @Before fun saveSize() { originalSize = originalMotionSize() }
    @After fun restoreSize() {
        compose.mainClock.autoAdvance = true
        motionShell("wm size $originalSize")
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private fun exercise(bottom: Boolean) {
        resizeMotionWindow(1000)
        var open by mutableStateOf(false)
        compose.setContent {
            NagiTheme(ThemeMode.LIGHT) {
                if (open) CommandBar("nagi", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                    BrowserSettings(bottomSearchBar = bottom), onDismiss = { open = false }, onExecute = {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val input = bounds("command-input-row")
        val startHeight = bounds("command-panel").height
        val samples = mutableListOf<Float>()
        for (elapsed in listOf(32L, 48L, 240L)) {
            compose.mainClock.advanceTimeBy(elapsed)
            compose.waitForIdle()
            val panel = bounds("command-panel")
            val row = bounds("command-input-row")
            if (bottom) assertEquals("Expansion stays attached above the bottom input", row.bottom, panel.bottom, 1f)
            else {
                assertEquals("The top input never slides", input.top, row.top, 1f)
                assertEquals("Expansion starts at the top input", row.top, panel.top, 1f)
            }
            assertEquals(input.height, row.height, 1f)
            samples += panel.height
        }
        assertTrue("Opening grows out of the input", startHeight < samples.last())
        assertTrue("Results progressively unfold", samples.first() < samples.last())
        assertTrue("Reveal has no bounce or overshoot", samples.zipWithNext().all { (a, b) -> a <= b })
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews().mapNotNull { it.layoutParams as? WindowManager.LayoutParams }
                .filter { it.type == WindowManager.LayoutParams.TYPE_APPLICATION }
            assertTrue("The real dialog window is present", windows.isNotEmpty())
            assertTrue("Android does not animate the whole dialog", windows.all { it.windowAnimations == 0 })
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun resultsExpandDownFromTheStationaryTopInput() = exercise(bottom = false)

    @Test fun phoneInputMovesFromItsTriggerToTheTopAndReturnsOnDismissal() {
        resizeMotionWindow(411)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        var open by mutableStateOf(false)
        compose.setContent {
            NagiTheme(ThemeMode.LIGHT, compactUiMode = CompactUiMode.OFF) {
                if (open) CommandBar("nagi", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                    BrowserSettings(commandBarLayoutNoticeDismissed = true), onDismiss = { open = false }, onExecute = {},
                    phoneEntryBounds = Rect(20 * density, 320 * density, 390 * density, 368 * density))
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open = true }
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val start = bounds("command-input-row")
        val samples = mutableListOf(start.top)
        for (elapsed in listOf(32L, 48L, 240L)) {
            compose.mainClock.advanceTimeBy(elapsed)
            compose.waitForIdle()
            val row = bounds("command-input-row")
            assertEquals("The text is translated, never scaled", start.height, row.height, 1f)
            samples += row.top
        }
        assertTrue("Opening travels from the tapped search field", start.top > samples.last() + 100 * density)
        assertTrue("The move has no bounce or overshoot", samples.zipWithNext().all { (a, b) -> a >= b })
        compose.onNodeWithTag("command-phone-screen").assertIsDisplayed()
        compose.onNodeWithTag("command-panel").assertWidthIsEqualTo(androidx.compose.ui.unit.Dp(411f))
        val top = samples.last()
        compose.onNodeWithTag("command-input").performKeyInput { pressKey(Key.Escape) }
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        assertTrue("Dismissal returns toward the same trigger", bounds("command-input-row").top > top)
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        compose.onNodeWithTag("command-panel").assertDoesNotExist()
    }

    @Test fun phoneKeyboardEntryDoesNotAnimateFromTheTouchTrigger() {
        resizeMotionWindow(411)
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        compose.setContent {
            NagiTheme(ThemeMode.LIGHT) {
                CommandBar("nagi", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all), BrowserSettings(),
                    onDismiss = {}, onExecute = {}, animateEntrance = false,
                    phoneEntryBounds = Rect(20 * density, 320 * density, 390 * density, 368 * density))
            }
        }
        val row = bounds("command-input-row")
        assertTrue("Keyboard shortcuts open immediately at the top", row.top < 100 * density)
        compose.onNodeWithTag("command-phone-screen").assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
class CommandBarReducedMotionUiTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun zeroDurationScaleShowsResultsWithoutWaitingForTheReveal() {
        val originalSize = originalMotionSize()
        resizeMotionWindow(411)
        try {
        var open by mutableStateOf(false)
        compose.setContent {
            NagiTheme(ThemeMode.LIGHT) {
                if (open) CommandBar("nagi", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                    BrowserSettings(), onDismiss = {}, onExecute = {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open = true }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        val height = compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow.height
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsDisplayed()
        compose.onNodeWithTag("command-phone-screen").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(240)
        assertEquals(height, compose.onNodeWithTag("command-panel").fetchSemanticsNode().boundsInWindow.height, 1f)
        compose.mainClock.autoAdvance = true
        } finally {
            compose.mainClock.autoAdvance = true
            motionShell("wm size $originalSize")
        }
    }
}
