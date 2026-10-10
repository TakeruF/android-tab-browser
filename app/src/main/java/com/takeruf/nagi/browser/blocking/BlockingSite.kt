package com.takeruf.nagi.browser.blocking

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** OkHttp's bundled Public Suffix List separates registrable sites, including private suffixes. */
fun blockingSite(url: String): String? = runCatching {
    val parsed = url.toHttpUrlOrNull() ?: return@runCatching null
    val canonical = parsed.newBuilder().host(parsed.host.trimEnd('.')).build()
    canonical.topPrivateDomain() ?: canonical.host
}.getOrNull()
