package com.takeruf.nagi.ui.sidebar

import androidx.compose.runtime.saveable.listSaver

/** Compact windows use a temporary tab sheet; the saved wide sidebar preference stays independent. */
data class AdaptiveSidebarState(
    val compact: Boolean = false,
    val compactCollapsed: Boolean? = null,
) {
    fun forWidth(widthDp: Float): AdaptiveSidebarState {
        // Use one boundary in both directions: the same window must have the same UI
        // and automatic site mode, regardless of its previous size or saved state.
        val nextCompact = widthDp < 600f
        return if (nextCompact == compact) this else AdaptiveSidebarState(compact = nextCompact)
    }

    fun isCollapsed(preferredCollapsed: Boolean): Boolean =
        if (compact) compactCollapsed ?: true else preferredCollapsed

    companion object {
        val Saver = listSaver<AdaptiveSidebarState, Int>(
            save = { listOf(if (it.compact) 1 else 0, when (it.compactCollapsed) {
                null -> -1
                false -> 0
                true -> 1
            }) },
            restore = { AdaptiveSidebarState(it[0] == 1, when (it[1]) {
                -1 -> null
                0 -> false
                else -> true
            }) },
        )
    }
}
