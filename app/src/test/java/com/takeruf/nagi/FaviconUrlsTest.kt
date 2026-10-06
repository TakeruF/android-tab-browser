package com.takeruf.nagi

import com.takeruf.nagi.browser.favicon.*
import org.junit.Assert.*
import org.junit.Test

class FaviconUrlsTest {
    @Test fun originNormalizesHostsAndDefaultPortsWithoutPrivateUrlParts() {
        assertEquals("https://example.com/", faviconOrigin("HTTPS://user:secret@EXAMPLE.com:443/private?q=secret#fragment"))
        assertEquals("http://example.com:8080/", faviconOrigin("http://example.com:8080/a"))
        listOf(null, "about:blank", "file:///icon.png", "javascript:alert(1)", "invalid").forEach { assertNull(faviconOrigin(it)) }
    }
    @Test fun declaredIconsResolveBaseRelativeAndCdnLinksAndIgnoreNonIcons() {
        val html = """
            <base href="https://cdn.example.com/assets/">
            <link href='small.svg?x=1&amp;y=2' rel='SHORTCUT ICON'>
            <link rel=apple-touch-icon href=../touch.png>
            <link rel=stylesheet href=style.css>
            <link rel=icon href='file:///private/icon.png'>
            <link rel=icon href='small.svg?x=1&amp;y=2'>
        """
        assertEquals(listOf("https://cdn.example.com/assets/small.svg?x=1&y=2", "https://cdn.example.com/touch.png"),
            faviconLinks(html, "https://example.com/page"))
    }
    @Test fun fallbackCandidatesStayOnTheExactOrigin() {
        assertEquals(listOf("https://example.com:8443/favicon.ico", "https://example.com:8443/apple-touch-icon.png",
            "https://example.com:8443/apple-touch-icon-precomposed.png"), defaultFaviconUrls("https://example.com:8443/path?private"))
    }
}
