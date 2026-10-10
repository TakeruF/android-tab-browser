package com.takeruf.nagi

import android.app.Activity
import android.app.Application
import android.view.View
import com.takeruf.nagi.browser.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class VideoPresentationTest {
    @Test fun onlyPlayingVideoEntersPipAndItsOwnerCanCloseIt() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        val commands = mutableListOf<String>()
        var exits = 0
        try {
            controller.show(View(activity), "a", VideoPlayback(hasVideo = true), control = { commands += it }) { exits++ }
            assertFalse(controller.canAutoEnter)
            controller.update("a", VideoPlayback(playing = true, width = 1920, height = 1080, hasVideo = true))
            assertTrue(controller.canAutoEnter)
            controller.autoPipEnabled = false
            assertFalse(controller.canAutoEnter)
            controller.autoPipEnabled = true
            controller.minimize()
            assertTrue(controller.presentation.value!!.popup)
            controller.close("b", pause = true)
            assertNotNull(controller.presentation.value)
            controller.popupEnabled = false
            assertNull(controller.presentation.value)
            assertEquals(listOf("pause"), commands)
            assertEquals(1, exits)
            controller.close()
            assertEquals(1, exits)
        } finally { controller.dispose() }
    }
    @Test fun genericFullscreenNeverEntersPipAndReentrantExitDoesNotCloseReplacement() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        try {
            controller.show(View(activity), null) { controller.close() }
            assertFalse(controller.canAutoEnter)
            controller.minimize()
            assertFalse(controller.presentation.value!!.popup)
            controller.show(View(activity), "b", VideoPlayback(playing = true, hasVideo = true)) {}
            assertEquals("b", controller.presentation.value!!.tabId)
            controller.update("a", VideoPlayback())
            assertTrue(controller.canAutoEnter)
            assertEquals(2.39f, VideoPlayback(width = 10000, height = 1).aspectRatio, 0.00001f)
            assertEquals(1f / 2.39f, VideoPlayback(width = 1, height = 10000).aspectRatio, 0.00001f)
        } finally { controller.dispose() }
    }
}
