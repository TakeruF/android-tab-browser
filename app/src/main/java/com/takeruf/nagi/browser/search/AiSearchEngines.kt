package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.SearchEngine

object AiSearchEngines {
    val chatGpt = SearchEngine("chatgpt", "ChatGPT", "chatgpt", "https://chatgpt.com/?q={query}")
    val ids = DefaultSearchEngines.aiIds + chatGpt.id

    fun defaultId(settings: BrowserSettings): String {
        val regionalDefault = if (settings.searchRegionCountry == "CN") "qwen" else "chatgpt"
        return if (settings.automaticSearchRegion) regionalDefault
        else settings.defaultAiEngineId ?: regionalDefault
    }

    fun available(engines: List<SearchEngine>): List<SearchEngine> =
        listOf(chatGpt) + engines.filter { it.id in DefaultSearchEngines.aiIds }

    fun default(settings: BrowserSettings, engines: List<SearchEngine>): SearchEngine =
        available(engines).firstOrNull { it.id == defaultId(settings) } ?: chatGpt
}
