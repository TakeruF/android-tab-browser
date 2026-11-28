package com.takeruf.nagi.browser.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import kotlin.math.sqrt

/** UI-thread cache shared by engines. Evicted images may still be held by an active gesture. */
internal class HistoryPreviewImageCache(val maxBytes: Long) {
    private data class Key(val owner: Any, val entry: Any)
    private val images = LinkedHashMap<Key, Bitmap>(16, 0.75f, true)
    var byteCount = 0L
        private set
    val maxImagePixels get() = minOf(4_194_304L, maxBytes / (4 * 4)).coerceAtLeast(1)

    fun get(owner: Any, entry: Any): Bitmap? = images[Key(owner, entry)]
    fun put(owner: Any, entry: Any, bitmap: Bitmap) {
        remove(owner, entry)
        val bytes = bitmap.allocationByteCount.toLong()
        if (bytes > maxBytes) return
        images[Key(owner, entry)] = bitmap
        byteCount += bytes
        while (byteCount > maxBytes) {
            val oldest = images.entries.iterator()
            byteCount -= oldest.next().value.allocationByteCount
            oldest.remove()
        }
        // Never recycle: Compose can still be displaying an evicted image.
    }
    fun remove(owner: Any, entry: Any) {
        images.remove(Key(owner, entry))?.let { byteCount -= it.allocationByteCount }
    }
    fun clear(owner: Any) {
        val iterator = images.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            if (item.key.owner === owner) {
                byteCount -= item.value.allocationByteCount
                iterator.remove()
            }
        }
    }
    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1.0, sqrt(maxImagePixels.toDouble() / (width.toDouble() * height)))
        return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
    }
    companion object {
        private var shared: HistoryPreviewImageCache? = null
        fun shared(context: Context): HistoryPreviewImageCache = shared ?: run {
            val heapMiB = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 128
            HistoryPreviewImageCache(minOf(96L, heapMiB.coerceAtLeast(64) / 4L) * 1024 * 1024)
                .also { shared = it }
        }
    }
}
