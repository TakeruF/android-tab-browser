package com.takeruf.nagi.browser.favicon

import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale

internal fun faviconOrigin(url: String?): String? = runCatching {
    val uri = URI(url ?: return null)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if (scheme !in setOf("https", "http") || uri.host == null) return null
    val port = if ((scheme == "https" && uri.port == 443) || (scheme == "http" && uri.port == 80)) -1 else uri.port
    URI(scheme, null, uri.host.lowercase(Locale.ROOT), port, "/", null, null).toString()
}.getOrNull()

internal fun faviconLinks(html: String, pageUrl: String): List<String> =
    Jsoup.parse(html, pageUrl).select("link[href]").filter { link ->
        link.attr("rel").lowercase(Locale.ROOT).split(Regex("\\s+")).any {
            it == "icon" || it == "apple-touch-icon" || it == "apple-touch-icon-precomposed"
        }
    }.map { it.absUrl("href") }.filter { faviconOrigin(it) != null }.distinct().take(8)

internal fun defaultFaviconUrls(siteUrl: String?): List<String> = faviconOrigin(siteUrl)?.let { origin ->
    listOf("${origin}favicon.ico", "${origin}apple-touch-icon.png", "${origin}apple-touch-icon-precomposed.png")
}.orEmpty()
