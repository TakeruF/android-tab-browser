package com.takeruf.nagi

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.takeruf.nagi.browser.search.BrowserLaunchRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35])
class BrowserLaunchRequestTest {
    @Test fun webSearchPreservesLiteralTextIncludingUrlsAndEngineKeywords() {
        for (query in listOf("公路交通新法", "https://example.com/a?x=1&y=2", "bd Kotlin + Compose")) {
            assertEquals(BrowserLaunchRequest.WebSearch(query), BrowserLaunchRequest.fromIntent(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "  $query  ")))
        }
    }
    @Test fun emptySearchAndUnsupportedUrisDoNotOpenTabs() {
        assertNull(BrowserLaunchRequest.fromIntent(Intent(Intent.ACTION_WEB_SEARCH)))
        assertNull(BrowserLaunchRequest.fromIntent(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "  ")))
        assertNull(BrowserLaunchRequest.fromIntent(Intent(Intent.ACTION_VIEW, Uri.parse("file:///tmp/test"))))
    }
    @Test fun ordinaryWebLinksStillNavigate() {
        val url = "https://example.com/path?q=hello"
        assertEquals(BrowserLaunchRequest.Navigate(url), BrowserLaunchRequest.fromIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
    }
}
