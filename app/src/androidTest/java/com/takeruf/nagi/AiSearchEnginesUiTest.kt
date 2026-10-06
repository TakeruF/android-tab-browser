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

class AiSearchEnginesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private val strings get() = NagiStrings(compose.activity)

    @Before fun setup() = runBlocking {
        container = (compose.activity.application as NagiApplication).container
        container.workspace.ready.await()
        original = container.settings.settings.first()
        container.settings.update { it.copy(defaultSearchEngineId = "google", defaultAiEngineId = "chatgpt", commonSearchEngineIds = setOf("google"),
            automaticSearchRegion = false, theme = ThemeMode.LIGHT) }
    }

    @After fun cleanup() = runBlocking { container.settings.update { original } }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ai-search-validation").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun defaultAiPickerPersistsAndRanksLongInputFirst() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithText(strings(R.string.ui_default_search_engine)).assertIsDisplayed()
        compose.onNodeWithTag("default-ai-engine").performClick()
        compose.onNodeWithText("Perplexity").performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().defaultAiEngineId == "perplexity" } }
        assertEquals("google", runBlocking { container.settings.settings.first().defaultSearchEngineId })
        screenshot("separate-defaults")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("Explain " + "this topic in detail ".repeat(5))
        compose.onNodeWithTag("suggestion:ai:perplexity").assertIsDisplayed()
        compose.onNodeWithTag("suggestion:primary").assertIsDisplayed()
        val aiBounds = compose.onNodeWithTag("suggestion:ai:perplexity").fetchSemanticsNode().boundsInRoot
        val searchBounds = compose.onNodeWithTag("suggestion:primary").fetchSemanticsNode().boundsInRoot
        assertTrue(aiBounds.top < searchBounds.top)
        screenshot("default-ai-long-input")
        compose.onNode(hasSetTextAction()).performTextReplacement("android tablet")
        assertTrue(compose.onNodeWithTag("suggestion:primary").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithTag("suggestion:ai:perplexity").fetchSemanticsNode().boundsInRoot.top)
        screenshot("default-search-short-input")
    }

    @Test fun settingsShowRegionAndCommandBarUsesShortNames() {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        val list = compose.onNodeWithTag("search-engines-list")
        list.performScrollToNode(hasTestTag("common-engine:qwen"))
        compose.onNodeWithText(strings(R.string.ui_qwen_china_mainland)).assertIsDisplayed()
        compose.onNodeWithTag("common-engine:qwen").assertIsOff().performClick()
        screenshot("qwen-settings")
        list.performScrollToNode(hasTestTag("common-engine:perplexity"))
        compose.onNodeWithText("Perplexity").assertIsDisplayed()
        compose.onNodeWithTag("common-engine:perplexity").assertIsOff().performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().commonSearchEngineIds.orEmpty().containsAll(setOf("qwen", "perplexity")) } }
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_settings)).performClick()
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_browser)).performClick()
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("android tablet")
        compose.onNodeWithTag("suggestion:engine:qwen").assertIsDisplayed()
        compose.onNodeWithText(strings(R.string.ui_ask_engine_about_query, strings(R.string.ui_qwen), "android tablet")).assertIsDisplayed()
        compose.onNodeWithText(strings(R.string.ui_ask_engine_about_query, "Perplexity", "android tablet")).assertIsDisplayed()
        compose.onNodeWithText(strings(R.string.ui_qwen_china_mainland), substring = true).assertDoesNotExist()
        screenshot("ai-search-suggestions")
        compose.onNode(hasSetTextAction()).performTextReplacement("qwen 日本 & 中文")
        compose.onNodeWithText(strings(R.string.ui_ask_engine_about_query, strings(R.string.ui_qwen), "日本 & 中文")).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("pplx 日本 & 中文")
        compose.onNodeWithText(strings(R.string.ui_ask_engine_about_query, "Perplexity", "日本 & 中文")).assertIsDisplayed()
    }
    @Test fun engineListDistinguishesAiDefaultAndAllowsChatGptSelection() {
        runBlocking {
            container.settings.update { it.copy(defaultSearchEngineId = "baidu", defaultAiEngineId = "qwen",
                searchRegionCountry = "CN", commonSearchEngineIds = null, automaticSearchRegion = false) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithTag("customize-search-engines").performClick()
        compose.onNodeWithTag("selected-common-engine:baidu").assert(hasText(strings(R.string.ui_search_default_badge)))
        compose.onNodeWithTag("selected-common-engine:qwen").assert(hasText(strings(R.string.ui_ai_default_badge)))
        compose.onNodeWithTag("selected-common-engine:chatgpt").assert(hasText(strings(R.string.ui_ai_engine_badge)))
        screenshot("explicit-ai-default-mainland")
        compose.onNodeWithTag("search-engines-list").performScrollToNode(hasTestTag("make-default:chatgpt"))
        compose.onNodeWithTag("make-default:chatgpt").assertIsEnabled().performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().defaultAiEngineId == "chatgpt" } }
        assertEquals("baidu", runBlocking { container.settings.settings.first().defaultSearchEngineId })
        compose.onNodeWithTag("make-default:chatgpt").assertIsNotEnabled()
        screenshot("explicit-ai-default-chatgpt")
    }

    @Test fun mainlandDefaultsShowBaiduAndQwenWithoutChatGpt() {
        runBlocking {
            container.settings.update { it.copy(defaultSearchEngineId = "baidu", defaultAiEngineId = null, searchRegionCountry = "CN",
                commonSearchEngineIds = null, automaticSearchRegion = false) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(strings(R.string.ui_settings)).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("android tablet")
        compose.onNodeWithText(strings(R.string.ui_search_1_s_for_2_s, "百度", "android tablet")).assertIsDisplayed()
        compose.onNodeWithText(strings(R.string.ui_ask_engine_about_query, strings(R.string.ui_qwen), "android tablet")).assertIsDisplayed()
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertDoesNotExist()
        compose.onNodeWithTag("suggestion:engine:google").assertDoesNotExist()
        screenshot("mainland-default-baidu-qwen")
    }

}
