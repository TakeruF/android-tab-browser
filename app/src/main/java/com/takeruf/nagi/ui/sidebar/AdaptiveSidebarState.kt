package com.takeruf.nagi.ui.sidebar

import androidx.compose.runtime.saveable.listSaver

/** Window adaptation is temporary; the saved sidebar preference remains independent. */
data class AdaptiveSidebarState(
    val compact: Boolean = false,
    val compactCollapsed: Boolean? = null,
) {
    fun forWidth(widthDp: Float): AdaptiveSidebarState {
        val nextCompact = if (compact) widthDp < 640f else widthDp < 600f
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
