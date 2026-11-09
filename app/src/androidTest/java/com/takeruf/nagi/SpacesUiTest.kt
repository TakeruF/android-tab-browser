package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
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
        runBlocking { container.settings.update { original }; container.spaces.delete(spaceId) }
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
        webViews.forEach { evaluate(it, "window.__spaceToken='kept';document.getElementById('draft').value='unfinished draft'") }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Use Spaces"))
        assertTrue(compose.onNodeWithText("Use Spaces").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithText("Default search engine").fetchSemanticsNode().boundsInRoot.top)
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
        compose.onNodeWithContentDescription("Back to browser").performClick()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme) } }
            for (width in listOf(1000, 360)) {
                resize(width)
                if (width == 360) compose.onNodeWithTag("compact-tabs-button").performClick()
                compose.onNodeWithContentDescription("New Space").assertDoesNotExist()
                compose.onNodeWithContentDescription("Space actions").assertDoesNotExist()
                compose.onNodeWithText("Pinned tabs").assertIsDisplayed()
                compose.onNodeWithTag("sidebar-split-group").assertIsDisplayed()
                compose.onNodeWithContentDescription("Sidebar tabs").performTouchInput { swipeLeft() }
                assertEquals(spaceId, runBlocking { container.settings.settings.first().selectedSpaceId })
                screenshot("off-${width}-${theme.name.lowercase()}")
                if (width == 360) {
                    compose.onNodeWithContentDescription("Close Tabs").performClick()
                    compose.waitUntil(5_000) { views().size == 2 }
                }
                assertEquals(webViews.toSet(), views().toSet())
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
        compose.onNodeWithContentDescription("Back to browser").performClick()
        compose.onNodeWithContentDescription("New Space").assertIsDisplayed()
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
}
