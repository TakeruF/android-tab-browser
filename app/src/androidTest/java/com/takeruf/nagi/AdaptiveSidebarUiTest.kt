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

    @Test fun windowResizeRestoresPreferenceAndAllowsTemporaryManualExpansion() {
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
        fun resize(width: Float) {
            shell("wm size ${(width * density).toInt()}x${(800 * density).toInt()}")
            compose.waitUntil(10_000) {
                kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 30
            }
            compose.waitForIdle()
        }
        fun preference() = runBlocking { container.settings.settings.first().sidebarCollapsed }

        resize(1000f)
        awaitSidebar(false)
        resize(480f)
        awaitSidebar(true)
        assertFalse(preference())
        resize(620f)
        awaitSidebar(true)
        resize(640f)
        awaitSidebar(false)

        resize(480f)
        awaitSidebar(true)
        compose.onNodeWithContentDescription("Expand sidebar").performClick()
        awaitSidebar(false)
        assertFalse(preference())
        resize(1000f)
        awaitSidebar(false)
        compose.onNodeWithContentDescription("Collapse sidebar").performClick()
        awaitSidebar(true)
        compose.waitUntil { preference() }
        resize(480f)
        awaitSidebar(true)
        compose.onNodeWithContentDescription("Expand sidebar").performClick()
        awaitSidebar(false)
        assertTrue(preference())
        resize(1000f)
        awaitSidebar(true)
    }
}
