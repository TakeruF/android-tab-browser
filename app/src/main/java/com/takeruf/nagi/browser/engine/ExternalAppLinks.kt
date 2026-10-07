package com.takeruf.nagi.browser.engine

import android.content.Intent
import android.net.Uri
import java.util.Locale

internal data class ExternalAppLink(val intent: Intent, val webFallback: String?, val isWebLink: Boolean)

internal object ExternalAppLinks {
    private val blockedSchemes = setOf("about", "javascript", "data", "file", "content", "blob", "intent", "android-app")

    fun parse(url: String): ExternalAppLink? = runCatching {
        val original = Uri.parse(url)
        val parsed = if (original.scheme.equals("intent", true)) Intent.parseUri(url, Intent.URI_INTENT_SCHEME) else null
        val data = parsed?.data ?: original
        val scheme = data.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme in blockedSchemes || !scheme.matches(Regex("[a-z][a-z0-9+.-]*"))) return null
        val web = scheme == "https" || scheme == "http"
        if (web && data.host.isNullOrBlank()) return null
        // Rebuild rather than forwarding website-provided actions, components, selectors,
        // extras or permission flags. Only browsable VIEW links may leave the browser.
        val intent = Intent(Intent.ACTION_VIEW, data).addCategory(Intent.CATEGORY_BROWSABLE)
        intent.`package` = parsed?.`package`
        var fallback = parsed?.getStringExtra("browser_fallback_url")?.takeIf(::isWebUrl)
        if (web && data.host.equals("play.google.com", true)) {
            intent.setPackage("com.android.vending")
            fallback = data.toString()
        } else if (scheme == "market" && data.host == "details" && fallback == null) {
            data.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let {
                fallback = Uri.Builder().scheme("https").authority("play.google.com")
                    .path("/store/apps/details").appendQueryParameter("id", it).build().toString()
            }
        }
        ExternalAppLink(intent, fallback, web)
    }.getOrNull()

    private fun isWebUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
    }
}
