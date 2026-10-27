package com.takeruf.nagi

import com.takeruf.nagi.browser.engine.GoogleSignInRecovery
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class GoogleSignInRecoveryTest {
    private val rejected = "https://accounts.google.com/v3/signin/rejected"
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun parameters(url: String) = URI(url).rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
        val pair = it.split('=', limit = 2)
        pair[0] to URLDecoder.decode(pair[1], "UTF-8")
    }

    @Test fun recognizesTheGoogleRejectionEndpointsAcrossPageLanguages() {
        for (path in listOf("/v3/signin/rejected", "/v2/signin/rejected", "/signin/v2/rejected", "/signin/rejected/")) {
            assertTrue(path, GoogleSignInRecovery.isRejectedPage("https://accounts.google.com$path?hl=zh-CN"))
        }
        assertTrue(GoogleSignInRecovery.isRejectedPage("https://ACCOUNTS.GOOGLE.COM:443/v3/signin/rejected"))
    }

    @Test fun rejectsOtherOriginsAndOrdinaryLoginOrErrorPages() {
        for (url in listOf("http://accounts.google.com/v3/signin/rejected", "https://accounts.google.com.evil.example/v3/signin/rejected",
            "https://example.com/v3/signin/rejected", "https://accounts.google.com@evil.example/v3/signin/rejected",
            "https://user@accounts.google.com/v3/signin/rejected", "https://accounts.google.com:8443/v3/signin/rejected",
            "https://accounts.google.com/v3/signin/identifier", "https://accounts.google.com/v3/signin/challenge/pwd",
            "https://accounts.google.com/some/rejected", "https://example.com/?continue=$rejected")) {
            assertFalse(url, GoogleSignInRecovery.isRejectedPage(url))
            assertNull(url, GoogleSignInRecovery.retryUrl(url))
        }
    }

    @Test fun restartsGoogleSignInAndKeepsTheDestinationAndLocale() {
        val destination = "https://docs.google.com/document/d/fixture/edit?mode=view&state=a+b=c"
        val retry = GoogleSignInRecovery.retryUrl("$rejected?continue=${encode(destination)}&service=wise&hl=ja")!!
        assertEquals("/ServiceLogin", URI(retry).path)
        assertEquals("accounts.google.com", URI(retry).host)
        assertEquals(mapOf("continue" to destination, "service" to "wise", "hl" to "ja"), parameters(retry))
    }

    @Test fun retainsNestedOAuthStateWithoutCarryingTheFailedAttemptOrIdentifier() {
        val destination = "https://accounts.google.com/o/oauth2/v2/auth?client_id=fixture&state=a%2Bb&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"
        val retry = GoogleSignInRecovery.retryUrl("$rejected?continue=${encode(destination)}&Email=fixture%40example.invalid&login_hint=fixture&dsh=obsolete&ifkv=obsolete")!!
        assertEquals(mapOf("continue" to destination), parameters(retry))
    }

    @Test fun neverTurnsANonWebContinueIntoANavigation() {
        for (destination in listOf("javascript:alert(1)", "intent://example/", "//example.com/", "file:///fixture", "https://user@example.com/")) {
            assertEquals("https://accounts.google.com/ServiceLogin",
                GoogleSignInRecovery.retryUrl("$rejected?continue=${encode(destination)}"))
        }
    }

    @Test fun canRetryWithoutDestinationAndIgnoresMalformedInput() {
        assertEquals("https://accounts.google.com/ServiceLogin", GoogleSignInRecovery.retryUrl(rejected))
        for (url in listOf("", "%", "not a url", "$rejected?continue=%ZZ")) assertNull(GoogleSignInRecovery.retryUrl(url))
    }
}
