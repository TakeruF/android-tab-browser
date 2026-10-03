package com.orbit.browser

import com.orbit.browser.browser.search.*
import com.orbit.browser.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class InputResolverTest {
    private val engines = DefaultSearchEngines.all
    @Test fun handlesRequestedUrlForms() {
        mapOf("google.com" to "https://google.com", "https://google.com" to "https://google.com",
            "localhost:8080" to "https://localhost:8080", "192.168.1.1" to "https://192.168.1.1",
            "http://localhost:8080/a?q=1#top" to "http://localhost:8080/a?q=1#top").forEach { (input, expected) ->
            assertEquals(ResolvedInput.Navigate(expected), InputResolver.resolve(input, engines, "google"))
        }
    }
    @Test fun preservesQueryAndSupportsInternationalHostsAndIpv6() {
        assertEquals("https://xn--r8jz45g.jp:8443/path?q=a%20b", InputResolver.normalizeUrl("例え.jp:8443/path?q=a%20b"))
        assertEquals("https://[::1]:8080/test", InputResolver.normalizeUrl("[::1]:8080/test"))
    }
    @Test fun refusesMalformedAddressesInsteadOfSilentlyDroppingPorts() {
        listOf("https://google.com:abc", "https://google.com:70000", "https://google.com:",
            "https://user:password@example.com", "999.168.1.1", "-bad.com", "google..com").forEach {
            assertNull(it, InputResolver.normalizeUrl(it))
        }
    }
    @Test fun searchesNaturalLanguageWithEncoding() {
        val value = InputResolver.resolve("android tablet browser & 日本語", engines, "ddg") as ResolvedInput.Search
        assertEquals("ddg", value.engine.id)
        assertEquals("https://duckduckgo.com/?q=android+tablet+browser+%26+%E6%97%A5%E6%9C%AC%E8%AA%9E", value.url)
    }
    @Test fun keywordOverridesDefault() {
        mapOf("g android webview" to "google", "ddg android browser" to "ddg", "yt android tablet" to "youtube",
            "gh chromium android" to "github", "scholar web browser usability" to "scholar").forEach { (input, id) ->
            assertEquals(id, (InputResolver.resolve(input, engines, "bing") as ResolvedInput.Search).engine.id)
        }
    }
    @Test fun acceptsUserDefinedEngineAndCaseInsensitiveKeyword() {
        val custom = SearchEngine("custom", "Custom", "x", "https://example.com/?q={query}")
        assertEquals("https://example.com/?q=a%26b", (InputResolver.resolve("X a&b", listOf(custom), "custom") as ResolvedInput.Search).url)
    }
    @Test fun unknownKeywordAndBareKeywordUseDefault() {
        assertEquals("bing", (InputResolver.resolve("unknown android", engines, "bing") as ResolvedInput.Search).engine.id)
        assertEquals("bing", (InputResolver.resolve("g", engines, "bing") as ResolvedInput.Search).engine.id)
    }
    @Test fun refusesExecutableSchemes() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "data:text/html,test", "intent://abc", "ftp://example.com").forEach {
            assertTrue(InputResolver.resolve(it, engines, "google") is ResolvedInput.Invalid)
        }
    }
    @Test fun validatesCustomEngineTemplate() {
        val good = SearchEngine("x", "Example", "x", "https://example.com/search?q={query}")
        assertNull(InputResolver.validateEngine(good))
        assertNotNull(InputResolver.validateEngine(good.copy(urlTemplate = "https://example.com")))
        assertNotNull(InputResolver.validateEngine(good.copy(urlTemplate = "javascript:{query}")))
        assertNotNull(InputResolver.validateEngine(good.copy(keyword = "two words")))
    }
    @Test fun suggestionsSearchAcrossSpacesAndExcludeClosedTabs() {
        val workspace = WorkspaceSnapshot(spaces = listOf(Space("work", "Work")),
            tabs = listOf(BrowserTab("open", "work", title = "Android Docs"),
                BrowserTab("closed", "work", title = "Android", closedAt = 1)),
            searchEngines = engines, history = listOf(HistoryEntry(url = "https://android.com", title = "Android")))
        val results = SuggestionProvider.suggestions("android", workspace, BrowserSettings())
        assertTrue(results.any { it.id == "tab:open" })
        assertFalse(results.any { it.id == "tab:closed" })
        assertTrue(results.any { it.category == SuggestionCategory.HISTORY })
    }
    @Test fun commandPrefixDoesNotProduceWebSearch() {
        val results = SuggestionProvider.suggestions(">split", WorkspaceSnapshot(searchEngines = engines), BrowserSettings())
        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.category == SuggestionCategory.COMMANDS })
    }
}
