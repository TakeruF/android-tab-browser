package com.takeruf.nagi.browser.search

import android.app.SearchManager
import android.content.Intent

sealed interface BrowserLaunchRequest {
    data class Navigate(val url: String) : BrowserLaunchRequest
    data class WebSearch(val query: String) : BrowserLaunchRequest

    companion object {
        fun fromIntent(intent: Intent): BrowserLaunchRequest? {
            if (intent.action == Intent.ACTION_WEB_SEARCH) {
                return intent.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let(::WebSearch)
            }
            return intent.dataString?.takeIf {
                it.startsWith("https://", true) || it.startsWith("http://", true)
            }?.let(::Navigate)
        }
    }
}
