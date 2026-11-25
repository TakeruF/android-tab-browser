package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.takeruf.nagi.ui.components.LocalNagiMenuDimensions
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.components.NagiOverflowMenu
import com.takeruf.nagi.ui.components.nagiMenuDimensions

/** Shared by the pane and the compact footer; contains no native page references. */
@Stable
class BrowserPageMenuState {
    var tabId by mutableStateOf<String?>(null)
    var bottomBarBounds by mutableStateOf<Rect?>(null)
    fun toggle(id: String?) { tabId = id.takeUnless { it == tabId } }
}

@Composable
internal fun BrowserPageMenu(
    expanded: Boolean,
    compactWindow: Boolean,
    onDismiss: () -> Unit,
    bottomBarBounds: Rect?,
    hostBounds: Rect?,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!compactWindow) {
        NagiOverflowMenu(expanded, onDismiss, content = content)
        return
    }
    if (!expanded) return
    val dimensions = nagiMenuDimensions()
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    val toolbar = with(density) { LocalNagiUiDimensions.current.toolbarHeight.roundToPx() }
    val gap = with(density) { 8.dp.roundToPx() }
    val overlap = with(density) { 12.dp.roundToPx() }
    val menuWidth = with(density) { (windowSize.width.toDp() * 0.72f).coerceAtMost(280.dp) }
    val maxHeight = with(density) {
        (windowSize.height - topInset - bottomInset - toolbar - gap * 2)
            .coerceAtLeast(1).toDp().coerceAtMost(640.dp)
    }
    val position = remember(topInset, bottomInset, toolbar, gap, overlap, bottomBarBounds, hostBounds) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                // Translate the actual bar into the popup anchor's window coordinates.
                val barTop = if (bottomBarBounds != null && hostBounds != null)
                    bottomBarBounds.top.toInt() + anchorBounds.top - hostBounds.top.toInt()
                else windowSize.height - bottomInset - toolbar
                return IntOffset(
                    (windowSize.width - popupContentSize.width - gap).coerceAtLeast(gap),
                    (barTop + overlap - popupContentSize.height).coerceAtLeast(topInset + gap),
                )
            }
        }
    }
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)) {
        CompositionLocalProvider(LocalNagiMenuDimensions provides dimensions,
            LocalMinimumInteractiveComponentSize provides dimensions.rowHeight) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp, shadowElevation = 6.dp,
                modifier = Modifier.width(menuWidth).heightIn(max = maxHeight).testTag("compact-page-menu")) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).testTag("compact-page-tools"), content = content)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    actions()
                }
            }
        }
    }
}
