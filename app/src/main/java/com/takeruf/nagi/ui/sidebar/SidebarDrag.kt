package com.takeruf.nagi.ui.sidebar

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

enum class SidebarSection { FAVORITES, PINNED, TODAY, SPACE, SPLIT_LEFT, SPLIT_RIGHT, SPLIT_TAB }
data class SidebarDestination(val section: SidebarSection, val id: String? = null, val after: Boolean = false, val atStart: Boolean = false)
data class SidebarDragItem(val id: String, val title: String, val favicon: String?, val favorite: Boolean = false, val siteUrl: String? = null, val pairRight: com.takeruf.nagi.domain.model.BrowserTab? = null, val pairLeft: com.takeruf.nagi.domain.model.BrowserTab? = null, val pairActiveTabId: String? = null)

data class FavoriteLanding(val item: SidebarDragItem, val topLeft: Offset)

@Stable
class SidebarDragState {
    var item by mutableStateOf<SidebarDragItem?>(null)
    var point by mutableStateOf(Offset.Zero)
    var destination by mutableStateOf<SidebarDestination?>(null)
    var grabOffset by mutableStateOf(Offset.Zero)
        private set
    var sourceSize by mutableStateOf(Offset.Zero)
        private set
    var favoriteOrder by mutableStateOf<List<String>?>(null)
        private set
    var favoriteLanding by mutableStateOf<FavoriteLanding?>(null)
        private set
    fun finishFavoriteLanding() { favoriteLanding = null }
    private var favoriteIds = emptyList<String>()
    private var originalFavoriteIds = emptyList<String>()
    private var favoriteCommitted = false

    // Keep the optimistic order until Room emits the saved order, avoiding a release flicker.
    fun syncFavorites(ids: List<String>) {
        favoriteIds = ids
        if (item == null && (ids == favoriteOrder || ids.toSet() != favoriteOrder?.toSet())) {
            favoriteOrder = null
            favoriteCommitted = false
        }
    }
    private fun favoriteSlots() = targets.values
        .filter { it.first.section == SidebarSection.FAVORITES && it.first.id != null }
        .sortedWith(compareBy({ it.second.top }, { it.second.left }))

    fun commitFavoriteOrder() {
        favoriteCommitted = favoriteOrder != null
        val value = item ?: return
        val index = favoriteOrder?.indexOf(value.id) ?: return
        favoriteSlots().getOrNull(index)?.second?.let { favoriteLanding = FavoriteLanding(value, it.topLeft) }
    }
    internal val sources = mutableMapOf<String, Triple<SidebarDragItem, Boolean, Rect>>()
    private data class Target(val first: SidebarDestination, val second: Rect, val third: Boolean, val replacementRight: Boolean?)
    private val targets = mutableMapOf<String, Target>()
    fun bounds(key: String, destination: SidebarDestination, rect: Rect, canSplit: Boolean = true, replacementRight: Boolean? = null) {
        targets[key] = Target(destination, rect, canSplit, replacementRight)
    }
    fun remove(key: String) { targets.remove(key) }
    fun start(value: SidebarDragItem, position: Offset, bounds: Rect? = null) {
        favoriteLanding = null
        item = value
        grabOffset = bounds?.let { position - it.topLeft } ?: Offset.Zero
        sourceSize = bounds?.let { Offset(it.width, it.height) } ?: Offset.Zero
        originalFavoriteIds = favoriteIds
        favoriteCommitted = false
        move(position)
    }
    fun move(position: Offset) {
        point = position
        val hit = targets.values.filter { it.second.contains(position) }
            .minByOrNull { it.second.width * it.second.height }
        if (item?.favorite == true && hit?.first?.section == SidebarSection.FAVORITES) {
            // Hit-test stationary slots, never the animated tiles, so a resting pointer
            // cannot make neighbors repeatedly swap back and forth.
            val slots = favoriteSlots()
            val index = slots.indexOfFirst { it.second.contains(position) }.takeIf { it >= 0 }
                ?: slots.indices.minByOrNull { (slots[it].second.center - position).getDistanceSquared() }
            val id = item!!.id
            val remaining = originalFavoriteIds.filter { it != id }.toMutableList()
            if (index != null && id in originalFavoriteIds) {
                val insertion = index.coerceAtMost(remaining.size)
                // The repository expects an anchor in the original list, with the dragged ID removed.
                destination = remaining.getOrNull(insertion)?.let { SidebarDestination(SidebarSection.FAVORITES, it) }
                    ?: SidebarDestination(SidebarSection.FAVORITES, remaining.lastOrNull(), after = true)
                remaining.add(insertion, id)
                favoriteOrder = remaining
                return
            }
        }
        if (item?.favorite == true) favoriteOrder = null
        if (item?.pairRight != null && hit?.first?.section !in listOf(SidebarSection.PINNED, SidebarSection.TODAY, SidebarSection.SPACE)) {
            destination = null
            return
        }
        destination = hit?.let { (target, rect, canSplit, replacementRight) ->
            // The middle combines standalone tabs or replaces a split member; edges reorder.
            if ((canSplit || replacementRight != null) && target.section in listOf(SidebarSection.PINNED, SidebarSection.TODAY) &&
                target.id != null && target.id != item?.id && item?.favorite == false && item?.pairRight == null &&
                position.y >= rect.top + rect.height * 0.25f &&
                position.y <= rect.bottom - rect.height * 0.25f) {
                if (replacementRight != null) SidebarDestination(
                    if (replacementRight) SidebarSection.SPLIT_RIGHT else SidebarSection.SPLIT_LEFT, target.id)
                else SidebarDestination(SidebarSection.SPLIT_TAB, target.id, after = position.x >= rect.center.x)
            } else target.copy(after = target.id != null && if (target.section == SidebarSection.FAVORITES)
                position.x > rect.center.x else position.y > rect.center.y)
        }
    }
    fun cancel() {
        item = null
        destination = null
        if (!favoriteCommitted) favoriteOrder = null
    }
}

@Composable
fun Modifier.sidebarTarget(drag: SidebarDragState, key: String, destination: SidebarDestination, canSplit: Boolean = true, replacementRight: Boolean? = null): Modifier {
    DisposableEffect(drag, key) { onDispose { drag.remove(key) } }
    return onGloballyPositioned { drag.bounds(key, destination, it.boundsInRoot(), canSplit, replacementRight) }
}

/** Register rows; the stable sidebar owns the pointer so recycling a row cannot interrupt a drag. */
@Composable
fun Modifier.sidebarDraggable(drag: SidebarDragState, item: SidebarDragItem, immediate: Boolean = false): Modifier {
    val key = "${item.id}-$immediate-${item.pairRight?.id.orEmpty()}"
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
                    state.start(source.first, origin + down.position, source.third)
                    state.move(origin + press.position)
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
