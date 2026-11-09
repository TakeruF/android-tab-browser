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
        controller.setTabContext(setOf("a", "b"), setOf("a"))
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
            assertTrue(commands.isEmpty())
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
    @Test fun returningToSourceRestoresInlinePlaybackWithoutPausing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        val commands = mutableListOf<String>()
        var exits = 0
        try {
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            controller.show(View(activity), "a", VideoPlayback(playing = true, hasVideo = true), popup = true,
                control = { commands += it }) { exits++ }
            // Starting a popup on its own page is allowed when another tab exists.
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            assertNotNull(controller.presentation.value)
            controller.setTabContext(setOf("a", "b"), setOf("b"))
            assertTrue(controller.presentation.value!!.popup)
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            assertNull(controller.presentation.value)
            assertEquals(1, exits)
            assertTrue(commands.isEmpty())
        } finally { controller.dispose() }
    }
    @Test fun oneTabDisablesPopoutButStillAllowsSystemPipFromFullscreen() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        var exits = 0
        try {
            controller.setTabContext(setOf("a"), setOf("a"))
            assertFalse(controller.popupAvailable)
            controller.show(View(activity), "a", VideoPlayback(playing = true, hasVideo = true)) { exits++ }
            controller.minimize()
            assertFalse(controller.presentation.value!!.popup)
            assertTrue(controller.canAutoEnter)
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            controller.minimize()
            assertTrue(controller.presentation.value!!.popup)
            controller.setTabContext(setOf("a"), setOf("a"))
            assertNull(controller.presentation.value)
            assertEquals(1, exits)
            controller.show(View(activity), "a", VideoPlayback(playing = true, hasVideo = true), popup = true) { exits++ }
            assertNull(controller.presentation.value)
            assertEquals(2, exits)
        } finally { controller.dispose() }
    }
    @Test fun userStartedInlineVideoPopsOutOnTabSwitchAndReturnsWithoutPause() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        var restored = 0
        val commands = mutableListOf<String>()
        try {
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            controller.updateInline(View(activity), "a", VideoPlayback(playing = true, hasVideo = true, autoPopoutAllowed = true),
                {}, { restored++ }, { commands += it })
            controller.setTabContext(setOf("a", "b"), setOf("b"))
            assertTrue(controller.presentation.value!!.popup)
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            assertNull(controller.presentation.value)
            assertEquals(1, restored)
            assertTrue(commands.isEmpty())
            // A replacement inline video without a playback gesture must not inherit permission.
            controller.updateInline(View(activity), "a", VideoPlayback(playing = true, hasVideo = true),
                { fail("Inline video was promoted") }, {}, {})
            controller.setTabContext(setOf("a", "b"), setOf("b"))
            assertNull(controller.presentation.value)
            assertFalse(controller.canAutoEnter)
        } finally { controller.dispose() }
    }
    @Test fun autoplayingInlineVideoDoesNotPopOutOnTabOrSpaceSwitch() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        var prepared = 0
        try {
            controller.setTabContext(setOf("a", "b"), setOf("a"))
            controller.updateInline(View(activity), "a", VideoPlayback(playing = true, hasVideo = true),
                { prepared++ }, {}, {})
            controller.setTabContext(setOf("a", "b"), setOf("b"))
            assertNull(controller.presentation.value)
            assertEquals(0, prepared)
            assertFalse(controller.canAutoEnter)
        } finally { controller.dispose() }
    }
    @Test fun playingInlineVideoNeverEnablesHomePipOrPreparesAVideoSurface() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        var prepared = 0
        var restored = 0
        val commands = mutableListOf<String>()
        try {
            controller.setTabContext(setOf("a"), setOf("a"))
            controller.updateInline(View(activity), "a", VideoPlayback(playing = true, hasVideo = true), { prepared++ }, { restored++ }, { commands += it })
            assertFalse(controller.canAutoEnter)
            controller.onUserLeave()
            assertNull(controller.presentation.value)
            controller.popupEnabled = false
            assertFalse(controller.canAutoEnter)
            controller.autoPipEnabled = false
            assertFalse(controller.canAutoEnter)
            controller.autoPipEnabled = true
            assertFalse(controller.canAutoEnter)
            // Even a late platform callback must not promote a page video.
            controller.onPipTransition()
            controller.onPipChanged(true)
            assertNull(controller.presentation.value)
            controller.onPipChanged(false)
            assertNull(controller.presentation.value)
            assertEquals(0, prepared)
            assertEquals(0, restored)
            assertTrue(commands.isEmpty())
            controller.onStopped()
            assertEquals(listOf("pause"), commands)
            controller.updateInline(View(activity), "a", VideoPlayback(playing = false, hasVideo = true), {}, {}, {})
            assertFalse(controller.canAutoEnter)
            controller.removeInline("a")
            assertFalse(controller.canAutoEnter)
        } finally { controller.dispose() }
    }
    @Test fun exitingFullscreenDisablesHomePipEvenWhileInlineVideoKeepsPlaying() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        org.robolectric.Shadows.shadowOf(activity.application).grantPermissions("${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = VideoPresentationController(activity)
        val playing = VideoPlayback(playing = true, hasVideo = true)
        try {
            controller.setTabContext(setOf("a"), setOf("a"))
            controller.updateInline(View(activity), "a", playing, {}, {}, {})
            assertFalse(controller.canAutoEnter)
            controller.show(View(activity), "a", playing) {}
            assertTrue(controller.canAutoEnter)
            controller.onPipChanged(true)
            controller.onPipChanged(false)
            assertTrue(controller.canAutoEnter)
            controller.close()
            assertFalse(controller.canAutoEnter)
            controller.updateInline(View(activity), "a", playing, {}, {}, {})
            controller.onUserLeave()
            assertFalse(controller.canAutoEnter)
            assertNull(controller.presentation.value)
        } finally { controller.dispose() }
    }
}
