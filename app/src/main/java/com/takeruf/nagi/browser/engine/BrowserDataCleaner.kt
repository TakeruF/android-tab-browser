package com.takeruf.nagi.browser.engine

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object BrowserDataCleaner {
    suspend fun clear(context: Context) = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine<Unit> { continuation ->
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        WebStorage.getInstance().deleteAllData()
        GeolocationPermissions.getInstance().clearAll()
        WebView(context).apply { clearCache(true); clearHistory(); clearFormData(); destroy() }
    }
}
