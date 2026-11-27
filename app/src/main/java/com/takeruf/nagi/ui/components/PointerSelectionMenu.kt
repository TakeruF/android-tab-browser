package com.takeruf.nagi.ui.components

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange

/** Request the field's existing context menu after mouse selection, without editing its range. */
fun Modifier.menuAfterPointerSelection(selection: () -> TextRange, ready: () -> Boolean): Modifier = composed {
    val view = LocalView.current
    val currentSelection by rememberUpdatedState(selection)
    val currentReady by rememberUpdatedState(ready)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var interaction by remember { mutableLongStateOf(0L) }
    onGloballyPositioned { coordinates = it }.pointerInput(view) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val expected = ++interaction
            if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
            val previous = currentSelection()
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    val selected = currentSelection()
                    if (change.previousPressed && !selected.collapsed && currentReady() && (moved || selected != previous)) {
                        val layout = coordinates?.takeIf { it.isAttached } ?: break
                        val position = change.position.let {
                            androidx.compose.ui.geometry.Offset(it.x.coerceIn(1f, (size.width - 1f).coerceAtLeast(1f)),
                                it.y.coerceIn(1f, (size.height - 1f).coerceAtLeast(1f)))
                        }
                        val root = layout.localToRoot(position)
                        // Dispatch after the primary release has finished. The field's
                        // native provider still owns classification and all edit actions.
                        view.post {
                            if (layout.isAttached && interaction == expected && currentReady() && currentSelection() == selected) {
                                val time = SystemClock.uptimeMillis()
                                val pointer = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
                                val point = MotionEvent.PointerCoords().apply { x = root.x; y = root.y; pressure = 1f }
                                for ((action, buttons) in listOf(MotionEvent.ACTION_DOWN to MotionEvent.BUTTON_SECONDARY, MotionEvent.ACTION_UP to 0)) {
                                    val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1,
                                        arrayOf(pointer), arrayOf(point), 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                                    try { view.dispatchTouchEvent(motion) } finally { motion.recycle() }
                                }
                            }
                        }
                    }
                    break
                }
            }
        }
    }
}
