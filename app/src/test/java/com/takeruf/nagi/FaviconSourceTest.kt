package com.takeruf.nagi

import com.takeruf.nagi.ui.components.faviconSource
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FaviconSourceTest {
    @Test fun missingSavedIconUsesOnlyTheSitesOrigin() {
        assertEquals("https://example.com:8443/favicon.ico",
            faviconSource("/missing/nagi-favicon.png", "https://user:password@example.com:8443/path?q=private#section"))
    }
    @Test fun savedIconTakesPriority() {
        val file = File.createTempFile("nagi-icon", ".png")
        try { assertEquals(file, faviconSource(file.absolutePath, "https://example.com")) }
        finally { file.delete() }
        assertEquals("https://example.com/custom.png", faviconSource("https://example.com/custom.png", "https://example.com"))
    }
    @Test fun nonWebsiteAndInvalidAddressesDoNotRequestIcons() {
        listOf(null, "about:blank", "file:///tmp/site", "javascript:alert(1)", "not a url").forEach {
            assertNull(faviconSource(null, it))
        }
    }
}
