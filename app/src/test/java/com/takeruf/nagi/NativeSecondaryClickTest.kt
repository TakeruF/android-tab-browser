package com.takeruf.nagi

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.takeruf.nagi.browser.engine.NativeSecondaryClick
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class NativeSecondaryClickTest {
    private fun event(action: Int, downTime: Long, buttons: Int,
        source: Int = InputDevice.SOURCE_MOUSE): MotionEvent {
        val pointer = MotionEvent.PointerProperties().apply {
            id = 0; toolType = if (source == InputDevice.SOURCE_TOUCHSCREEN) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_MOUSE
        }
        val coordinates = MotionEvent.PointerCoords().apply { x = 80f; y = 120f; pressure = 1f }
        return MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, arrayOf(pointer), arrayOf(coordinates),
            0, buttons, 1f, 1f, 0, 0, source, 0)
    }

    @Test fun genericOnlyClicksWithReusedDownTimeEachStartOneNativeLongPress() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val sink: (MotionEvent) -> Boolean = {
            assertTrue(it.isFromSource(InputDevice.SOURCE_TOUCHSCREEN))
            assertEquals(MotionEvent.TOOL_TYPE_FINGER, it.getToolType(0))
            assertEquals(0, it.buttonState)
            delivered.add(it.actionMasked)
            true
        }
        repeat(2) {
            for ((action, buttons) in listOf(MotionEvent.ACTION_BUTTON_PRESS to MotionEvent.BUTTON_SECONDARY,
                    MotionEvent.ACTION_BUTTON_RELEASE to 0)) {
                val input = event(action, 0, buttons)
                try { assertTrue(gesture.onMotion(input, sink)) } finally { input.recycle() }
            }
            assertTrue(gesture.longPressActive)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertFalse(gesture.longPressActive)
        }
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delivered)
    }

    @Test fun combinedTouchAndGenericMouseSequenceDoesNotDuplicateTheNativePressOrClick() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val downTime = SystemClock.uptimeMillis()
        for ((action, buttons) in listOf(MotionEvent.ACTION_DOWN to MotionEvent.BUTTON_SECONDARY,
                MotionEvent.ACTION_BUTTON_PRESS to MotionEvent.BUTTON_SECONDARY, MotionEvent.ACTION_UP to 0,
                MotionEvent.ACTION_BUTTON_RELEASE to 0)) {
            val input = event(action, downTime, buttons)
            try { assertTrue(gesture.onMotion(input) { delivered.add(it.actionMasked); true }) } finally { input.recycle() }
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delivered)
    }

    @Test fun newFingerGestureCancelsAPendingRightClickAndKeepsOrdinaryInputNative() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val sink: (MotionEvent) -> Boolean = { delivered.add(it.actionMasked); true }
        val secondary = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), MotionEvent.BUTTON_SECONDARY)
        try { assertTrue(gesture.onMotion(secondary, sink)) } finally { secondary.recycle() }
        val finger = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis() + 1, 0, InputDevice.SOURCE_TOUCHSCREEN)
        try { assertFalse(gesture.onMotion(finger, sink)) } finally { finger.recycle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), delivered)
        assertFalse(gesture.longPressActive)
    }

    @Test fun recognisedNativeSelectionReceivesReleaseSoItsFloatingToolbarCanBecomeVisible() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val input = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), MotionEvent.BUTTON_SECONDARY)
        try { assertTrue(gesture.onMotion(input) { delivered.add(it.actionMasked); true }) } finally { input.recycle() }
        gesture.selectionStarted()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
    }

    @Test fun reusedSelectionToolbarReceivesReleaseWithoutAnotherStartCallback() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        gesture.selectionStarted()
        val delivered = mutableListOf<Int>()
        val input = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), MotionEvent.BUTTON_SECONDARY)
        try { assertTrue(gesture.onMotion(input) { delivered.add(it.actionMasked); true }) } finally { input.recycle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
    }

    @Test fun delayedNativeClassificationCanStillReleaseAndShowTheSelectionToolbar() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val input = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), MotionEvent.BUTTON_SECONDARY)
        try { assertTrue(gesture.onMotion(input) { delivered.add(it.actionMasked); true }) } finally { input.recycle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(gesture.longPressActive)
        gesture.selectionStarted()
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals("No delayed cancellation may hide the toolbar", listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
    }

    @Test fun earlyNotificationForAnOldSelectionCannotTurnTheLongPressIntoATap() {
        val gesture = NativeSecondaryClick(RuntimeEnvironment.getApplication())
        val delivered = mutableListOf<Int>()
        val input = event(MotionEvent.ACTION_DOWN, SystemClock.uptimeMillis(), MotionEvent.BUTTON_SECONDARY)
        try { assertTrue(gesture.onMotion(input) { delivered.add(it.actionMasked); true }) } finally { input.recycle() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        gesture.selectionStarted()
        assertEquals(listOf(MotionEvent.ACTION_DOWN), delivered)
        val nativeLongPressDelay = android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(nativeLongPressDelay - 51))
        assertEquals("No UP may be sent before native long-press time", listOf(MotionEvent.ACTION_DOWN), delivered)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals("The release must occur only once", listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), delivered)
    }
}
