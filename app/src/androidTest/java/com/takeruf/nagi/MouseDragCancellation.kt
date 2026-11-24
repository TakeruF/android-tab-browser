package com.takeruf.nagi

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

/** Call on the main thread after MouseInjectionScope.cancel() clears its button state. */
internal fun cancelMouseDragTouchStream(activity: Activity) {
    // Compose ui-test 1.10.1 injects mouse DOWN/MOVE/UP through dispatchTouchEvent,
    // but routes its mouse ACTION_CANCEL through dispatchGenericMotionEvent. Send
    // cancellation through the original owner/channel as the Android touch stream
    // expects; otherwise the test leaves a live drag behind despite cleared buttons.
    fun owner(view: View): View? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            owner(view.getChildAt(index))?.let { return it }
        }
        return null
    }
    val target = checkNotNull(owner(activity.window.decorView)) { "Expected the Activity's Compose input owner" }
    val now = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0).apply {
        source = InputDevice.SOURCE_MOUSE
    }
    try { target.dispatchTouchEvent(event) } finally { event.recycle() }
}
