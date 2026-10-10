package com.takeruf.nagi.browser.reader

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

data class ReaderArticle(val title: String, val byline: String, val content: String, val sourceUrl: String)

object ReaderSanitizer {
    // Readability extracts text; jsoup independently removes executable markup and unsafe URLs.
    fun clean(html: String, sourceUrl: String): String = Jsoup.clean(html, sourceUrl,
        Safelist.relaxed().removeTags("iframe", "form", "input", "button", "style")
            .removeAttributes("all", "style", "class", "id")
            .removeProtocols("a", "href", "ftp", "mailto")
            .addProtocols("a", "href", "http", "https")
            .addProtocols("img", "src", "http", "https"))
}
