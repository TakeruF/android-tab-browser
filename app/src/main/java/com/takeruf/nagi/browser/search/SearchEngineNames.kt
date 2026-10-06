package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.SearchEngine

object SearchEngineNames {
    fun display(engine: SearchEngine, inSettings: Boolean = false, localize: (String) -> String = { it }): String =
        if (engine.id == "qwen" && engine.name == "Qwen") {
            localize(if (inSettings) "Qwen (China Mainland)" else "Qwen")
        } else engine.name
}
