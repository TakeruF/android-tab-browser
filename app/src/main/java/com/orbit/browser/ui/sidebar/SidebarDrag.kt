package com.orbit.browser.ui.sidebar

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay

enum class SidebarSection { FAVORITES, PINNED, TODAY, SPACE }
data class SidebarDestination(val section: SidebarSection, val id: String? = null, val after: Boolean = false)
data class SidebarDragItem(val id: String, val title: String, val favicon: String?, val favorite: Boolean = false)

@Stable
class SidebarDragState {
    var item by mutableStateOf<SidebarDragItem?>(null)
    var point by mutableStateOf(Offset.Zero)
    var destination by mutableStateOf<SidebarDestination?>(null)
    internal val sources = mutableMapOf<String, Triple<SidebarDragItem, Boolean, Rect>>()
    private val targets = mutableMapOf<String, Pair<SidebarDestination, Rect>>()
    fun bounds(key: String, destination: SidebarDestination, rect: Rect) { targets[key] = destination to rect }
    fun remove(key: String) { targets.remove(key) }
    fun start(value: SidebarDragItem, position: Offset) { item = value; move(position) }
    fun move(position: Offset) {
        point = position
        val hit = targets.values.filter { it.second.contains(position) }
            .minByOrNull { it.second.width * it.second.height }
        destination = hit?.let { (target, rect) ->
            target.copy(after = target.id != null && if (target.section == SidebarSection.FAVORITES)
                position.x > rect.center.x else position.y > rect.center.y)
        }
    }
    fun cancel() { item = null; destination = null }
}

@Composable
fun Modifier.sidebarTarget(drag: SidebarDragState, key: String, destination: SidebarDestination): Modifier {
    DisposableEffect(drag, key) { onDispose { drag.remove(key) } }
    return onGloballyPositioned { drag.bounds(key, destination, it.boundsInRoot()) }
}

/** Register rows; the stable sidebar owns the pointer so recycling a row cannot interrupt a drag. */
@Composable
fun Modifier.sidebarDraggable(drag: SidebarDragState, item: SidebarDragItem, immediate: Boolean = false): Modifier {
    val key = "${item.id}-$immediate"
    DisposableEffect(drag, key) { onDispose { drag.sources.remove(key) } }
    return onGloballyPositioned { drag.sources[key] = Triple(item, immediate, it.boundsInRoot()) }
}

@Composable
fun Modifier.sidebarDragHost(state: SidebarDragState, onStart: () -> Unit, onDrop: () -> Unit): Modifier {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val start by rememberUpdatedState(onStart)
    val drop by rememberUpdatedState(onDrop)
    return onGloballyPositioned { origin = it.boundsInRoot().topLeft }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val source = state.sources.values.filter { it.third.contains(origin + down.position) }
                .minByOrNull { it.third.width * it.third.height }
            if (source != null) {
                val press = if (source.second || down.type == PointerType.Mouse) {
                    var candidate: PointerInputChange? = null
                    while (candidate == null) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val slop = if (down.type == PointerType.Mouse) 1f else viewConfiguration.touchSlop
                        if ((change.position - down.position).getDistance() > slop) {
                            change.consume(); candidate = change
                        }
                    }
                    candidate
                } else awaitLongPressOrCancellation(down.id)
                if (press != null) {
                    press.consume()
                    start()
                    state.start(source.first, origin + press.position)
                    try {
                        while (true) {
                            // Intercept before the scrollable child once this is an active tab drag.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == press.id } ?: break
                            // Compose marks synthetic ACTION_CANCEL releases consumed. Never commit those.
                            if (change.isConsumed) break
                            state.move(origin + change.position)
                            val released = change.changedToUp()
                            change.consume()
                            if (!change.pressed) { if (released) drop(); break }
                        }
                    } finally { state.cancel() }
                }
            }
        }
    }
}

@Composable
fun SidebarAutoScroll(drag: SidebarDragState, list: LazyListState, viewport: Rect) {
    LaunchedEffect(drag.item, list, viewport) {
        while (drag.item != null) {
            val y = drag.point.y
            val edge = 64f
            val delta = when {
                !viewport.contains(drag.point) -> 0f
                y < viewport.top + edge -> -((viewport.top + edge - y) / edge).coerceIn(0f, 1f) * 18f
                y > viewport.bottom - edge -> ((y - viewport.bottom + edge) / edge).coerceIn(0f, 1f) * 18f
                else -> 0f
            }
            val canScroll = if (delta < 0f) list.canScrollBackward else list.canScrollForward
            if (delta != 0f && canScroll) {
                list.scrollBy(delta)
                drag.move(drag.point)
                withFrameNanos { }
            } else delay(16)
        }
    }
}

/** Secondary clicks open actions without selecting the row or starting a drag. */
@Composable
fun Modifier.sidebarContextMenu(onOpen: () -> Unit): Modifier {
    val open by rememberUpdatedState(onOpen)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type == PointerType.Mouse && currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                var canceled = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) canceled = true
                    if (!change.pressed) {
                        if (!canceled && change.changedToUp()) open()
                        change.consume()
                        break
                    }
                    change.consume()
                }
            }
        }
    }
}
