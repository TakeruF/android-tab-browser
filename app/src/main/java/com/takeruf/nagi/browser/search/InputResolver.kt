package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.SearchEngine
import com.takeruf.nagi.domain.model.BrowserSettings
import java.net.IDN
import java.net.URI
import java.net.URLEncoder

sealed interface ResolvedInput {
    data class Navigate(val url: String) : ResolvedInput
    data class Search(val engine: SearchEngine, val query: String, val url: String) : ResolvedInput
    data class Invalid(val reason: String) : ResolvedInput
}

/** Pure policy: UI and engine never guess whether an input is a URL. */
object InputResolver {
    fun resolve(input: String, engines: List<SearchEngine>, settings: BrowserSettings): ResolvedInput =
        resolve(input, engines.filter { it.id in CommonSearchEngines.ids(settings) }, settings.defaultSearchEngineId)

    fun resolve(input: String, engines: List<SearchEngine>, defaultId: String): ResolvedInput {
        val text = input.trim()
        if (text.isEmpty()) return ResolvedInput.Invalid("Enter a URL or search query")
        val first = text.substringBefore(' ').lowercase()
        val keywordEngine = engines.firstOrNull { it.keyword.lowercase() == first }
        if (keywordEngine != null && text.contains(' ') && text.substringAfter(' ').isNotBlank())
            return search(keywordEngine, text.substringAfter(' ').trim())
        normalizeUrl(text)?.let { return ResolvedInput.Navigate(it) }
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(text) ||
            Regex("^(javascript|data|file|content|intent):", RegexOption.IGNORE_CASE).containsMatchIn(text))
            return ResolvedInput.Invalid("Only http and https addresses can be opened here")
        val engine = engines.firstOrNull { it.id == defaultId } ?: engines.firstOrNull()
            ?: return ResolvedInput.Invalid("Add a search engine in Settings")
        return search(engine, text)
    }

    fun search(engine: SearchEngine, query: String): ResolvedInput.Search {
        val encoded = URLEncoder.encode(query, "UTF-8")
        // A path placeholder needs percent-encoded spaces; '+' only means space in a query string.
        val replacement = if (engine.urlTemplate.substringBefore('?').contains("{query}")) encoded.replace("+", "%20") else encoded
        return ResolvedInput.Search(engine, query, engine.urlTemplate.replace("{query}", replacement))
    }

    fun normalizeUrl(input: String): String? {
        if (input.any { it.isWhitespace() }) return null
        val explicit = input.startsWith("https://", true) || input.startsWith("http://", true)
        if (!explicit && input.contains("://")) return null
        val candidate = if (explicit) input else "https://$input"
        return runCatching {
            // Browsers accept literal braces in paths, queries and fragments, but URI
            // rejects them. Escape only that suffix, preserving the authority checks
            // and existing percent escapes (including encoded URL delimiters).
            val suffixStart = candidate.indexOfAny(charArrayOf('/', '?', '#'), candidate.indexOf("://") + 3)
            val parseable = if (suffixStart < 0) candidate else
                candidate.substring(0, suffixStart) + candidate.substring(suffixStart)
                    .replace("{", "%7B").replace("}", "%7D")
            val uri = URI(parseable)
            if (uri.rawUserInfo != null) return null
            val authority = uri.rawAuthority ?: return null
            if ('@' in authority) return null
            val ipv6 = authority.startsWith('[')
            val rawHost: String
            val rawPort: String?
            if (ipv6) {
                val closing = authority.indexOf(']')
                if (closing < 0 || uri.host == null) return null
                rawHost = authority.substring(0, closing + 1)
                val suffix = authority.substring(closing + 1)
                if (suffix.isNotEmpty() && !suffix.startsWith(':')) return null
                rawPort = suffix.takeIf { it.isNotEmpty() }?.removePrefix(":")
            } else {
                if (authority.count { it == ':' } > 1) return null
                rawHost = authority.substringBefore(':')
                rawPort = if (':' in authority) authority.substringAfter(':') else null
            }
            val host = if (ipv6) rawHost.lowercase() else IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).lowercase()
            if (host.isBlank() || host.length > 253) return null
            val portNumber = rawPort?.let { it.toIntOrNull()?.takeIf { value -> value in 1..65535 } ?: return null }
            val plausible = host == "localhost" || host.contains('.') || ipv6
            if (!explicit && !plausible) return null
            if (!ipv6 && host.split('.').any { it.isBlank() || it.length > 63 || !Regex("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?").matches(it) }) return null
            if (Regex("[0-9.]+").matches(host) && (host.split('.').size != 4 || host.split('.').any { (it.toIntOrNull() ?: -1) !in 0..255 })) return null
            val scheme = uri.scheme.lowercase()
            val port = portNumber?.let { ":$it" } ?: ""
            "$scheme://$host$port${uri.rawPath.orEmpty()}" +
                (uri.rawQuery?.let { "?$it" } ?: "") + (uri.rawFragment?.let { "#$it" } ?: "")
        }.getOrNull()
    }

    fun validateEngine(engine: SearchEngine): String? {
        if (engine.name.isBlank()) return "Name is required"
        if (!Regex("[a-z0-9_-]+").matches(engine.keyword)) return "Keyword must use lowercase letters, numbers, - or _"
        if (!engine.urlTemplate.contains("{query}")) return "Search URL must contain {query}"
        if (normalizeUrl(engine.urlTemplate.replace("{query}", "test")) == null ||
            !engine.urlTemplate.startsWith("https://", true)) return "Use a valid https search URL"
        if (!engine.iconUrl.isNullOrBlank() && normalizeUrl(engine.iconUrl) == null) return "Icon URL is invalid"
        return null
    }
}
