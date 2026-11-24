package com.takeruf.nagi

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.takeruf.nagi.browser.favicon.FaviconStore
import com.takeruf.nagi.browser.favicon.faviconOrigin
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import com.takeruf.nagi.browser.search.SuggestionAction
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Exercises the actual dialog and shared icon cache, including its wrapped private context. */
class CommandBarFaviconUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var originalSize: String
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
    }
    @After fun cleanup() { scenario.close(); shell("wm size $originalSize") }

    private fun pixelsContain(id: String, color: Int): Boolean {
        val bitmap = compose.onNodeWithTag("suggestion:tab:$id").captureToImage().asAndroidBitmap()
        return (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == color } }
    }

    @Test fun tabIconsFollowSiteAndThemeInPhoneAndKeyboardTabletDialogs() {
        FixtureServer().use { first -> FixtureServer().use { second ->
            val store = FaviconStore.get(activity)
            fun save(url: String, color: Int, dark: Boolean): String {
                val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                return try { runBlocking { store.save(url, bitmap, dark)!! } } finally { bitmap.recycle() }
            }
            val firstPath = save(first.origin, Color.MAGENTA, false)
            val secondPath = save(second.origin, Color.CYAN, false)
            save(first.origin, Color.YELLOW, true)
            save(second.origin, Color.GREEN, true)
            val workspace = WorkspaceSnapshot(spaces = listOf(Space("personal", "Personal")), tabs = listOf(
                BrowserTab("first", "personal", first.origin + "/first", "First site", firstPath),
                BrowserTab("second", "personal", second.origin + "/second", "Second site", secondPath),
                BrowserTab("blank", "personal")))
            var theme by mutableStateOf(ThemeMode.LIGHT)
            var keyboard by mutableStateOf(false)
            var selected: SuggestionAction? = null
            val density = activity.resources.displayMetrics.density
            main { activity.setContent {
                val config = Configuration(LocalConfiguration.current).apply {
                    this.keyboard = if (keyboard) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                    hardKeyboardHidden = if (keyboard) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
                }
                CompositionLocalProvider(LocalConfiguration provides config) {
                    NagiTheme(theme) { CommandBar("", workspace, BrowserSettings(), {}, { selected = it }) }
                }
            } }
            for ((width, dark) in listOf(411 to false, 1000 to true)) {
                shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
                main { theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT; keyboard = dark }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("suggestion:tab:first").fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(10_000) { pixelsContain("first", if (dark) Color.YELLOW else Color.MAGENTA) }
                compose.waitUntil(10_000) { pixelsContain("second", if (dark) Color.GREEN else Color.CYAN) }
                assertFalse("Rows must use their own site's icon", pixelsContain("second", if (dark) Color.YELLOW else Color.MAGENTA))
                compose.onNodeWithTag("command-input").assertIsFocused()
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                File(activity.getExternalFilesDir(null), "command-tab-icons-$width.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
            compose.onNodeWithTag("suggestion:tab:second").performClick()
            assertEquals(SuggestionAction.SelectTab("second"), selected)
            assertTrue("Cached icons should not request either site", first.requests.isEmpty() && second.requests.isEmpty())
        } }
    }

    @Test fun privateDialogDoesNotFetchOrReadNormalIconCache() {
        Assume.assumeTrue(PrivateProfiles.supported())
        FixtureServer().use { server ->
            var private: PrivateActivity? = null
            main { activity.openPrivateBrowsing() }
            try {
                compose.waitUntil(15_000) {
                    main { private = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .filterIsInstance<PrivateActivity>().firstOrNull() }
                    private != null
                }
                val workspace = WorkspaceSnapshot(spaces = listOf(Space("personal", "Personal")),
                    tabs = listOf(BrowserTab("private", "personal", server.origin + "/private-only", "Private site")))
                main { private!!.setContent { NagiTheme(ThemeMode.DARK) {
                    CommandBar("", workspace, BrowserSettings(), {}, {})
                } } }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("suggestion:tab:private").fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                SystemClock.sleep(1_000) // Allow an accidental async icon fetch to reach the fixture.
                assertTrue("Private dialog must not request a favicon", server.requests.isEmpty())
                assertNull(FaviconStore.get(activity).icons.value[faviconOrigin(server.origin)])
            } finally { main { private?.closePrivateBrowsing() } }
        }
    }
}
