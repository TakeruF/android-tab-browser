package com.takeruf.nagi.browser.engine

import androidx.webkit.WebViewFeature

/** Provider capability; unavailable providers retain the site's original appearance. */
object PageDarkening {
    val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
}
