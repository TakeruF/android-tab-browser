package com.takeruf.nagi.ui.browser

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.takeruf.nagi.browser.engine.AndroidEngineSurface
import com.takeruf.nagi.browser.engine.BrowserEngine

@Composable
fun BrowserSurface(engine: BrowserEngine, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    val surface = (engine as AndroidEngineSurface).surface
    NativeSurface(surface, modifier, onFocus, engine)
}

/** Attaches a native surface; history input stays at the native boundary. */
@Composable
fun NativeSurface(surface: View, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, historyEngine: BrowserEngine? = null) {
    val currentOnFocus = rememberUpdatedState(onFocus)
    AndroidView(factory = { context -> SurfaceContainer(context) { currentOnFocus.value() } }, modifier = modifier,
        onRelease = { container -> container.setHistoryEngine(null); container.removeAllViews() }, update = { container ->
            container.setHistoryEngine(historyEngine)
            if (surface.parent !== container) {
                (surface.parent as? ViewGroup)?.removeView(surface)
                container.removeAllViews()
                container.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        })
}

/** Observe focus at the native boundary so Compose's pointer interop stays the sole input owner. */
private class SurfaceContainer(context: Context, private val onFocus: () -> Unit) : FrameLayout(context) {
    private var historyEngine: BrowserEngine? = null
    private val historySwipe = HistorySwipeGesture(context,
        canNavigate = { back -> historyEngine?.let { if (back) it.canGoBack() else it.canGoForward() } == true },
        navigate = { back -> onFocus(); historyEngine?.let { if (back) it.goBack() else it.goForward() } },
        canScroll = { x, y, direction, result ->
            historyEngine?.canScrollHorizontallyAt(x, y, direction, result) ?: result(true)
        })

    fun setHistoryEngine(engine: BrowserEngine?) {
        if (historyEngine !== engine) { historySwipe.reset(); historyEngine = engine }
    }
    init {
        // Exclude the host itself while preserving the native surface's virtual fields.
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onFocus()
        if (historyEngine != null && historySwipe.onTouch(event) {
                val cancel = MotionEvent.obtain(event)
                try { cancel.action = MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(cancel) }
                finally { cancel.recycle() }
            }) return true
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_SCROLL) return super.dispatchGenericMotionEvent(event)
        onFocus()
        if (historyEngine != null && historySwipe.onScroll(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
