package com.takeruf.nagi

import android.app.Application
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.activity.ComponentActivity
import com.takeruf.nagi.browser.engine.ExternalAppLinks
import com.takeruf.nagi.browser.engine.NativeBrowserHost
import com.takeruf.nagi.data.datastore.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalAppLinksTest {
    @Test fun playLinksTargetPlayStoreAndMarketHasWebFallback() {
        val play = requireNotNull(ExternalAppLinks.parse("https://play.google.com/store/apps/details?id=com.example"))
        assertEquals("com.android.vending", play.intent.`package`)
        assertEquals(play.intent.dataString, play.webFallback)
        val market = requireNotNull(ExternalAppLinks.parse("market://details?id=com.example"))
        assertEquals("https://play.google.com/store/apps/details?id=com.example", market.webFallback)
        assertNull(ExternalAppLinks.parse("https://play.google.com.evil.test/")!!.intent.`package`)
    }

    @Test fun intentLinksDiscardExecutableFieldsAndExtras() {
        val link = requireNotNull(ExternalAppLinks.parse("intent://details?id=com.example#Intent;scheme=market;package=com.android.vending;action=android.intent.action.SEND;component=com.evil/.Private;launchFlags=0x3;S.secret=test;S.browser_fallback_url=https%3A%2F%2Fexample.com%2F;end"))
        assertEquals(Intent.ACTION_VIEW, link.intent.action)
        assertTrue(link.intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertNull(link.intent.component)
        assertNull(link.intent.selector)
        assertNull(link.intent.extras)
        assertEquals(0, link.intent.flags)
        assertEquals("com.android.vending", link.intent.`package`)
        assertEquals("https://example.com/", link.webFallback)
    }

    @Test fun localAndExecutableSchemesNeverReachOtherApps() {
        listOf("file:///sdcard/a", "content://private/a", "javascript:alert(1)", "data:text/html,x",
            "intent://private/a#Intent;scheme=content;end", "intent://broken").forEach {
            assertNull(it, ExternalAppLinks.parse(it))
        }
        assertNull(ExternalAppLinks.parse("intent://a#Intent;scheme=demo;S.browser_fallback_url=javascript%3Aalert(1);end")!!.webFallback)
    }

    @Test fun blockSettingPostsNonModalNoticeForAllSchemesAndUsesWebFallback() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            host.blockExternalApps = { true }
            var fallback: String? = null
            val play = "https://play.google.com/store/apps/details?id=com.example"
            assertTrue(host.openExternalLink(play) { fallback = it })
            assertEquals(play, host.blockedExternalApps.value!!.url)
            assertEquals(play, fallback)
            listOf("tel:123", "mailto:a@example.com", "sms:123", "geo:0,0", "demo://open").forEach {
                assertTrue(host.openExternalLink(it) { fallback = it })
                assertEquals(it, host.blockedExternalApps.value!!.url)
                host.dismissBlockedExternalApp(host.blockedExternalApps.value!!)
                assertNull(host.blockedExternalApps.value)
            }
            assertTrue(host.openExternalLink("market://details?id=com.example") { fallback = it })
            assertEquals("https://play.google.com/store/apps/details?id=com.example", fallback)
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun allowOnceOpensOnlyCurrentRequestWithoutChangingSetting() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            host.blockExternalApps = { true }
            host.openExternalLink("demo://old") {}
            val oldRequest = host.blockedExternalApps.value!!
            host.openExternalLink("demo://new") {}
            host.allowBlockedExternalApp(oldRequest)
            assertNull(shadowOf(activity).nextStartedActivity)
            val currentRequest = host.blockedExternalApps.value!!
            host.allowBlockedExternalApp(currentRequest)
            val launched = requireNotNull(shadowOf(activity).nextStartedActivity)
            assertEquals("demo://new", launched.dataString)
            assertTrue(launched.hasCategory(Intent.CATEGORY_BROWSABLE))
            assertNull(host.blockedExternalApps.value)
            assertTrue(host.blockExternalApps())
            host.allowBlockedExternalApp(currentRequest)
            assertNull(shadowOf(activity).nextStartedActivity)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun unblockedLinksLaunchDirectlyWithoutDialog() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            host.openExternalLink("demo://open") {}
            assertEquals("demo://open", shadowOf(activity).nextStartedActivity.dataString)
            assertNull(host.blockedExternalApps.value)
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun webLinksStayInNagiUnlessAnAppHandlesTheHost() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            val url = "https://example.com/open"
            val intent = requireNotNull(ExternalAppLinks.parse(url)).intent
            fun handler(pkg: String, authority: String?) = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.Main"; exported = true }
                filter = IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("https")
                    authority?.let { addDataAuthority(it, null) }
                }
            }
            val pm = shadowOf(activity.packageManager)
            pm.addResolveInfoForIntent(intent, handler("other.browser", null))
            assertFalse(host.openExternalLink(url) {})
            pm.addResolveInfoForIntent(intent, handler("example.app", "example.com"))
            assertTrue(host.openExternalLink(url) {})
            assertEquals("example.app", shadowOf(activity).nextStartedActivity.`package`)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun blockSettingDefaultsOffAndSurvivesOtherSettingsUpdates() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = java.io.File.createTempFile("external-apps", ".preferences_pb").apply { delete() }
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), store)
            assertFalse(settings.settings.first().blockExternalApps)
            settings.update { it.copy(blockExternalApps = true) }
            settings.update { it.copy(openLinksInNewTab = true) }
            assertTrue(SettingsStore(RuntimeEnvironment.getApplication(), store).settings.first().blockExternalApps)
            settings.update { it.copy(blockExternalApps = false) }
            assertFalse(settings.settings.first().blockExternalApps)
        } finally { scope.cancel(); file.delete() }
    }
}
