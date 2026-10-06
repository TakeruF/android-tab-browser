package com.takeruf.nagi.browser.search

import java.net.URLEncoder

/** Web handoff only: no API key, page content extraction, or background requests. */
object ChatGptSearch {
    fun url(query: String): String = "https://chatgpt.com/?q=${URLEncoder.encode(query, "UTF-8")}"

    // A local ordering hint, never a restriction on which action the user can choose.
    fun preferFor(query: String): Boolean {
        val text = query.trim().lowercase()
        return text.codePointCount(0, text.length) >= 80 ||
            text.endsWith('?') || text.endsWith('？') ||
            requestMarkers.any { text.contains(it) }
    }

    private val requestMarkers = listOf(
        "説明して", "比較して", "要約して", "教えて", "解説して", "考えて", "まとめて",
        "解释", "解釋", "比较", "比較", "总结", "總結", "告诉我", "告訴我",
        "설명해", "비교해", "요약해", "알려줘",
        "explain ", "compare ", "summarize ", "summarise ", "tell me ",
    )
}
