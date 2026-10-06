package com.takeruf.nagi.browser.engine

import android.webkit.MimeTypeMap

object FileSelectionPolicy {
    fun types(request: FileSelectionRequest) = request.mimeTypes.flatMap { it.split(',') }.mapNotNull { value ->
        val type = value.trim().lowercase()
        when {
            type.startsWith('.') -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1))
            Regex("[a-z0-9!#$&^_.+-]+/([a-z0-9!#$&^_.+-]+|\\*)").matches(type) -> type
            type == "*/*" -> type
            else -> null
        }
    }.distinct()

    fun offersPhoto(request: FileSelectionRequest): Boolean {
        val types = types(request)
        return !request.multiple && (types.isEmpty() || types.all { it == "*/*" || it == "image/*" || it == "image/jpeg" })
    }
}
