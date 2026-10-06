package com.takeruf.nagi.ui.sidebar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Claim a quick horizontal touch gesture before child clicks/scrolling, but leave tab drags alone. */
@Composable
fun Modifier.sidebarSpaceSwipe(
    drag: SidebarDragState,
    onStart: () -> Unit,
    onDrag: (delta: Float) -> Unit,
    onFinish: (forward: Boolean?) -> Unit,
): Modifier {
    val start by rememberUpdatedState(onStart)
    val move by rememberUpdatedState(onDrag)
    val finish by rememberUpdatedState(onFinish)
    var origin by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin = it.boundsInRoot().topLeft }.pointerInput(drag) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch || drag.item != null) return@awaitEachGesture
            // The dedicated favicon handle starts an immediate tab drag.
            if (drag.sources.values.any { it.second && it.third.contains(origin + down.position) }) return@awaitEachGesture
            var claimed = false
            val threshold = (size.width * 0.25f).coerceIn(24.dp.toPx(), 64.dp.toPx())
            var previousX = down.position.x
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (event.changes.any { it.id != down.id && it.pressed } || change.isConsumed || drag.item != null) break
                    val delta = change.position - down.position
                    if (!claimed) {
                        if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) break
                        if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) >= abs(delta.x)) break
                        if (abs(delta.x) > viewConfiguration.touchSlop && abs(delta.x) > abs(delta.y) * 1.4f) {
                            claimed = true
                            start()
                        }
                    }
                    if (claimed) {
                        move(change.position.x - previousX)
                        previousX = change.position.x
                        val released = change.changedToUp()
                        change.consume()
                        if (!change.pressed) {
                            finish(if (released && abs(delta.x) >= threshold) delta.x < 0f else null)
                            completed = true
                            break
                        }
                    } else if (!change.pressed) break
                }
            } finally {
                if (claimed && !completed) finish(null)
            }
        }
    }
}
