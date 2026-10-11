package com.takeruf.nagi.browser.engine

internal object SiteUserAgent {
    /** A tablet's default Android UA is not a request for a phone layout. */
    fun mobile(systemUserAgent: String): String = when {
        Regex("\\bMobile\\b").containsMatchIn(systemUserAgent) -> systemUserAgent
        " Safari/" in systemUserAgent -> systemUserAgent.replace(" Safari/", " Mobile Safari/")
        else -> "$systemUserAgent Mobile"
    }
}
