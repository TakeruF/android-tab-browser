package com.takeruf.nagi.browser.engine

import android.graphics.Canvas
import android.webkit.WebView
import androidx.core.graphics.createBitmap
import kotlin.math.abs

/** UI-thread only. Three destinations plus the current viewport, under a shared byte budget. */
internal class WebViewHistoryPreviews(private val view: WebView) {
    private data class Entry(val index: Int, val url: String)
    private val owner = Any()
    private val cache = HistoryPreviewImageCache.shared(view.context)
    private val entries = linkedSetOf<Entry>()
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
        if (!traverse) entries.filter { it.index > entry.index }.forEach(::remove)
        if (captured) return
        if (refresh) capture()
        captured = true
    }

    private fun remove(entry: Entry) { entries.remove(entry); cache.remove(owner, entry) }

    private fun capture(requireShown: Boolean = true) {
        val entry = visible ?: return
        if (view.width <= 0 || view.height <= 0 || (requireShown && !view.isShown)) return
        val (width, height) = cache.dimensions(view.width, view.height)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.scale(width.toFloat() / view.width, height.toFloat() / view.height)
        // View.draw uses scroll coordinates even for a viewport-sized canvas.
        canvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
        view.draw(canvas)
        entries.remove(entry)
        entries.add(entry)
        cache.put(owner, entry, bitmap)
        while (entries.size > 4) remove(entries.first())
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
        entries.filter { history.getItemAtIndex(it.index)?.url != it.url }.forEach(::remove)
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
        val image = cache.get(owner, Entry(index, url))?.takeIf {
            view.height > 0 && abs(it.width.toFloat() / it.height - view.width.toFloat() / view.height) < 0.05f
        }
        return HistoryPagePreview(url, image)
    }

    /** Draw the retained document only; never wake or navigate a destination tab. */
    fun current(): HistoryPagePreview? {
        val entry = visible ?: return null
        if (entry.url == "about:blank") return HistoryPagePreview(entry.url, null)
        if (!awaitingDocument) capture(requireShown = false)
        return HistoryPagePreview(entry.url, cache.get(owner, entry))
    }

    fun clear() { cache.clear(owner); entries.clear(); visible = null; captured = false; traversing = false; awaitingDocument = false }
}
