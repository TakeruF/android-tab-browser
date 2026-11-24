package com.takeruf.nagi

import android.graphics.Bitmap
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class SpacesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var originalConfiguration: Configuration
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private val extraSpaceIds = mutableListOf<String>()
    private lateinit var left: String
    private lateinit var right: String
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 20 }
        compose.waitForIdle()
    }
    @Before fun setup() {
        originalConfiguration = Configuration(compose.activity.resources.configuration)
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Spaces toggle QA")
            spaceId = container.settings.settings.first().selectedSpaceId
            left = container.tabs.create(spaceId, "${server.origin}/ua-spaces-left")
            right = container.tabs.create(spaceId, "${server.origin}/ua-spaces-right", select = false)
            container.tabs.pair(left, right)
        }
        resize(1000)
        compose.waitUntil(20_000) {
            val current = views()
            var loaded = false
            compose.runOnIdle { loaded = current.size == 2 && current.all { it.progress == 100 } }
            loaded
        }
    }
    @After fun cleanup() {
        compose.runOnIdle { compose.activity.window.decorView.dispatchConfigurationChanged(originalConfiguration) }
        runBlocking {
            container.settings.update { original }
            extraSpaceIds.forEach { container.spaces.delete(it) }
            container.spaces.delete(spaceId)
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
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown && it.width > 0 } }
        return result
    }
    private fun evaluate(view: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(10_000) { result.await() } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "spaces-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        shell("cp ${File(compose.activity.getExternalFilesDir(null), "spaces-$name.png").absolutePath} /sdcard/Download/spaces-$name.png")
    }
    @Test fun toggleUpdatesWideAndCompactUiPreservesLiveSplitAndRestoresSpaces() {
        val before = runBlocking { container.workspace.dao.spaces() }
        val webViews = views()
        val leftView = webViews.single { evaluate(it, "location.pathname") == "\"/ua-spaces-left\"" }
        webViews.forEach { evaluate(it, "window.__spaceToken='kept';document.getElementById('draft').value='unfinished draft'") }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.selectSettingsCategory("general")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Use Spaces"))
        assertTrue(compose.onNodeWithText("Use Spaces").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Open links in new tab").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText("Use Spaces").performClick()
        compose.onNodeWithText("Turn off Spaces?").assertIsDisplayed()
        assertTrue(runBlocking { container.settings.settings.first().spacesEnabled })
        screenshot("confirm-off-wide")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("spaces-confirmation").assertDoesNotExist()
        assertTrue(runBlocking { container.settings.settings.first().spacesEnabled })
        compose.onNodeWithText("Use Spaces").performClick()
        compose.onNodeWithTag("confirm-spaces-change").performClick()
        compose.waitUntil(5_000) { runBlocking { !container.settings.settings.first().spacesEnabled } }
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("New Space").fetchSemanticsNodes().isEmpty() }
        screenshot("settings-off")
        compose.returnToBrowserFromSettings()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme) } }
            for (width in listOf(1000, 360)) {
                resize(width)
                if (width == 360) compose.onNodeWithTag("compact-tabs-button").performClick()
                compose.onNodeWithContentDescription("New Space").assertDoesNotExist()
                compose.onNodeWithContentDescription("Space actions").assertDoesNotExist()
                compose.onNodeWithText("Pinned tabs").assertIsDisplayed()
                if (width >= 600) compose.onNodeWithTag("sidebar-split-group").performScrollTo().assertIsDisplayed()
                else {
                    compose.onNodeWithTag("sidebar-split-group").assertDoesNotExist()
                    compose.onNodeWithTag("sidebar-tab-$left").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("sidebar-tab-$right").performScrollTo().assertIsDisplayed()
                }
                compose.onNodeWithContentDescription("Sidebar tabs").performTouchInput { swipeLeft() }
                assertEquals(spaceId, runBlocking { container.settings.settings.first().selectedSpaceId })
                screenshot("off-${width}-${theme.name.lowercase()}")
                if (width == 360) {
                    compose.onNodeWithContentDescription("Close Tabs").performClick()
                    compose.waitUntil(5_000) { views().singleOrNull() === leftView }
                }
                assertEquals(if (width >= 600) webViews.toSet() else setOf(leftView), views().toSet())
                webViews.forEach { assertEquals("\"kept:unfinished draft\"", evaluate(it, "window.__spaceToken+':'+document.getElementById('draft').value")) }
            }
        }
        resize(1000)
        compose.onNodeWithContentDescription("Collapse sidebar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Expand sidebar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("New Space").assertDoesNotExist()
        screenshot("off-collapsed")
        compose.onNodeWithContentDescription("Expand sidebar").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.selectSettingsCategory("general")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Use Spaces"))
        resize(360)
        compose.onNodeWithText("Use Spaces").performClick()
        compose.onNodeWithText("Turn on Spaces?").assertIsDisplayed()
        assertFalse(runBlocking { container.settings.settings.first().spacesEnabled })
        screenshot("confirm-on-compact")
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(runBlocking { container.settings.settings.first().spacesEnabled })
        compose.onNodeWithText("Use Spaces").performClick()
        compose.onNodeWithTag("confirm-spaces-change").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().spacesEnabled } }
        resize(1000)
        compose.returnToBrowserFromSettings()
        compose.onNodeWithContentDescription("New Space").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Space actions").assertIsDisplayed()
        screenshot("on-restored")
        assertEquals(before, runBlocking { container.workspace.dao.spaces() })
        assertEquals(right, runBlocking { container.workspace.dao.tab(left)!!.splitRightTabId })
        assertEquals(webViews.toSet(), views().toSet())
        runBlocking { container.settings.update { it.copy(spacesEnabled = false) } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pinned tabs").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("New Space").assertDoesNotExist()
        assertFalse(runBlocking { container.settings.settings.first().spacesEnabled })
    }

    @Test fun collapsedSwitcherShowsOnlyCurrentSpaceAndRetainsLiveSplitAcrossMenuSwitches() {
        val webViews = views()
        webViews.forEach { evaluate(it, "window.__spaceToken='kept';document.getElementById('draft').value='unfinished draft'") }
        val otherId = runBlocking {
            container.spaces.create("Other Space QA", icon = "💼")
            container.settings.settings.first().selectedSpaceId.also {
                extraSpaceIds += it
                container.spaces.select(spaceId)
            }
        }
        compose.onNodeWithContentDescription("Collapse sidebar").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("collapsed-space-switcher").fetchSemanticsNodes().isNotEmpty() }
        for ((theme, compact, keyboard) in listOf(
            Triple(ThemeMode.LIGHT, CompactUiMode.OFF, true),
            Triple(ThemeMode.DARK, CompactUiMode.ON, false),
            Triple(ThemeMode.LIGHT, CompactUiMode.AUTO, true),
            Triple(ThemeMode.DARK, CompactUiMode.AUTO, false),
        )) {
            runBlocking { container.settings.update { it.copy(theme = theme, compactUiMode = compact) } }
            compose.runOnIdle {
                compose.activity.window.decorView.dispatchConfigurationChanged(Configuration(compose.activity.resources.configuration).apply {
                    this.keyboard = if (keyboard) Configuration.KEYBOARD_QWERTY else Configuration.KEYBOARD_NOKEYS
                    hardKeyboardHidden = if (keyboard) Configuration.HARDKEYBOARDHIDDEN_NO else Configuration.HARDKEYBOARDHIDDEN_YES
                })
            }
            val compactEnabled = compact.enabled(keyboard)
            val rail = compose.onNodeWithTag("sidebar")
            val controlSize = if (compactEnabled) 36.dp else 48.dp
            rail.assertWidthIsEqualTo(if (compactEnabled) 52.dp else 72.dp)
            compose.onNodeWithTag("collapsed-space-switcher")
                .assertIsDisplayed().assertIsSelected().assertContentDescriptionEquals("Spaces: Spaces toggle QA")
                .assertWidthIsEqualTo(controlSize).assertHeightIsEqualTo(controlSize)
            val railBounds = rail.fetchSemanticsNode().boundsInRoot
            for (label in listOf("Expand sidebar", "Search or enter URL", "New tab", "History", "Settings")) {
                val control = compose.onNodeWithContentDescription(label).assertIsDisplayed().assertHasClickAction()
                control.assertWidthIsEqualTo(controlSize).assertHeightIsEqualTo(controlSize)
                val bounds = control.fetchSemanticsNode().boundsInRoot
                assertTrue("$label must fit inside the rail", bounds.left >= railBounds.left && bounds.right <= railBounds.right)
            }
            compose.onAllNodes(hasContentDescription("Switch to", substring = true)).assertCountEquals(0)
            compose.onNodeWithContentDescription("New Space").assertDoesNotExist()
            screenshot("current-only-${compact.name.lowercase()}-$keyboard")
            if (compact == CompactUiMode.OFF) {
                compose.onNodeWithTag("collapsed-space-switcher").performTouchInput { longClick() }
                compose.onNodeWithTag("collapsed-space-tooltip").assertIsDisplayed()
            }
            compose.onNodeWithTag("collapsed-space-switcher").performClick()
            compose.onNodeWithTag("collapsed-space-tooltip").assertDoesNotExist()
            compose.onNodeWithTag("collapsed-space-option-$spaceId").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("collapsed-space-option-$otherId").performScrollTo().assertIsNotSelected()
            screenshot("switcher-menu-${compact.name.lowercase()}-$keyboard")
            compose.onNodeWithTag("collapsed-space-option-$otherId").performClick()
            compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().selectedSpaceId == otherId } }
            compose.onNodeWithTag("collapsed-space-switcher").assertContentDescriptionEquals("Spaces: Other Space QA")
            compose.onNodeWithTag("collapsed-space-option-$otherId").assertDoesNotExist()
            compose.onNodeWithTag("collapsed-space-switcher").performClick()
            compose.onNodeWithTag("collapsed-space-option-$otherId").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("collapsed-space-option-$spaceId").performScrollTo().performClick()
            compose.waitUntil(10_000) { views().size == 2 }
            assertEquals(webViews.toSet(), views().toSet())
            webViews.forEach {
                assertEquals("\"kept:unfinished draft\"", evaluate(it, "window.__spaceToken+':'+document.getElementById('draft').value"))
            }
        }
        runBlocking { container.settings.update { it.copy(spacesEnabled = false) } }
        compose.onNodeWithTag("collapsed-space-switcher").assertDoesNotExist()
        runBlocking { container.settings.update { it.copy(spacesEnabled = true) } }
        compose.onNodeWithTag("collapsed-space-switcher").assertIsDisplayed()
        assertEquals(right, runBlocking { container.workspace.dao.tab(left)!!.splitRightTabId })
    }

    @Test fun compactSheetStaysOpenForSpaceSwipesAndDismissesForTabSelectionOrBack() {
        resize(360)
        compose.waitUntil(10_000) { views().size == 1 }
        val firstPage = views().single()
        evaluate(firstPage, "window.__spaceToken='first';document.getElementById('draft').value='日本語の未送信下書き';document.getElementById('draft').dispatchEvent(new Event('input',{bubbles:true}))")
        val otherId = runBlocking {
            container.spaces.create("Swiped Space QA", icon = "💼")
            container.settings.settings.first().selectedSpaceId.also {
                extraSpaceIds += it
                container.tabs.create(it, "${server.origin}/ua-spaces-other")
            }
        }
        compose.waitUntil(15_000) {
            val page = views().singleOrNull()
            var ready = false
            compose.runOnIdle { ready = page != null && page.url == "${server.origin}/ua-spaces-other" && page.progress == 100 }
            ready
        }
        val otherPage = views().single()
        evaluate(otherPage, "window.__spaceToken='other';document.getElementById('draft').value='Unsent other draft';document.getElementById('draft').dispatchEvent(new Event('input',{bubbles:true}))")
        runBlocking { container.spaces.select(spaceId) }
        compose.waitUntil(10_000) { views().singleOrNull() === firstPage }
        compose.onNodeWithTag("compact-tabs-button").performClick()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme) } }
            val title = compose.onNodeWithTag("compact-tab-sheet-title")
            val density = compose.activity.resources.displayMetrics.density
            // A horizontal gesture shorter than the commit threshold keeps the same Space.
            title.performTouchInput { swipe(center, center - Offset(24 * density, 0f), durationMillis = 180) }
            compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
            assertEquals(spaceId, runBlocking { container.settings.settings.first().selectedSpaceId })
            title.performTouchInput { swipeLeft(durationMillis = 240) }
            compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().selectedSpaceId == otherId } }
            compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
            title.assertTextEquals("Swiped Space QA")
            compose.waitUntil(10_000) { views().singleOrNull() === otherPage }
            assertEquals("\"other:Unsent other draft\"", evaluate(otherPage, "window.__spaceToken+':'+document.getElementById('draft').value"))
            screenshot("swipe-keeps-sheet-${theme.name.lowercase()}")
            title.performTouchInput { swipeRight(durationMillis = 240) }
            compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().selectedSpaceId == spaceId } }
            compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
            title.assertTextEquals("Spaces toggle QA")
            compose.waitUntil(10_000) { views().singleOrNull() === firstPage }
            assertEquals("\"first:日本語の未送信下書き\"", evaluate(firstPage, "window.__spaceToken+':'+document.getElementById('draft').value"))
        }
        fun awaitSheetClosed() {
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("compact-tab-sheet").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        }
        compose.onNodeWithTag("sidebar-tab-$left").performScrollTo().performClick()
        awaitSheetClosed()
        assertSame(firstPage, views().single())
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
        compose.waitForIdle()
        shell("input keyevent 4")
        awaitSheetClosed()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription("Close Tabs").performClick()
        awaitSheetClosed()
        assertEquals(1, server.requests.count { it.path == "/ua-spaces-left" })
        assertEquals(1, server.requests.count { it.path == "/ua-spaces-other" })
        assertEquals(right, runBlocking { container.workspace.dao.tab(left)!!.splitRightTabId })
    }
}
