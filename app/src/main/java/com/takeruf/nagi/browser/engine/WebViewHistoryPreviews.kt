package com.takeruf.nagi.browser.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.sqrt

/** UI-thread only. Three viewport images, each capped at 0.5 MP, per live engine. */
internal class WebViewHistoryPreviews(private val view: WebView) {
    private data class Entry(val index: Int, val url: String)
    private data class Image(val bitmap: Bitmap, val aspect: Float)
    private val images = linkedMapOf<Entry, Image>()
    private var visible: Entry? = null
    private var captured = false
    private var traversing = false
    private var awaitingDocument = false

    fun leaving(traverse: Boolean = traversing) {
        traversing = traverse
        val entry = visible ?: return
        if (!traverse) images.keys.removeAll { it.index > entry.index }
        if (captured || view.width <= 0 || view.height <= 0 || !view.isShown) return
        captured = true
        val scale = minOf(1f, sqrt(524_288f / (view.width.toFloat() * view.height)))
        val bitmap = createBitmap((view.width * scale).toInt().coerceAtLeast(1),
            (view.height * scale).toInt().coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        // View.draw uses scroll coordinates even for a viewport-sized canvas.
        canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        view.draw(canvas)
        images.remove(entry)
        images[entry] = Image(bitmap, view.width.toFloat() / view.height)
        while (images.size > 3) images.remove(images.keys.first())
    }

    fun started() { leaving(traversing); awaitingDocument = true }

    /** pushState/fragment traversals do not have page-start/commit callbacks. */
    fun historyUpdated(): Boolean {
        if (awaitingDocument) return false
        committed()
        return true
    }

    fun committed() {
        val history = view.copyBackForwardList()
        val item = history.currentItem ?: return
        visible = Entry(history.currentIndex, item.url)
        captured = false
        traversing = false
        awaitingDocument = false
        images.keys.removeAll { history.getItemAtIndex(it.index)?.url != it.url }
    }

    fun preview(back: Boolean): HistoryPagePreview? {
        val history = view.copyBackForwardList()
        val index = history.currentIndex + if (back) -1 else 1
        val url = history.getItemAtIndex(index)?.url ?: return null
        val image = images[Entry(index, url)]?.takeIf {
            view.height > 0 && abs(it.aspect - view.width.toFloat() / view.height) < 0.05f
        }
        return HistoryPagePreview(url, image?.bitmap)
    }

    fun clear() { images.clear(); visible = null; captured = false; traversing = false; awaitingDocument = false }
}
