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
    NativeSurface(surface, modifier, onFocus)
}

/** Only attaches a native surface. No browsing logic or WebView API lives in Compose. */
@Composable
fun NativeSurface(surface: View, modifier: Modifier = Modifier, onFocus: () -> Unit = {}) {
    val currentOnFocus = rememberUpdatedState(onFocus)
    AndroidView(factory = { context -> SurfaceContainer(context) { currentOnFocus.value() } }, modifier = modifier,
        onRelease = { container -> container.removeAllViews() }, update = { container ->
            if (surface.parent !== container) {
                (surface.parent as? ViewGroup)?.removeView(surface)
                container.removeAllViews()
                container.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        })
}

/** Observe focus at the native boundary so Compose's pointer interop stays the sole input owner. */
private class SurfaceContainer(context: Context, private val onFocus: () -> Unit) : FrameLayout(context) {
    init {
        // Exclude the host itself while preserving the native surface's virtual fields.
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onFocus()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_SCROLL) return super.dispatchGenericMotionEvent(event)
        onFocus()
        return super.dispatchGenericMotionEvent(event)
    }
}
