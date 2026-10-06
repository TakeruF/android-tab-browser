package com.takeruf.nagi

import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.browser.search.ResolvedInput
import com.takeruf.nagi.domain.model.BrowserSettings
import org.junit.Assert.*
import org.junit.Test

class AiSearchEnginesTest {
    @Test fun automaticRegionOverridesSavedAiChoice() {
        for (country in listOf("CN", "JP", "HK", "MO", "TW", "US")) {
            val expected = if (country == "CN") "qwen" else "chatgpt"
            val settings = BrowserSettings(searchRegionCountry = country, defaultAiEngineId = "perplexity")
            assertEquals(expected, AiSearchEngines.default(settings, DefaultSearchEngines.all).id)
            assertEquals("perplexity", AiSearchEngines.defaultId(settings.copy(automaticSearchRegion = false)))
        }
    }

    @Test fun selectedAiKeywordsUseRequestedTemplatesWithEncodedQueries() {
        val settings = BrowserSettings(commonSearchEngineIds = setOf("google", "qwen", "perplexity"))
        for ((keyword, expected) in listOf(
            "qwen" to "https://www.qianwen.com/?q=",
            "pplx" to "https://www.perplexity.ai/search/?q="
        )) {
            val result = InputResolver.resolve("$keyword 日本 & 中文", DefaultSearchEngines.all, settings) as ResolvedInput.Search
            assertEquals(expected + "%E6%97%A5%E6%9C%AC+%26+%E4%B8%AD%E6%96%87", result.url)
            assertNull(InputResolver.validateEngine(result.engine))
        }
    }
    @Test fun selectedDefaultAiLeadsLongQueriesAndSearchLeadsShortQueries() {
        val workspace = WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all)
        for (aiId in listOf("chatgpt", "qwen", "perplexity")) {
            val settings = BrowserSettings(defaultSearchEngineId = "bing", defaultAiEngineId = aiId, automaticSearchRegion = false,
                commonSearchEngineIds = setOf("google", "qwen", "perplexity", "chatgpt"))
            for ((query, first) in listOf("weather" to "primary", "あ".repeat(80) to "ai:$aiId", "😀".repeat(80) to "ai:$aiId")) {
                val suggestions = SuggestionProvider.suggestions(query, workspace, settings)
                    .filter { it.category == SuggestionCategory.SEARCH }
                assertEquals(first, suggestions.first().id)
                val ai = suggestions.first { it.id == "ai:$aiId" }
                assertEquals(InputResolver.search(AiSearchEngines.default(settings, workspace.searchEngines), query).url,
                    (ai.action as SuggestionAction.Navigate).url)
                assertEquals(suggestions.size, suggestions.map { (it.action as SuggestionAction.Navigate).url }.distinct().size)
            }
            assertEquals("primary", SuggestionProvider.suggestions("b " + "あ".repeat(80), workspace, settings).first().id)
        }
    }
}
