package com.takeruf.nagi

import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class ChatGptSearchTest {
    private val workspace = WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all)
    private fun suggestions(query: String, defaultEngine: String = "google") =
        SuggestionProvider.suggestions(query, workspace, BrowserSettings(defaultSearchEngineId = defaultEngine))

    @Test fun shortSearchKeepsConfiguredEngineFirstAndChatGptSecond() {
        val result = suggestions("android tablet", "ddg")
        assertEquals(listOf("primary", "ai:chatgpt"), result.take(2).map { it.id })
        assertTrue((result.first().action as SuggestionAction.Navigate).url.startsWith("https://duckduckgo.com/"))
    }

    @Test fun longInputAndMultilingualRequestsPreferAiWithoutRemovingSearch() {
        for (query in listOf("a".repeat(79) + " b", "円安を説明して", "Explain inflation", "比较这两个方案", "이 부분을 설명해", "Why inflation?")) {
            assertEquals(query, listOf("ai:chatgpt", "primary"), suggestions(query).take(2).map { it.id })
        }
        assertEquals("primary", suggestions("a".repeat(79)).first().id)
        assertFalse(ChatGptSearch.preferFor("😀".repeat(40)))
    }

    @Test fun explicitSearchKeywordWinsEvenForLongQuestions() {
        val result = SuggestionProvider.suggestions("DDG Explain inflation?", workspace,
            BrowserSettings(commonSearchEngineIds = setOf("google", "ddg", "chatgpt")))
        assertEquals("primary", result.first().id)
        assertTrue((result.first().action as SuggestionAction.Navigate).url.startsWith("https://duckduckgo.com/"))
        assertEquals(ChatGptSearch.url("Explain inflation?"), (result[1].action as SuggestionAction.Navigate).url)
    }

    @Test fun urlsCommandsEmptyInputAndUnsafeSchemesNeverProduceAiHandoff() {
        for (query in listOf("", ">split", "https://example.com/", "example.com", "javascript:alert(1)", "file:///etc/passwd")) {
            assertFalse(query, suggestions(query).any { it.id == "ai:chatgpt" })
        }
    }

    @Test fun handoffPreservesUnicodeNewlinesAndQueryDelimiters() {
        val query = "日本語 한국어 中文 😀\n&temporary-chat=true + # /?"
        val uri = URI(ChatGptSearch.url(query))
        assertEquals("https", uri.scheme)
        assertEquals("chatgpt.com", uri.host)
        assertNull(uri.fragment)
        assertEquals(1, uri.rawQuery.split('&').size)
        assertEquals(query, URLDecoder.decode(uri.rawQuery.removePrefix("q="), "UTF-8"))
    }
}
