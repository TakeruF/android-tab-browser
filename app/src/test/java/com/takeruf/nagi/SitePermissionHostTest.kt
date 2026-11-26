package com.takeruf.nagi

import android.Manifest
import androidx.activity.ComponentActivity
import com.takeruf.nagi.browser.engine.NativeBrowserHost
import com.takeruf.nagi.browser.engine.SitePermission
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SitePermissionHostTest {
    @Test fun siteConsentStillRequiredWhenAndroidAlreadyGrantedMicrophone() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        shadowOf(activity.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            var granted: Set<SitePermission>? = null
            host.requestPermission("https://example.com", setOf(SitePermission.MICROPHONE)) { granted = it }
            val prompt = host.sitePermissionPrompts.value!!
            assertEquals("https://example.com", prompt.origin)
            assertNull(granted)
            host.allowSitePermission(prompt)
            assertEquals(setOf(SitePermission.MICROPHONE), granted)
            assertNull(host.sitePermissionPrompts.value)
            host.allowSitePermission(prompt)
            assertNull(host.sitePermissionPrompts.value)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun cancelledRequestDisappearsAndDoesNotBlockANewSite() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val host = NativeBrowserHost(controller.get())
        controller.start().resume()
        try {
            var first: Set<SitePermission>? = null
            val callback: (Set<SitePermission>) -> Unit = { first = it }
            host.requestPermission("https://first.example", setOf(SitePermission.MICROPHONE), callback)
            val stale = host.sitePermissionPrompts.value!!
            host.cancelPermission({})
            assertSame(stale, host.sitePermissionPrompts.value)
            host.cancelPermission(callback)
            assertEquals(emptySet<SitePermission>(), first)
            assertNull(host.sitePermissionPrompts.value)
            var second: Set<SitePermission>? = null
            host.requestPermission("https://second.example", setOf(SitePermission.CAMERA, SitePermission.LOCATION)) { second = it }
            val current = host.sitePermissionPrompts.value!!
            host.allowSitePermission(stale)
            assertSame(current, host.sitePermissionPrompts.value)
            host.dismissSitePermission(current)
            assertEquals(emptySet<SitePermission>(), second)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun aSecondRequestCannotReplaceTheVisiblePromptAndDisposeDeniesTheFirst() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val host = NativeBrowserHost(controller.get())
        controller.start().resume()
        var first: Set<SitePermission>? = null
        host.requestPermission("https://first.example", setOf(SitePermission.MICROPHONE)) { first = it }
        val original = host.sitePermissionPrompts.value
        var second: Set<SitePermission>? = null
        host.requestPermission("https://second.example", setOf(SitePermission.CAMERA)) { second = it }
        assertEquals(emptySet<SitePermission>(), second)
        assertSame(original, host.sitePermissionPrompts.value)
        host.dispose()
        assertEquals(emptySet<SitePermission>(), first)
        assertNull(host.sitePermissionPrompts.value)
        controller.pause().stop().destroy()
    }
}
