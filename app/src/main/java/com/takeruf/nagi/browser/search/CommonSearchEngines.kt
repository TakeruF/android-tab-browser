package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.BrowserSettings

object CommonSearchEngines {
    const val CHATGPT = "chatgpt"
    const val QWEN = "qwen"

    // Both defaults remain available regardless of the optional common set.
    fun ids(settings: BrowserSettings): Set<String> =
        settings.commonSearchEngineIds.orEmpty() + settings.defaultSearchEngineId + AiSearchEngines.defaultId(settings)

    fun select(settings: BrowserSettings, id: String, enabled: Boolean): BrowserSettings {
        if (id in setOf(settings.defaultSearchEngineId, AiSearchEngines.defaultId(settings)) && !enabled) return settings
        val current = ids(settings)
        return settings.copy(
            commonSearchEngineIds = if (enabled) current + id else current - id,
            automaticSearchRegion = false,
        )
    }
}
