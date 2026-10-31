package com.takeruf.nagi

import android.app.Application
import android.content.Context
import com.takeruf.nagi.browser.engine.SiteDisplayModeStore
import com.takeruf.nagi.browser.engine.SiteDisplayMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SiteDisplayModeStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        context.getSharedPreferences("site_display_modes", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun explicitMobileChoiceIsAvailableToNewStoresAndAllPathsOnTheHost() {
        val first = SiteDisplayModeStore(context)
        assertNull(first.desktopMode("https://example.com/one"))
        first.remember("https://EXAMPLE.com./one", false)
        val recreated = SiteDisplayModeStore(context)
        assertEquals(false, recreated.desktopMode("http://example.com:8080/two?q=1#section"))
        assertNull(recreated.desktopMode("https://other.example.com/"))
        assertNull(recreated.desktopMode("https://unrelated.test/"))
    }

    @Test fun desktopChoiceReplacesMobileChoiceAndOverridesEitherDefault() {
        val store = SiteDisplayModeStore(context)
        store.remember("https://example.com/", false)
        store.remember("https://example.com/new", true)
        assertEquals(true, SiteDisplayModeStore(context).desktopMode("https://example.com/"))
    }

    @Test fun unicodeAndAsciiDomainsShareAChoice() {
        val store = SiteDisplayModeStore(context)
        store.remember("https://例え.テスト/", false)
        assertEquals(false, store.desktopMode("https://xn--r8jz45g.xn--zckzah/path"))
    }

    @Test fun nonWebAddressesCannotSaveAChoice() {
        val store = SiteDisplayModeStore(context)
        listOf("about:blank", "file://example.com/test", "javascript:alert(1)", "https://", "not a URL").forEach {
            store.remember(it, false)
            assertNull(store.desktopMode(it))
            assertNull(SiteDisplayModeStore.siteHost(it))
        }
    }

    @Test fun deviceDefaultChoicePersistsWithoutChangingLegacyPreferenceTypes() {
        val preferences = context.getSharedPreferences("site_display_modes", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("legacy.test", true).commit()
        val store = SiteDisplayModeStore(context)
        assertEquals(SiteDisplayMode.DESKTOP, store.mode("https://legacy.test/"))
        store.rememberDeviceDefault("https://EXAMPLE.com./login")
        assertEquals(SiteDisplayMode.DEVICE_DEFAULT, SiteDisplayModeStore(context).mode("http://example.com:8080/next"))
        assertEquals(false, preferences.getBoolean("example.com", true))
        assertNull(store.mode("https://other.example.com/"))
        assertEquals(true, store.desktopMode("https://legacy.test/"))
    }

    @Test fun bothExplicitChoicesReplaceTheDeviceDefaultChoice() {
        val store = SiteDisplayModeStore(context)
        for (desktop in listOf(false, true)) {
            store.rememberDeviceDefault("https://example.com/")
            store.remember("https://example.com/next", desktop)
            assertEquals(if (desktop) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE,
                SiteDisplayModeStore(context).mode("https://example.com/"))
        }
    }

    @Test fun deviceDefaultMemoryChoiceIsIsolatedAndRejectsNonWebAddresses() {
        val store = SiteDisplayModeStore(context, inMemory = true)
        store.rememberDeviceDefault("https://example.com/")
        assertEquals(SiteDisplayMode.DEVICE_DEFAULT, store.mode("https://example.com/next"))
        assertNull(SiteDisplayModeStore(context).mode("https://example.com/"))
        for (url in listOf("about:blank", "file://example.com/test", "javascript:alert(1)")) {
            store.rememberDeviceDefault(url)
            assertNull(store.mode(url))
        }
    }
}
