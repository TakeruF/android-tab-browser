package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CameraAvoidanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings

    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() = runBlocking { container.settings.update { original } }

    private fun expectMargin(value: Int) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("$value dp").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("$value dp").assertIsDisplayed()
    }

    @Test fun toggleFineAdjustmentResetAndRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        val list = compose.onNodeWithTag("settings-list")
        list.performScrollToNode(hasText("Avoid camera overlap"))
        compose.onNodeWithTag("settings-camera-margin").assertDoesNotExist()
        val before = list.fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithText("Avoid camera overlap").performClick()
        list.performScrollToNode(hasTestTag("settings-camera-margin-slider"))
        expectMargin(32)
        val after = list.fetchSemanticsNode().boundsInRoot.left
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(32f * density, after - before, 1f)
        compose.onNodeWithContentDescription("Decrease margin by 1").performClick()
        expectMargin(31)
        compose.onNodeWithContentDescription("Increase margin by 1").performClick()
        expectMargin(32)
        compose.onNodeWithTag("settings-camera-margin-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(17f) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("17 dp").fetchSemanticsNodes().isNotEmpty() }
        expectMargin(17)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "camera-avoidance-settings.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { runBlocking { container.settings.settings.first().cameraSideMargin == 17 } }
        // Navigation is intentionally independent of saved settings.
        if (compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-camera-margin-slider"))
        expectMargin(17)
        compose.onNodeWithText("Reset").performClick()
        expectMargin(32)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Avoid camera overlap"))
        compose.onNodeWithText("Avoid camera overlap").performClick()
        compose.onNodeWithTag("settings-camera-margin").assertDoesNotExist()
        assertEquals(before, compose.onNodeWithTag("settings-list").fetchSemanticsNode().boundsInRoot.left, 1f)
    }
}
