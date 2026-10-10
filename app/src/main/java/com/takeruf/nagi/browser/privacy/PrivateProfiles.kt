package com.takeruf.nagi.browser.privacy

import androidx.webkit.ProfileStore
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewCompat
import android.webkit.WebView
import android.webkit.CookieManager
import java.util.UUID

/** WebKit supplies isolated cookie/storage/cache jars; Nagi supplies their session lifetime. */
object PrivateProfiles {
    private const val PREFIX = "nagi-private-"
    fun supported() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)

    fun removeStaleProfiles() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        val store = ProfileStore.getInstance()
        store.allProfileNames.filter { it.startsWith(PREFIX) }.forEach { store.deleteProfile(it) }
    }

    fun create(): String {
        check(supported())
        return PREFIX + UUID.randomUUID()
    }

    fun attach(view: WebView, name: String): CookieManager {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            WebViewCompat.setProfile(view, name)
            return ProfileStore.getInstance().getOrCreateProfile(name).cookieManager
        }
        error("Private browsing requires isolated WebView profiles")
    }

    fun clear(name: String, done: () -> Unit = {}) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) return done()
        val profile = ProfileStore.getInstance().getProfile(name) ?: return done()
        profile.geolocationPermissions.clearAll()
        // Loaded profiles cannot be deleted in this process. Clear all website data now;
        // delete the remaining empty profile directory at the next cold startup.
        WebStorageCompat.deleteBrowsingData(profile.webStorage) { done() }
    }

    suspend fun clearAwait(name: String) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        clear(name) { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) }
    }
}
