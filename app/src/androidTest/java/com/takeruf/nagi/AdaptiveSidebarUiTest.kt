package com.takeruf.nagi

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test

class AdaptiveSidebarUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var originalSize: String? = null
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    @After fun restoreDisplay() {
        originalSize?.let { shell("wm size $it") }
    }

    @Test fun windowResizeRestoresPreferenceAndKeepsTabSheetTemporary() {
        val container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
        }
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))
            ?.groupValues?.get(1) ?: "reset"
        val density = compose.activity.resources.displayMetrics.density
        fun awaitSidebar(collapsed: Boolean) {
            val label = if (collapsed) "Expand sidebar" else "Collapse sidebar"
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
            }
            if (collapsed) compose.onNodeWithTag("sidebar").assertWidthIsEqualTo(72.dp)
        }
        fun awaitCompact(sheetOpen: Boolean = false) {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("compact-browser-bar").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("sidebar").assertDoesNotExist()
            compose.onNodeWithTag("sidebar-resize-handle").assertDoesNotExist()
            if (sheetOpen) compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
            else compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        }
        fun resize(width: Float, height: Float = 800f) {
            shell("wm size ${(width * density).toInt()}x${(height * density).toInt()}")
            compose.waitUntil(10_000) {
                kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 30
            }
            compose.waitForIdle()
        }
        fun preference() = runBlocking { container.settings.settings.first().sidebarCollapsed }

        resize(1000f)
        awaitSidebar(false)
        resize(620f)
        awaitSidebar(false)
        resize(480f)
        awaitCompact()
        assertFalse(preference())
        resize(620f)
        awaitSidebar(false)
        // Exact boundary is wide, even after narrow mode or Activity restoration.
        resize(599f)
        awaitCompact()
        resize(600f)
        awaitSidebar(false)
        compose.activityRule.scenario.recreate()
        awaitSidebar(false)
        resize(599f)
        awaitCompact()
        compose.activityRule.scenario.recreate()
        awaitCompact()
        resize(620f)
        awaitSidebar(false)
        // A portrait tablet-sized window is wide; orientation alone is irrelevant.
        resize(800f, 1280f)
        awaitSidebar(false)
        resize(1280f, 800f)
        awaitSidebar(false)

        resize(480f)
        awaitCompact()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        awaitCompact(sheetOpen = true)
        assertFalse(preference())
        resize(1000f)
        awaitSidebar(false)
        compose.onNodeWithContentDescription("Collapse sidebar").performClick()
        awaitSidebar(true)
        compose.waitUntil { preference() }
        resize(480f)
        awaitCompact()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        awaitCompact(sheetOpen = true)
        assertTrue(preference())
        resize(1000f)
        awaitSidebar(true)
    }
}
