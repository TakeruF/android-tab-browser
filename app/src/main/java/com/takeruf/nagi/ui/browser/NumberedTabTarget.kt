package com.takeruf.nagi.ui.browser

sealed interface NumberedTabTarget {
    data class Favorite(val url: String) : NumberedTabTarget
    data class Tab(val id: String) : NumberedTabTarget
}

fun BrowserUiState.numberedTabTarget(number: Int): NumberedTabTarget? {
    if (number !in 1..9 || currentSpace == null) return null
    val index = number - 1
    favorites.getOrNull(index)?.let { return NumberedTabTarget.Favorite(it.url) }
    // Stable partition: pinned rows precede normal rows, preserving each section's order.
    val tabs = visibleTabs.filter { it.isPinned } + visibleTabs.filter { !it.isPinned }
    return tabs.getOrNull(index - favorites.size)?.let { NumberedTabTarget.Tab(it.id) }
}
