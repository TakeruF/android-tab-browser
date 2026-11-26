package com.takeruf.nagi

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.robolectric.RuntimeEnvironment
import com.takeruf.nagi.ui.browser.HistorySwipeGesture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class HistorySwipePreviewTest {
    private fun scroll(gesture: HistorySwipeGesture, distance: Float) {
        val now = SystemClock.uptimeMillis()
        val pointer = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val coordinates = MotionEvent.PointerCoords().apply { x = 100f; y = 100f; setAxisValue(MotionEvent.AXIS_HSCROLL, distance) }
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, arrayOf(pointer), arrayOf(coordinates),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHPAD, 0)
        try { gesture.onScroll(event) } finally { event.recycle() }
    }
    @Test fun partialWheelBurstShowsDestinationThenCancelsWithoutNavigation() {
        val context = RuntimeEnvironment.getApplication()
        val previews = mutableListOf<Pair<Boolean, Float>>()
        var cancellations = 0
        val navigations = mutableListOf<Boolean>()
        val gesture = HistorySwipeGesture(context, { true }, navigations::add, { _, _, _, result -> result(false) },
            { back, progress -> previews.add(back to progress) }, { cancellations++ })
        scroll(gesture, 0.5f)
        assertTrue(previews.any { it.first && it.second > 0f && it.second < 1f })
        assertTrue(navigations.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(251))
        assertEquals(1, cancellations)
        assertTrue(navigations.isEmpty())
    }
    @Test fun forwardWheelBurstShowsForwardPreviewAndCommitsOnlyOnceIncludingMomentum() {
        val previews = mutableListOf<Boolean>()
        val navigations = mutableListOf<Boolean>()
        val gesture = HistorySwipeGesture(RuntimeEnvironment.getApplication(), { true }, navigations::add,
            { _, _, _, result -> result(false) }, { back, _ -> previews.add(back) })
        scroll(gesture, -0.5f)
        scroll(gesture, -4f)
        repeat(4) { scroll(gesture, -4f) }
        assertTrue(previews.isNotEmpty())
        assertTrue(previews.none { it })
        assertEquals(listOf(false), navigations)
    }
    @Test fun nestedScrollerGetsTheWheelWithoutAnyHistoryPreview() {
        var previewed = false
        var navigated = false
        val gesture = HistorySwipeGesture(RuntimeEnvironment.getApplication(), { true }, { navigated = true },
            { _, _, _, result -> result(true) }, { _, _ -> previewed = true })
        scroll(gesture, 4f)
        assertFalse(previewed)
        assertFalse(navigated)
    }
}
