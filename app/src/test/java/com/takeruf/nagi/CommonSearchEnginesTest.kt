package com.takeruf.nagi

import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CommonSearchEnginesTest {
    private val workspace = WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all)
    private fun search(settings: BrowserSettings, query: String = "android tablet") =
        SuggestionProvider.suggestions(query, workspace, settings).filter { it.category == SuggestionCategory.SEARCH }

    @Test fun defaultsShowRegionalEngineAndRegionalAi() {
        for (country in listOf("GB", "JP", "HK", "MO", "TW", "CN")) {
            val engine = RegionalSearchPolicy.engine(country)
            val result = search(BrowserSettings(defaultSearchEngineId = engine, searchRegionCountry = country))
            assertEquals(listOf("primary", if (country == "CN") "ai:qwen" else "ai:chatgpt"), result.map { it.id })
            assertEquals(InputResolver.search(workspace.searchEngines.first { it.id == engine }, "android tablet").url,
                (result.first().action as SuggestionAction.Navigate).url)
        }
    }

    @Test fun mainlandManualCombinationOverridesRegionalAiDefault() {
        val settings = BrowserSettings(defaultSearchEngineId = "baidu", searchRegionCountry = "CN")
        assertEquals(setOf("baidu", "qwen"), CommonSearchEngines.ids(settings))
        val selected = CommonSearchEngines.select(settings, "chatgpt", true)
        assertEquals(setOf("baidu", "qwen", "chatgpt"), selected.commonSearchEngineIds)
        assertFalse(selected.automaticSearchRegion)
        val withoutQwen = CommonSearchEngines.select(selected.copy(defaultAiEngineId = "chatgpt"), "qwen", false)
        assertEquals(listOf("primary", "ai:chatgpt"), search(withoutQwen).map { it.id })
        assertEquals(setOf("baidu", "chatgpt"), CommonSearchEngines.ids(withoutQwen.copy(searchRegionCountry = "JP")))
        assertEquals(listOf("ai:qwen", "primary"), search(settings, "Explain inflation?").map { it.id })
    }

    @Test fun overseasChineseCombinationIncludesBaiduGoogleAndChatGptOnly() {
        val settings = CommonSearchEngines.select(BrowserSettings(), "baidu", true)
        assertFalse(settings.automaticSearchRegion)
        assertEquals(setOf("google", "baidu", "chatgpt"), settings.commonSearchEngineIds)
        assertEquals(listOf("primary", "ai:chatgpt", "engine:baidu"), search(settings).map { it.id })
    }

    @Test fun bothDefaultsCannotBeRemoved() {
        val settings = BrowserSettings()
        assertEquals(settings, CommonSearchEngines.select(settings, "chatgpt", false))
        assertEquals(settings, CommonSearchEngines.select(settings, "google", false))
        assertEquals(listOf("ai:chatgpt", "primary"), search(settings, "Explain inflation?").map { it.id })
    }

    @Test fun unselectedKeywordIsOrdinaryQueryUntilEngineIsSelected() {
        val settings = BrowserSettings()
        val query = "360 android tablet"
        val plain = InputResolver.resolve(query, workspace.searchEngines, settings) as ResolvedInput.Search
        assertEquals("google", plain.engine.id)
        assertEquals(query, plain.query)
        val result = search(CommonSearchEngines.select(settings, "so360", true), query)
        assertEquals("primary", result.first().id)
        assertTrue((result.first().action as SuggestionAction.Navigate).url.startsWith("https://www.so.com/"))
        assertFalse(search(BrowserSettings()).any { it.id == "engine:so360" })
    }

    @Test fun allSelectedEnginesAreShownWithoutArbitraryLimitAndStaleIdsAreIgnored() {
        val selected = DefaultSearchEngines.all.map { it.id }.toSet() + "deleted"
        val result = search(BrowserSettings(commonSearchEngineIds = selected))
        assertEquals(DefaultSearchEngines.all.size + 1, result.size)
        assertEquals(result.size, result.map { it.id }.distinct().size)
    }
}
