package com.takeruf.nagi.browser.engine

import android.view.View

/** Android rendering bridge only; browser commands stay on BrowserEngine. */
interface AndroidEngineSurface { val surface: View }
interface FullscreenHost {
    fun showFullscreen(view: View, exit: () -> Unit)
    fun hideFullscreen()
}
