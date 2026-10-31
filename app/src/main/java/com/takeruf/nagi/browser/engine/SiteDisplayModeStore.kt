package com.takeruf.nagi.browser.engine

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.net.IDN
import java.util.Locale

enum class SiteDisplayMode { MOBILE, DESKTOP, DEVICE_DEFAULT }

/** Explicit display choices are shared by every tab and survive Activity/process recreation. */
class SiteDisplayModeStore(
    context: Context,
    private val preferences: SharedPreferences = context.applicationContext
        .getSharedPreferences("site_display_modes", Context.MODE_PRIVATE),
    private val inMemory: Boolean = false,
) {
    private val choices = mutableMapOf<String, SiteDisplayMode>()
    fun mode(url: String): SiteDisplayMode? {
        val key = siteHost(url) ?: return null
        if (inMemory) return choices[key]
        if (preferences.getBoolean("device-default:$key", false)) return SiteDisplayMode.DEVICE_DEFAULT
        return if (preferences.contains(key)) {
            if (preferences.getBoolean(key, false)) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE
        } else null
    }

    fun desktopMode(url: String): Boolean? = mode(url)?.let { it == SiteDisplayMode.DESKTOP }

    fun remember(url: String, desktop: Boolean) {
        val key = siteHost(url) ?: return
        if (inMemory) { choices[key] = if (desktop) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE; return }
        // apply() updates memory synchronously, before a new tab or Activity can read it.
        preferences.edit().putBoolean(key, desktop).remove("device-default:$key").apply()
    }

    fun rememberDeviceDefault(url: String) {
        val key = siteHost(url) ?: return
        if (inMemory) { choices[key] = SiteDisplayMode.DEVICE_DEFAULT; return }
        // Keep the existing Boolean preference readable by older versions.
        preferences.edit().putBoolean(key, false).putBoolean("device-default:$key", true).apply()
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
