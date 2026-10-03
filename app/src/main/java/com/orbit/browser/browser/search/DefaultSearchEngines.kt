package com.orbit.browser.browser.search

import com.orbit.browser.domain.model.SearchEngine

object DefaultSearchEngines {
    // Seeds only; Room is the source of truth after the first launch.
    val all = listOf(
        SearchEngine("google", "Google", "g", "https://www.google.com/search?q={query}"),
        SearchEngine("baidu", "百度", "bd", "https://www.baidu.com/s?wd={query}"),
        SearchEngine("bing", "Bing", "b", "https://www.bing.com/search?q={query}"),
        SearchEngine("ddg", "DuckDuckGo", "ddg", "https://duckduckgo.com/?q={query}"),
        SearchEngine("brave", "Brave Search", "brave", "https://search.brave.com/search?q={query}"),
        SearchEngine("scholar", "Google Scholar", "scholar", "https://scholar.google.com/scholar?q={query}"),
        SearchEngine("youtube", "YouTube", "yt", "https://www.youtube.com/results?search_query={query}"),
        SearchEngine("github", "GitHub", "gh", "https://github.com/search?q={query}"),
        SearchEngine("wikipedia", "Wikipedia", "wiki", "https://en.wikipedia.org/w/index.php?search={query}"),
    )
}
