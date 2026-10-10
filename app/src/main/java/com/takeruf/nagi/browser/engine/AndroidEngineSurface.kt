package com.takeruf.nagi.browser.engine

import android.view.View

/** Android rendering bridge only; browser commands stay on BrowserEngine. */
interface AndroidEngineSurface { val surface: View }
interface FullscreenHost {
    fun showFullscreen(view: View, exit: () -> Unit)
    fun hideFullscreen()
    fun showVideo(view: View, tabId: String, playback: VideoPlayback, popup: Boolean,
        control: (String) -> Unit, exit: () -> Unit) = showFullscreen(view, exit)
    fun updateVideo(tabId: String, playback: VideoPlayback) {}
    fun minimizeVideo(tabId: String) {}
    fun hideVideo(tabId: String) = hideFullscreen()
}
