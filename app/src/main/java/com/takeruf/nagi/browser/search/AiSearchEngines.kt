package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.SearchEngine

object AiSearchEngines {
    val chatGpt = SearchEngine("chatgpt", "ChatGPT", "chatgpt", "https://chatgpt.com/?q={query}")
    val ids = DefaultSearchEngines.aiIds + chatGpt.id

    fun defaultId(settings: BrowserSettings): String = settings.defaultAiEngineId
        ?: if (settings.searchRegionCountry == "CN") "qwen" else "chatgpt"

    fun available(engines: List<SearchEngine>): List<SearchEngine> =
        listOf(chatGpt) + engines.filter { it.id in DefaultSearchEngines.aiIds }

    fun default(settings: BrowserSettings, engines: List<SearchEngine>): SearchEngine =
        available(engines).firstOrNull { it.id == defaultId(settings) } ?: chatGpt
}
