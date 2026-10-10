package org.mlm.adblock

import android.net.Uri

/**
 * Maps an Android/Gecko request descriptor to an adblock-rust [request type].
 *
 * The adblock crate expects one of: document, subdocument, script, image,
 * stylesheet, xmlhttprequest, media, font, websocket, other.
 */
object RequestTypeMapper {

    private const val TYPE_DOCUMENT = "document"
    private const val TYPE_SUBDOCUMENT = "subdocument"
    private const val TYPE_SCRIPT = "script"
    private const val TYPE_IMAGE = "image"
    private const val TYPE_STYLESHEET = "stylesheet"
    private const val TYPE_XMLHTTPREQUEST = "xmlhttprequest"
    private const val TYPE_MEDIA = "media"
    private const val TYPE_FONT = "font"
    private const val TYPE_WEBSOCKET = "websocket"
    private const val TYPE_OTHER = "other"

    /**
     * @param url requested URL (used as a fallback when the type string is unhelpful)
     * @param mimeOrType either an HTTP `Accept` header (WebView path) or a
     *   Gecko web-extension request type (script/image/stylesheet/...)
     */
    fun from(url: Uri, mimeOrType: String?): String {
        val t = mimeOrType?.trim()?.lowercase().orEmpty()

        if (t.isEmpty()) return fromExtension(url)

        // Gecko web-extension request types already align with adblock-rust.
        when (t) {
            "document", "main_frame" -> return TYPE_DOCUMENT
            "subdocument", "sub_frame", "iframe" -> return TYPE_SUBDOCUMENT
            "script" -> return TYPE_SCRIPT
            "image", "imageset" -> return TYPE_IMAGE
            "stylesheet" -> return TYPE_STYLESHEET
            "xmlhttprequest", "xhr", "fetch", "beacon", "ping" -> return TYPE_XMLHTTPREQUEST
            "media" -> return TYPE_MEDIA
            "font" -> return TYPE_FONT
            "websocket" -> return TYPE_WEBSOCKET
            "other" -> return TYPE_OTHER
        }

        // Accept-header / MIME heuristics.
        when {
            t.contains("text/css") || t.contains("/css") -> return TYPE_STYLESHEET
            t.contains("javascript") || t.contains("ecmascript") -> return TYPE_SCRIPT
            t.contains("image/") || t == "image" -> return TYPE_IMAGE
            t.contains("font/") || t.contains("woff") || t.contains("/opentype") ->
                return TYPE_FONT
            t.contains("video/") || t.contains("audio/") -> return TYPE_MEDIA
            t.contains("json") || t.contains("xml") || t.contains("form-urlencoded") ->
                return TYPE_XMLHTTPREQUEST
            t.contains("text/html") -> return TYPE_SUBDOCUMENT
        }

        return fromExtension(url)
    }

    private fun fromExtension(url: Uri): String {
        val path = url.lastPathSegment?.lowercase().orEmpty()
        return when {
            path.endsWith(".js") || path.endsWith(".mjs") -> TYPE_SCRIPT
            path.endsWith(".css") -> TYPE_STYLESHEET
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") ||
                path.endsWith(".bmp") || path.endsWith(".avif") || path.endsWith(".ico") ->
                TYPE_IMAGE
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") ||
                path.endsWith(".otf") || path.endsWith(".eot") -> TYPE_FONT
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m3u8") ||
                path.endsWith(".mp3") || path.endsWith(".ogg") || path.endsWith(".wav") ||
                path.endsWith(".m4a") -> TYPE_MEDIA
            else -> TYPE_OTHER
        }
    }
}
