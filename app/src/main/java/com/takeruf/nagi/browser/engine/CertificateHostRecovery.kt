package com.takeruf.nagi.browser.engine

import java.net.URI

/**
 * Some sites serve a certificate that only names `www.<host>` on their bare host. Like Chrome,
 * move to the `www` origin when the certificate proves that origin, instead of bypassing validation.
 */
object CertificateHostRecovery {
    private val ipv4 = Regex("\\d{1,3}(?:\\.\\d{1,3}){3}")

    /** WebView canonicalizes an empty root path to '/'; fragments do not identify another TLS request. */
    fun matchesMainDocument(errorUrl: String, pageUrl: String): Boolean {
        val page = documentAddress(pageUrl) ?: return false
        return documentAddress(errorUrl) == page
    }

    private data class DocumentAddress(val scheme: String, val host: String, val port: Int,
        val path: String, val query: String?)

    private fun documentAddress(url: String): DocumentAddress? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme.lowercase()
        require(scheme == "https" || scheme == "http")
        require(uri.rawUserInfo == null)
        DocumentAddress(scheme, requireNotNull(uri.host).lowercase(),
            uri.port.takeUnless { it == -1 } ?: if (scheme == "https") 443 else 80,
            uri.rawPath.orEmpty().ifEmpty { "/" }, uri.rawQuery)
    }.getOrNull()

    /** [certificateNames] are the certificate's DNS subject alternative names (or its CN when none are readable). */
    fun wwwRetryUrl(url: String, certificateNames: Collection<String>): String? = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null) return null
        if (host.startsWith("www.") || '.' !in host || host.startsWith('[') || ipv4.matches(host)) return null
        val target = "www.$host"
        val covered = certificateNames.any { name ->
            val dns = name.trim().trimEnd('.').lowercase()
            dns == target || dns == "*.$host"
        }
        if (!covered) return null
        // Rebuild from raw components so existing percent escapes survive unchanged.
        "https://$target" + (if (uri.port != -1) ":${uri.port}" else "") + uri.rawPath.orEmpty().ifEmpty { "/" } +
            (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
    }.getOrNull()
}
