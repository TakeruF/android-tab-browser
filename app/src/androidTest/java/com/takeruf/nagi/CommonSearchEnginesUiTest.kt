package com.takeruf.nagi

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.localization.NagiStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CommonSearchEnginesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private val strings get() = NagiStrings(compose.activity)

    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { it.copy(defaultSearchEngineId = "google", defaultAiEngineId = "chatgpt", commonSearchEngineIds = null,
                automaticSearchRegion = false, theme = ThemeMode.LIGHT) }
        }
    }

    @After fun cleanup() = runBlocking { container.settings.update { original } }

    private fun openSearch() {
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("android tablet")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        // The native IME animation is not driven by Compose's test clock.
        val settledAt = android.os.SystemClock.uptimeMillis() + 600
        compose.waitUntil(2_000) { android.os.SystemClock.uptimeMillis() >= settledAt }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "common-search-validation").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun selectedEnginesAppearTogetherAndTogglesStayInSync() {
        runBlocking {
            container.settings.update { it.copy(commonSearchEngineIds = setOf("google", "so360", "chatgpt")) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        val list = compose.onNodeWithTag("search-engines-list")
        val google = compose.onNodeWithTag("selected-common-engine:google")
        google.assertIsDisplayed().assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("selected-common-engine:so360").assertIsDisplayed().assertIsOn()
        compose.onNodeWithTag("selected-common-engine:chatgpt").assertIsDisplayed().assertIsOn()
        screenshot("common-engines-google-360-chatgpt")

        compose.onNodeWithTag("selected-common-engine:so360").performClick()
        compose.waitUntil(10_000) { runBlocking { "so360" !in container.settings.settings.first().commonSearchEngineIds.orEmpty() } }
        compose.onNodeWithTag("selected-common-engine:so360").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("common-engine:so360"))
        compose.onNodeWithTag("common-engine:so360").assertIsOff().performClick()
        compose.waitUntil(10_000) { runBlocking { "so360" in container.settings.settings.first().commonSearchEngineIds.orEmpty() } }
        list.performScrollToNode(hasTestTag("selected-common-engine:so360"))
        compose.onNodeWithTag("selected-common-engine:so360").assertIsOn()
        compose.onNodeWithTag("selected-common-engine:chatgpt").assertIsOn().assertIsNotEnabled()
        compose.onNodeWithTag("selected-common-engine:google").assertIsOn().assertIsNotEnabled()
    }

    @Test fun chooseOverseasCombinationRecreateAndRemoveAnEngine() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        openSearch()
        compose.onNodeWithTag("suggestion:primary").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:engine:baidu").assertDoesNotExist()
        compose.onNodeWithTag("suggestion:engine:so360").assertDoesNotExist()
        screenshot("default-google-chatgpt")
        compose.onNode(hasSetTextAction()).performTextReplacement("360 android tablet")
        compose.onNodeWithText(strings(R.string.ui_search_1_s_for_2_s, "Google", "360 android tablet")).assertIsDisplayed()
        compose.onNodeWithText("Esc").performClick()

        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        val list = compose.onNodeWithTag("search-engines-list")
        list.performScrollToNode(hasTestTag("common-engine:baidu"))
        compose.onNodeWithTag("common-engine:baidu").assertIsOff().performClick()
        compose.waitUntil(10_000) { runBlocking { "baidu" in container.settings.settings.first().commonSearchEngineIds.orEmpty() } }
        compose.onNodeWithTag("common-engine:baidu").assertIsOn()
        list.performScrollToNode(hasTestTag("common-engine:google"))
        compose.onNodeWithTag("common-engine:google").assertIsOn().assertIsNotEnabled()
        screenshot("selected-engines")
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_settings)).performClick()
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_browser)).performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        openSearch()
        compose.onNodeWithTag("suggestion:primary").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:engine:baidu").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:engine:so360").assertDoesNotExist()
        screenshot("google-chatgpt-baidu-after-recreate")
        compose.onNodeWithText("Esc").performClick()

        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        compose.onNodeWithTag("search-engines-list").performScrollToNode(hasTestTag("common-engine:baidu"))
        compose.onNodeWithTag("common-engine:baidu").performClick()
        compose.waitUntil(10_000) { runBlocking { "baidu" !in container.settings.settings.first().commonSearchEngineIds.orEmpty() } }
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_settings)).performClick()
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_browser)).performClick()
        openSearch()
        compose.onNodeWithTag("suggestion:engine:baidu").assertDoesNotExist()
    }
}
