package com.takeruf.nagi.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Give a secondary click the same selection UI as a finger long press.
 * WebView's tablet mouse menu is provider-owned and has no public style switch.
 * Keep selection/editing/actions in WebView rather than copying them into a JS menu.
 */
internal class NativeSecondaryClick(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var sourceDownTime: Long? = null
    private var buttonsReleased = true
    private var touchDownTime = 0L
    private var x = 0f
    private var y = 0f
    private var dispatch: ((MotionEvent) -> Boolean)? = null
    var longPressActive = false
        private set
    var selectionOnly = false
        private set
    private var selectionModeStarted = false
    private var floatingSelectionActive = false
    private var dispatching = false
    private val finish = Runnable { finishLongPress() }

    fun onMotion(event: MotionEvent, sendTouch: (MotionEvent) -> Boolean): Boolean {
        if (dispatching) return false
        if (!event.isFromSource(InputDevice.SOURCE_MOUSE) && !event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) reset()
            return false
        }
        val action = event.actionMasked
        val secondary = event.buttonState and MotionEvent.BUTTON_SECONDARY != 0 ||
            (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE) &&
            event.actionButton == MotionEvent.BUTTON_SECONDARY
        val initial = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS
        if (action == MotionEvent.ACTION_SCROLL || initial && !secondary) {
            reset()
            return false
        }
        if (initial && secondary && (buttonsReleased || sourceDownTime != event.downTime)) {
            cancelLongPress()
            sourceDownTime = event.downTime
            buttonsReleased = false
            showSelectionMenu(event.x, event.y, sendTouch)
            return true
        }
        if (sourceDownTime != event.downTime) return false
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE) buttonsReleased = true
        if (action == MotionEvent.ACTION_MOVE && (abs(event.x - x) > slop || abs(event.y - y) > slop) ||
            action == MotionEvent.ACTION_CANCEL) cancelLongPress()
        return secondary || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            action == MotionEvent.ACTION_BUTTON_RELEASE || action == MotionEvent.ACTION_MOVE
    }

    fun showSelectionMenu(x: Float, y: Float, sendTouch: (MotionEvent) -> Boolean, selectionOnly: Boolean = false) {
        cancelLongPress()
        this.selectionOnly = selectionOnly
        this.x = x; this.y = y
        touchDownTime = SystemClock.uptimeMillis()
        dispatch = sendTouch
        longPressActive = true
        selectionModeStarted = floatingSelectionActive
        send(MotionEvent.ACTION_DOWN)
        // Only release a recognised native selection; other targets must never click.
        handler.postDelayed(finish, ViewConfiguration.getLongPressTimeout().toLong() +
            if (selectionModeStarted) 100 else 1_500)
    }

    private fun send(action: Int) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { x = this@NativeSecondaryClick.x; y = this@NativeSecondaryClick.y; pressure = 1f }
        val event = MotionEvent.obtain(touchDownTime, SystemClock.uptimeMillis(), action, 1,
            arrayOf(properties), arrayOf(coordinates), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        dispatching = true
        try { dispatch?.invoke(event) } finally { dispatching = false; event.recycle() }
    }

    private fun cancelLongPress() {
        handler.removeCallbacks(finish)
        if (longPressActive) send(MotionEvent.ACTION_CANCEL)
        longPressActive = false
        selectionOnly = false
        dispatch = null
    }

    /** Called only after WebView creates its native floating selection ActionMode. */
    fun selectionStarted() {
        floatingSelectionActive = true
        if (longPressActive) {
            selectionModeStarted = true
            finishLongPress()
        }
    }

    fun selectionFinished() { floatingSelectionActive = false; selectionModeStarted = false }

    private fun finishLongPress() {
        handler.removeCallbacks(finish)
        // A delayed notification for the previous selection can arrive immediately
        // after DOWN. It must not turn this intended long press into an ordinary tap.
        val remaining = touchDownTime + ViewConfiguration.getLongPressTimeout() + 100 - SystemClock.uptimeMillis()
        if (longPressActive && selectionModeStarted && remaining > 0) {
            handler.postDelayed(finish, remaining)
            return
        }
        if (longPressActive) send(if (selectionModeStarted) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL)
        longPressActive = false
        selectionOnly = false
        dispatch = null
    }

    fun reset() { cancelLongPress(); sourceDownTime = null; buttonsReleased = true }
}
