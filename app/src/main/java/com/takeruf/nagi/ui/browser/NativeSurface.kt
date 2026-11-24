package com.takeruf.nagi.ui.browser

import android.content.Context
import android.view.MotionEvent
import android.view.InputDevice
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.takeruf.nagi.browser.engine.AndroidEngineSurface
import com.takeruf.nagi.browser.engine.BrowserEngine

@Composable
fun BrowserSurface(engine: BrowserEngine, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    val renderer = engine as AndroidEngineSurface
    val colors = MaterialTheme.colorScheme
    SideEffect {
        renderer.setDragPreviewColors(colors.surfaceContainerHigh.toArgb(), colors.onSurface.toArgb(),
            colors.outlineVariant.toArgb(), colors.primary.toArgb())
    }
    NativeSurface(renderer.surface, modifier, onFocus, engine)
}

/** Attaches a native surface; history input stays at the native boundary. */
@Composable
fun NativeSurface(surface: View, modifier: Modifier = Modifier, onFocus: () -> Unit = {}, historyEngine: BrowserEngine? = null) {
    val currentOnFocus = rememberUpdatedState(onFocus)
    AndroidView(factory = { context -> SurfaceContainer(context) { currentOnFocus.value() } }, modifier = modifier,
        onRelease = { container ->
            val owner = container.getTag(com.takeruf.nagi.R.id.native_surface_owner) as? View
            if (owner?.getTag(com.takeruf.nagi.R.id.video_source_container) === container)
                owner.setTag(com.takeruf.nagi.R.id.video_source_container, null)
            container.setTag(com.takeruf.nagi.R.id.native_surface_owner, null)
            container.setHistoryEngine(null); container.removeAllViews()
        }, update = { container ->
            val previous = container.getTag(com.takeruf.nagi.R.id.native_surface_owner) as? View
            if (previous !== surface && previous?.getTag(com.takeruf.nagi.R.id.video_source_container) === container)
                previous.setTag(com.takeruf.nagi.R.id.video_source_container, null)
            // Compose can reuse this holder for a different tab while its former
            // WebView is in a video overlay. Restoration must check this owner.
            container.setTag(com.takeruf.nagi.R.id.native_surface_owner, surface)
            container.setHistoryEngine(historyEngine)
            if (historyEngine != null) surface.setTag(com.takeruf.nagi.R.id.video_source_container, container)
            // An automatic video overlay temporarily owns this same WebView.
            if (historyEngine != null && surface.getTag(com.takeruf.nagi.R.id.video_presented_surface) == true) {
                container.removeAllViews()
                return@AndroidView
            }
            if (surface.parent !== container || container.childCount != 1 || container.getChildAt(0) !== surface) {
                (surface.parent as? ViewGroup)?.removeView(surface)
                container.removeAllViews()
                container.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        })
}

/** Observe focus at the native boundary so Compose's pointer interop stays the sole input owner. */
private class SurfaceContainer(context: Context, private val onFocus: () -> Unit) : FrameLayout(context) {
    private var historyEngine: BrowserEngine? = null
    private class MouseGesture(val downTime: Long, val deviceId: Int,
        var pointerDown: Boolean = true, var buttonsReleased: Boolean = false)
    private var nativeMouseGesture: MouseGesture? = null
    private val historySwipe = HistorySwipeGesture(context,
        canNavigate = { back -> historyEngine?.let { if (back) it.canGoBack() else it.canGoForward() } == true },
        navigate = { back -> onFocus(); historyEngine?.let { if (back) it.goBack() else it.goForward() } },
        canScroll = { x, y, direction, result ->
            historyEngine?.canScrollHorizontallyAt(x, y, direction, result) ?: result(true)
        })

    fun setHistoryEngine(engine: BrowserEngine?) {
        if (historyEngine !== engine) { historySwipe.reset(); nativeMouseGesture = null; historyEngine = engine }
    }
    init {
        // Exclude the host itself while preserving the native surface's virtual fields.
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val mouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        if (mouse && event.actionMasked == MotionEvent.ACTION_DOWN)
            nativeMouseGesture = MouseGesture(event.downTime, event.deviceId)
        try {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onFocus()
            if (historyEngine != null && historySwipe.onTouch(event) {
                    val cancel = MotionEvent.obtain(event)
                    try { cancel.action = MotionEvent.ACTION_CANCEL; super.dispatchTouchEvent(cancel) }
                    finally { cancel.recycle() }
                }) return true
            return super.dispatchTouchEvent(event)
        } finally {
            if (event.actionMasked == MotionEvent.ACTION_CANCEL) nativeMouseGesture = null
            else if (mouse && event.actionMasked == MotionEvent.ACTION_UP) {
                nativeMouseGesture?.let { gesture ->
                    gesture.pointerDown = false
                    if (gesture.buttonsReleased) nativeMouseGesture = null
                }
            }
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        // Android delivers mouse button events separately from DOWN/UP. Generic
        // dispatch can reach this focused native child even when Compose owns the
        // click on an overlaid card. Do not synthesize a second page click there.
        if (event.isFromSource(InputDevice.SOURCE_MOUSE) &&
            (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS || event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE)) {
            val gesture = nativeMouseGesture
            if (gesture == null || gesture.downTime != event.downTime || gesture.deviceId != event.deviceId ||
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS && !gesture.pointerDown) {
                if (gesture?.pointerDown == false) nativeMouseGesture = null
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE && event.buttonState == 0) gesture.buttonsReleased = true
            // Keep a matching final generic release even after UP. It still
            // belongs to this native click, not to a newly shown/dismissed overlay.
            try { return super.dispatchGenericMotionEvent(event) }
            finally { if (!gesture.pointerDown && gesture.buttonsReleased) nativeMouseGesture = null }
        }
        if (event.actionMasked != MotionEvent.ACTION_SCROLL) return super.dispatchGenericMotionEvent(event)
        onFocus()
        if (historyEngine != null && historySwipe.onScroll(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onDetachedFromWindow() {
        nativeMouseGesture = null
        super.onDetachedFromWindow()
    }

    override fun onViewRemoved(child: View) {
        nativeMouseGesture = null
        super.onViewRemoved(child)
    }
}
