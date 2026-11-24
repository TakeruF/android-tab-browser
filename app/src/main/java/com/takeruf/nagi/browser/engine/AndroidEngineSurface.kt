package com.takeruf.nagi.browser.engine

import android.view.View
import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/** Ephemeral, engine-owned visual history; never persisted with workspace/history data. */
data class HistoryPagePreview(val url: String, val bitmap: Bitmap?)

/** Android rendering bridge only; browser commands stay on BrowserEngine. */
interface AndroidEngineSurface {
    val surface: View
    fun setDragPreviewColors(background: Int, content: Int, outline: Int, accent: Int) {}
    fun historyPagePreview(back: Boolean): HistoryPagePreview? = null
    val historyVisualGeneration: StateFlow<Long>? get() = null
}
interface FullscreenHost {
    fun showFullscreen(view: View, exit: () -> Unit)
    fun hideFullscreen()
    fun showVideo(view: View, tabId: String, playback: VideoPlayback, popup: Boolean,
        control: (String) -> Unit, exit: () -> Unit) = showFullscreen(view, exit)
    fun updateVideo(tabId: String, playback: VideoPlayback) {}
    fun minimizeVideo(tabId: String) {}
    fun hideVideo(tabId: String) = hideFullscreen()
    fun updateInlineVideo(view: View, tabId: String, playback: VideoPlayback,
        prepare: () -> Unit, restore: () -> Unit, control: (String) -> Unit) {}
    fun removeInlineVideo(tabId: String) {}
}
