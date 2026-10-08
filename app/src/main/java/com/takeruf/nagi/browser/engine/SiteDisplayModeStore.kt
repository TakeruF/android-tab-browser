package com.takeruf.nagi.browser.engine

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.net.IDN
import java.util.Locale

/** Explicit display choices are shared by every tab and survive Activity/process recreation. */
class SiteDisplayModeStore(
    context: Context,
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("site_display_modes", Context.MODE_PRIVATE),
) {
    fun desktopMode(url: String): Boolean? {
        val key = siteHost(url) ?: return null
        return if (preferences.contains(key)) preferences.getBoolean(key, false) else null
    }

    fun remember(url: String, desktop: Boolean) {
        val key = siteHost(url) ?: return
        // apply() updates memory synchronously, before a new tab or Activity can read it.
        preferences.edit().putBoolean(key, desktop).apply()
    }

    companion object {
        fun siteHost(url: String): String? = runCatching {
            val uri = Uri.parse(url)
            if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
            val host = uri.host?.trimEnd('.')?.takeIf(String::isNotEmpty) ?: return null
            if (':' in host) host.lowercase(Locale.ROOT)
            else IDN.toASCII(host).lowercase(Locale.ROOT)
        }.getOrNull()
    }
}
