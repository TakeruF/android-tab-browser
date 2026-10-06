package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.SearchEngine

object DefaultSearchEngines {
    // Seeds only; existing installations receive additions once. Room then remains the source of truth.
    val all = listOf(
        SearchEngine("google", "Google", "g", "https://www.google.com/search?q={query}"),
        SearchEngine("baidu", "百度", "bd", "https://www.baidu.com/s?wd={query}"),
        SearchEngine("sogou", "搜狗", "sg", "https://www.sogou.com/web?query={query}"),
        SearchEngine("so360", "360", "360", "https://www.so.com/s?q={query}"),
        SearchEngine("douyin", "抖音", "dy", "https://www.douyin.com/search/{query}"),
        SearchEngine("shenma", "神马", "sm", "https://m.sm.cn/s?q={query}"),
        SearchEngine("bing", "Bing", "b", "https://www.bing.com/search?q={query}"),
        SearchEngine("ddg", "DuckDuckGo", "ddg", "https://duckduckgo.com/?q={query}"),
        SearchEngine("brave", "Brave Search", "brave", "https://search.brave.com/search?q={query}"),
        SearchEngine("scholar", "Google Scholar", "scholar", "https://scholar.google.com/scholar?q={query}"),
        SearchEngine("youtube", "YouTube", "yt", "https://www.youtube.com/results?search_query={query}"),
        SearchEngine("github", "GitHub", "gh", "https://github.com/search?q={query}"),
        SearchEngine("wikipedia", "Wikipedia", "wiki", "https://en.wikipedia.org/w/index.php?search={query}"),
    )
    val additionalIds = setOf("sogou", "so360", "douyin", "shenma")
}
