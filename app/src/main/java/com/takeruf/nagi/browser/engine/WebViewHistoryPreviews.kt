package com.takeruf.nagi.browser.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import kotlin.math.abs
import kotlin.math.sqrt

/** UI-thread only. Three recent destinations plus the current viewport, at 0.5 MP each. */
internal class WebViewHistoryPreviews(private val view: WebView) {
    private data class Entry(val index: Int, val url: String)
    private data class Image(val bitmap: Bitmap, val aspect: Float)
    private val images = linkedMapOf<Entry, Image>()
    private var visible: Entry? = null
    private var captured = false
    private var traversing = false
    private var awaitingDocument = false

    /** Save before native input reaches SPA click/key handlers, without pruning history. */
    fun interaction() {
        if (!awaitingDocument && !captured) capture()
    }

    fun leaving(traverse: Boolean = traversing, refresh: Boolean = true) {
        traversing = traverse
        val entry = visible ?: return
        if (!traverse) images.keys.removeAll { it.index > entry.index }
        if (captured) return
        if (refresh) capture()
        captured = true
    }

    private fun capture() {
        val entry = visible ?: return
        if (view.width <= 0 || view.height <= 0 || !view.isShown) return
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
        while (images.size > 4) images.remove(images.keys.first())
    }

    // By page-start the renderer may already have cleared its old pixels. Keep
    // the last ready/input snapshot instead of overwriting it with a blank frame.
    fun started() { leaving(traversing, refresh = false); awaitingDocument = true }

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
        // Same-document navigation can change DOM before its native callback. Keep
        // an image of the current entry in advance rather than drawing the new DOM
        // and labelling it as the departing page. No polling or speculative loads.
        val entry = visible
        view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (visible == entry && !awaitingDocument && !captured && view.url == entry?.url) capture()
            }
        })
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
