package com.takeruf.nagi.domain.model

import kotlin.math.abs

/** Shared selection policy for the transactional close and its visual prediction. */
object TabCloseDestination {
    fun next(closing: BrowserTab, tabs: List<BrowserTab>): BrowserTab? {
        val remaining = tabs.filter { it.id != closing.id && it.spaceId == closing.spaceId &&
            it.closedAt == null && it.archivedAt == null }
        return remaining.firstOrNull { it.id == closing.parentTabId }
            ?: remaining.minByOrNull { abs(it.position.toLong() - closing.position) }
    }
}
