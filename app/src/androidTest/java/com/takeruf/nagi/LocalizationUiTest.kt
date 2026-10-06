package com.takeruf.nagi

import android.app.LocaleManager
import android.os.LocaleList
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.localization.NagiStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

@SdkSuppress(minSdkVersion = 33)
class LocalizationUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalLocales: LocaleList
    private lateinit var spaceId: String

    @Before fun setup() {
        container = (context.applicationContext as NagiApplication).container
        originalLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create("Language QA 日本 한국 中文")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
    }
    @After fun cleanup() {
        if (::scenario.isInitialized) scenario.close()
        context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales
        runBlocking { container.spaces.delete(spaceId); container.settings.update { original } }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val directory = File(activity.getExternalFilesDir(null), "localization").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun verify(language: String, home: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(home).fetchSemanticsNodes().isNotEmpty() }
        val strings = NagiStrings(activity)
        compose.onNodeWithContentDescription(strings(R.string.ui_tab_1_s, strings(R.string.ui_new_tab))).assertIsDisplayed()
        compose.onAllNodesWithText("Language QA 日本 한국 中文").onFirst().assertIsDisplayed()
        screenshot("$language-home")
        compose.onNodeWithContentDescription(strings(R.string.ui_settings)).performClick()
        compose.onNodeWithText(strings(R.string.ui_settings)).assertIsDisplayed()
        val list = compose.onNodeWithTag("settings-list")
        list.performScrollToNode(hasTestTag("customize-search-engines"))
        compose.onNodeWithContentDescription(strings(R.string.ui_edit_1_s, "搜狗")).assertDoesNotExist()
        screenshot("$language-settings-main")
        compose.onNodeWithTag("customize-search-engines").performClick()
        val enginesList = compose.onNodeWithTag("search-engines-list")
        for (engine in listOf("搜狗", "360", "抖音", "神马")) {
            val description = strings(R.string.ui_make_1_s_default, engine)
            enginesList.performScrollToNode(hasContentDescription(description))
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
        screenshot("$language-search-engines")
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_settings)).performClick()
        compose.onNodeWithTag("customize-search-engines").assertIsDisplayed()
        compose.onNodeWithTag("customize-search-engines").performClick()
        compose.onNodeWithTag("search-engines-list").assertIsDisplayed()
        compose.waitForIdle()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings-list").assertIsDisplayed()
        compose.onNodeWithTag("search-engines-list").assertDoesNotExist()
        list.performScrollToNode(hasText(strings(R.string.ui_theme_color)))
        compose.onNodeWithText(strings(R.string.ui_dark)).performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().theme == ThemeMode.DARK } }
        screenshot("$language-settings")
        compose.onNodeWithContentDescription(strings(R.string.ui_back_to_browser)).performClick()
        compose.onNodeWithText(strings(R.string.ui_search_anything)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(">" + strings(R.string.ui_open_settings))
        compose.onNodeWithText(strings(R.string.ui_open_settings)).assertIsDisplayed()
        screenshot("$language-commands")
        compose.onNodeWithText("Esc").performClick()
        assertEquals(language.substringBefore('-'), activity.resources.configuration.locales[0].language)
    }
    @Test fun english() = verify("en", "Where would you like to go?")
    @Test fun japanese() = verify("ja", "どこを開きますか？")
    @Test fun korean() = verify("ko", "어디로 이동할까요?")
    @Test fun chinese() = verify("zh-CN", "想去哪里？")
}
