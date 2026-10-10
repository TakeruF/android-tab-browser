package com.takeruf.nagi.ui.browser

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/** Preserve provenance while Compose requires an exact SOURCE_MOUSE for wheel routing. */
internal object TouchpadScrollDispatch {
    var active = false
        private set
    fun dispatch(block: () -> Boolean): Boolean {
        val previous = active
        active = true
        try { return block() } finally { active = previous }
    }
}

/** Trackpad-only history input; touchscreen gestures stay with the native page. */
internal class HistorySwipeGesture(
    context: Context,
    private val canNavigate: (back: Boolean) -> Boolean,
    private val navigate: (back: Boolean) -> Unit,
    private val canScroll: (Float, Float, Int, (Boolean) -> Unit) -> Unit,
) {
    private val density = context.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val threshold = 96f * density
    private val scrollFactor = ViewConfiguration.get(context).scaledHorizontalScrollFactor
    private var dx = 0f
    private var dy = 0f
    private var tracking = false
    private var rejected = false
    private var claimed = false
    private class PadScrollCheck(var scrollable: Boolean? = null, var completedBack: Boolean? = null)
    private var padCheck: PadScrollCheck? = null
    private var touchGeneration = 0L
    private var wheelLastTime = Long.MIN_VALUE
    private var wheelX = 0f
    private var wheelY = 0f
    private var wheelBlocked = false
    private var wheelClaimed = false
    private var wheelCanScroll: Boolean? = null
    private var wheelBack: Boolean? = null
    private var wheelGeneration = 0L

    fun reset() {
        resetTouch()
        resetWheel()
    }

    private fun resetTouch(cancelPending: Boolean = true) {
        if (cancelPending) touchGeneration++
        tracking = false; rejected = false; claimed = false
        dx = 0f; dy = 0f; padCheck = null
    }

    private fun resetWheel() {
        wheelGeneration++
        wheelLastTime = Long.MIN_VALUE; wheelX = 0f; wheelY = 0f
        wheelBlocked = false; wheelClaimed = false; wheelCanScroll = null; wheelBack = null
    }

    fun onTouch(event: MotionEvent, cancelChild: () -> Unit): Boolean {
        // Finger tool type alone does not imply a trackpad: real touchscreens use it too.
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) { reset(); return false }
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) { reset(); return false }
        if (action == MotionEvent.ACTION_CANCEL) {
            val consumed = claimed; resetTouch(); return consumed
        }
        val classifiedSwipe = Build.VERSION.SDK_INT >= 34 &&
            event.classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE
        if (!tracking && !rejected && classifiedSwipe) tracking = true
        if (!tracking) return false
        if (!rejected && Build.VERSION.SDK_INT >= 34 && (action == MotionEvent.ACTION_MOVE ||
                action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP)) {
            val wasClaimed = claimed
            if (!classifiedSwipe && action == MotionEvent.ACTION_MOVE) rejected = true
            else if (classifiedSwipe) {
                // Include relative pixel scroll distances from every batched sample.
                for (sample in 0..event.historySize) {
                    val x = if (sample == event.historySize) event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE)
                        else event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE, sample)
                    val y = if (sample == event.historySize) event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE)
                        else event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE, sample)
                    dx -= x; dy -= y
                    decide()
                }
            }
            if (!rejected && abs(dx) > slop && padCheck == null) {
                val check = PadScrollCheck().also { padCheck = it }
                val generation = touchGeneration
                canScroll(event.x, event.y, if (dx > 0) -1 else 1) { scrollable ->
                    if (generation == touchGeneration) {
                        check.scrollable = scrollable
                        check.completedBack?.let { back -> if (!scrollable && canNavigate(back)) navigate(back) }
                    }
                }
            }
            decide()
            if (!wasClaimed && claimed) cancelChild()
        }
        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            val consumed = claimed
            if (!rejected && abs(dx) >= threshold && abs(dx) > abs(dy) * 2) {
                val back = dx > 0
                val check = padCheck
                if (check?.scrollable == false && canNavigate(back)) navigate(back)
                else if (check != null && check.scrollable == null) check.completedBack = back
            }
            tracking = false
            rejected = true
            if (action == MotionEvent.ACTION_UP) resetTouch(cancelPending = false)
            return consumed
        }
        return claimed
    }

    private fun decide() {
        if (rejected) return
        if (abs(dy) > slop && abs(dx) < abs(dy) * 2) { rejected = true; return }
        if (padCheck?.scrollable == false && abs(dx) > maxOf(24f * density, slop) &&
            abs(dx) > abs(dy) * 2 && canNavigate(dx > 0)) claimed = true
    }

    fun onScroll(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) || !isTouchpad(event)) { resetWheel(); return false }
        if (wheelLastTime == Long.MIN_VALUE || event.eventTime - wheelLastTime > 250) resetWheel()
        wheelLastTime = event.eventTime
        if (wheelClaimed) return true // Includes momentum; one history step per burst.
        if (wheelBlocked) return false
        for (sample in 0..event.historySize) {
            wheelX += (if (sample == event.historySize) event.getAxisValue(MotionEvent.AXIS_HSCROLL)
                else event.getHistoricalAxisValue(MotionEvent.AXIS_HSCROLL, sample)) * scrollFactor
            wheelY += (if (sample == event.historySize) event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                else event.getHistoricalAxisValue(MotionEvent.AXIS_VSCROLL, sample)) * scrollFactor
        }
        if (abs(wheelY) > slop && abs(wheelX) < abs(wheelY) * 2) { wheelBlocked = true; return false }
        if (abs(wheelX) <= slop || abs(wheelX) <= abs(wheelY) * 2) return false
        if (wheelBack != null && wheelBack != (wheelX > 0)) { wheelBlocked = true; return false }
        if (!canNavigate(wheelX > 0)) { wheelBlocked = true; return false }
        if (wheelCanScroll == null) {
            // Mark pending before invoking the adapter (which may answer synchronously).
            wheelCanScroll = true
            wheelBack = wheelX > 0
            val generation = wheelGeneration
            canScroll(event.x, event.y, if (wheelX > 0) -1 else 1) { scrollable ->
                if (generation == wheelGeneration && SystemClock.uptimeMillis() - wheelLastTime <= 250) {
                    wheelCanScroll = scrollable
                    if (scrollable) wheelBlocked = true else finishWheel()
                }
            }
        }
        finishWheel()
        return wheelClaimed
    }

    private fun finishWheel() {
        if (wheelCanScroll == false && !wheelClaimed && !wheelBlocked &&
            abs(wheelX) >= threshold && abs(wheelX) > abs(wheelY) * 2 && canNavigate(wheelX > 0)) {
            wheelClaimed = true
            navigate(wheelX > 0)
        }
    }

    private fun isTouchpad(event: MotionEvent): Boolean {
        val deviceBit = InputDevice.SOURCE_TOUCHPAD and InputDevice.SOURCE_CLASS_MASK.inv()
        return TouchpadScrollDispatch.active || event.source and deviceBit != 0 || event.device?.supportsSource(InputDevice.SOURCE_TOUCHPAD) == true ||
            (event.isFromSource(InputDevice.SOURCE_MOUSE) && event.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER) ||
            (Build.VERSION.SDK_INT >= 34 && event.classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE)
    }
}
