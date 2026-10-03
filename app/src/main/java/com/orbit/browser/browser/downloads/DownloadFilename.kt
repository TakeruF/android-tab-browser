package com.orbit.browser.browser.downloads

import java.net.URLDecoder

object DownloadFilename {
    fun fromDisposition(value: String?): String? {
        if (value == null) return null
        val encoded = Regex("filename\\*\\s*=\\s*UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)
        if (encoded != null) return runCatching { URLDecoder.decode(encoded.trim().replace("+", "%2B"), "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
        return Regex("filename\\s*=\\s*(\"[^\"]*\"|[^;]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)
            ?.trim()?.trim('"')?.takeIf { it.isNotBlank() }
    }
}
