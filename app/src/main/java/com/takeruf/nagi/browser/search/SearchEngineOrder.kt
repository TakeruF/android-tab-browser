package com.takeruf.nagi.browser.search

object SearchEngineOrder {
    private val leadingIds = listOf("google", "bing", "baidu", "chatgpt", "perplexity", "qwen")

    // Keep the remaining engines in their existing order, including custom engines.
    fun <T> sorted(items: List<T>, selectedIds: Set<String> = emptySet(), id: (T) -> String): List<T> =
        items.sortedWith(compareBy<T> { id(it) !in selectedIds }
            .thenBy { leadingIds.indexOf(id(it)).takeIf { index -> index >= 0 } ?: leadingIds.size })
}
