package com.takeruf.nagi

import android.app.Application
import android.content.res.Configuration
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.localization.NagiStrings
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalizationTest {
    @Test fun resourcesFormatAndCommandsMatchInAllFourLanguages() {
        for ((language, label) in listOf("en" to "New tab", "ja" to "新しいタブ", "ko" to "새 탭", "zh-CN" to "新标签页")) {
            val configuration = Configuration().apply { setLocale(Locale.forLanguageTag(language)) }
            val context = RuntimeEnvironment.getApplication().createConfigurationContext(configuration)
            val strings = NagiStrings(context)
            assertEquals(label, strings(R.string.ui_new_tab))
            val qwen = DefaultSearchEngines.all.first { it.id == "qwen" }
            val shortName = if (language in listOf("ja", "zh-CN")) "千问" else "Qwen"
            val settingsName = when (language) {
                "ja", "zh-CN" -> "千问（中国大陆）"
                "ko" -> "Qwen (중국 본토)"
                else -> "Qwen (China Mainland)"
            }
            assertEquals(shortName, strings.engineName(qwen))
            assertEquals(settingsName, strings.engineName(qwen, inSettings = true))
            assertEquals("My Qwen", strings.engineName(qwen.copy(name = "My Qwen"), inSettings = true))
            val qwenSearch = SuggestionProvider.suggestions("qwen 日本 & 中文", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                BrowserSettings(commonSearchEngineIds = setOf("google", "qwen", "perplexity")), strings::translate)
            assertTrue(qwenSearch.first().title.contains(shortName))
            assertFalse(qwenSearch.first().title.contains(settingsName))
            assertTrue(qwenSearch.any { it.id == "engine:perplexity" && it.title.contains("Perplexity") })
            val alternate = SuggestionProvider.suggestions("test", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                BrowserSettings(commonSearchEngineIds = setOf("google", "qwen")), strings::translate)
            assertTrue(alternate.first { it.id == "engine:qwen" }.title.contains(shortName))
            val aiSuggestions = SuggestionProvider.suggestions("日本 & 中文", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                BrowserSettings(commonSearchEngineIds = setOf("google", "qwen", "perplexity", "chatgpt")), strings::translate)
            for ((id, name) in listOf("engine:qwen" to shortName, "engine:perplexity" to "Perplexity", "ai:chatgpt" to "ChatGPT")) {
                val expected = when (language) {
                    "ja" -> "${name}に「日本 & 中文」を聞く"
                    "zh-CN" -> "向 $name 提问：“日本 & 中文”"
                    "ko" -> "${name}에 “日本 & 中文” 질문하기"
                    else -> "Ask $name about “日本 & 中文”"
                }
                assertEquals(expected, aiSuggestions.first { it.id == id }.title)
                if (id != "ai:chatgpt") {
                    val engine = DefaultSearchEngines.all.first { "engine:${it.id}" == id }
                    val explicit = SuggestionProvider.suggestions("${engine.keyword} 日本 & 中文", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                        BrowserSettings(commonSearchEngineIds = setOf("google", "qwen", "perplexity")), strings::translate)
                    assertEquals(expected, explicit.first().title)
                }
            }
            val tab = BrowserTab("test", "personal")
            assertEquals(label, strings.tabTitle(tab))
            assertEquals("User's name", strings.spaceName(Space("custom", "User's name")))
            assertEquals("User's name", strings.spaceName(Space("personal", "User's name")))
            val command = SuggestionProvider.suggestions(">" + label, WorkspaceSnapshot(), BrowserSettings(), strings::translate)
            assertTrue(command.any { it.action == SuggestionAction.Command(BrowserCommand.NEW_TAB) && it.title == label })
            val search = SuggestionProvider.suggestions("dy 日英 한국 中文", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                BrowserSettings(commonSearchEngineIds = setOf("google", "chatgpt", "douyin")), strings::translate).first()
            assertTrue(search.title.contains("抖音"))
            assertTrue(search.title.contains("日英 한국 中文"))
            assertFalse(search.title.contains("%1"))
            if (language != "en") assertNotEquals("Name is required", strings.translate("Name is required"))
        }
    }
}
