package com.takeruf.nagi.ui.sidebar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlin.math.abs

/** An invisible boundary target; only an active resize reveals the neutral guide. */
@Composable
internal fun SidebarResizeHandle(settings: BrowserSettings, onPreview: (Float?, Boolean?) -> Unit,
    onCommit: (Float, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val currentSettings by rememberUpdatedState(settings)
    val preview by rememberUpdatedState(onPreview)
    val commit by rememberUpdatedState(onCommit)
    var originX by remember { mutableFloatStateOf(0f) }
    var resizing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cursor = remember(context) { PointerIcon(android.view.PointerIcon.getSystemIcon(context,
        android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)) }
    Box(modifier.testTag("sidebar-resize-handle")
        .onGloballyPositioned { originX = it.positionInRoot().x }
        .pointerHoverIcon(cursor)
        .pointerInput(density) {
            awaitEachGesture {
                val down = awaitFirstDown()
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val downX = originX + down.position.x
                val startWidth = currentSettings.sidebarWidth
                val startCollapsed = currentSettings.sidebarCollapsed
                val slop = if (down.type == PointerType.Mouse) 1f else viewConfiguration.touchSlop
                var dragging = false
                var nextWidth = startWidth
                var nextCollapsed = startCollapsed
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        val delta = originX + change.position.x - downX
                        if (abs(delta) > slop) dragging = true
                        if (dragging) {
                            resizing = true
                            val raw = (if (startCollapsed) 72f else startWidth) + delta / density.density
                            nextCollapsed = raw <= (if (startCollapsed) 102f else 220f)
                            nextWidth = raw.coerceIn(220f, 380f)
                            preview(nextWidth, nextCollapsed)
                        }
                        val released = change.changedToUp()
                        change.consume()
                        if (!change.pressed) {
                            if (released && dragging) commit(if (nextCollapsed) startWidth else nextWidth, nextCollapsed)
                            break
                        }
                    }
                } finally {
                    resizing = false
                    preview(null, null)
                }
            }
        }, contentAlignment = Alignment.Center) {
        if (resizing) Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 16.dp)
            .background(Color(0xFF808080), RoundedCornerShape(2.dp)).testTag("sidebar-resize-indicator"))
    }
}
