package com.takeruf.nagi.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** App chrome spacing; typography, display density and website zoom remain independent. */
@Immutable
data class NagiUiDimensions(val compact: Boolean = false) {
    val controlSize = if (compact) 36.dp else 48.dp
    val collapsedSidebarWidth = if (compact) 52.dp else 72.dp
    val rowHeight = if (compact) 40.dp else 48.dp
    val toolbarHeight = if (compact) 48.dp else 56.dp
    val categoryHeight = if (compact) 40.dp else 56.dp
    val categoryWidth = 236.dp
    val settingsRowHeight = if (compact) 48.dp else 64.dp
    val rowHorizontalPadding = if (compact) 12.dp else 20.dp
    val rowVerticalPadding = if (compact) 6.dp else 12.dp
    val actionVerticalPadding = if (compact) 8.dp else 16.dp
    val sectionInset = if (compact) 12.dp else 16.dp
    val sectionGap = if (compact) 12.dp else 16.dp
    val headerVerticalPadding = if (compact) 8.dp else 16.dp
    val pagePadding = if (compact) 16.dp else 28.dp
    val favoriteHeight = if (compact) 48.dp else 58.dp
    fun gutter(wide: Boolean) = if (wide) (if (compact) 20.dp else 32.dp) else sectionInset
}

val LocalNagiUiDimensions = staticCompositionLocalOf { NagiUiDimensions() }
