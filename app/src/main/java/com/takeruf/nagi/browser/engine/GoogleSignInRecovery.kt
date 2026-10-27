package com.takeruf.nagi.browser.engine

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Recognize Google's rejection endpoint without inspecting login fields or localized page text. */
object GoogleSignInRecovery {
    private val rejectionPath = Regex("/(?:v[23]/signin/rejected|signin/(?:v2/)?rejected)/?")
    private const val ENTRY = "https://accounts.google.com/ServiceLogin"

    fun isRejectedPage(url: String): Boolean = rejectionUri(url) != null

    /** Restart sign-in rather than reloading a rejection result with a different user agent. */
    fun retryUrl(url: String): String? {
        val uri = rejectionUri(url) ?: return null
        val parameters = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            runCatching {
                val pair = part.split('=', limit = 2)
                decode(pair[0]) to decode(pair.getOrElse(1) { "" })
            }.getOrNull()
        }
        fun value(name: String) = parameters.firstOrNull { it.first == name }?.second
        val retained = buildList {
            value("continue")?.takeIf(::isWebDestination)?.let { add("continue" to it) }
            value("service")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }?.let { add("service" to it) }
            value("hl")?.takeIf { it.matches(Regex("[A-Za-z0-9-]{1,35}")) }?.let { add("hl" to it) }
        }
        // Do not carry identifiers or the rejected attempt's transient state into the retry.
        return ENTRY + if (retained.isEmpty()) "" else retained.joinToString("&", prefix = "?") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
    }

    private fun rejectionUri(url: String): URI? = runCatching {
        URI(url).takeIf {
            it.scheme.equals("https", ignoreCase = true) && it.host.equals("accounts.google.com", ignoreCase = true) &&
                it.userInfo == null && it.port in listOf(-1, 443) && rejectionPath.matches(it.rawPath.orEmpty())
        }
    }.getOrNull()

    private fun isWebDestination(url: String) = runCatching {
        val uri = URI(url)
        uri.scheme.lowercase() in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)

    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
}
