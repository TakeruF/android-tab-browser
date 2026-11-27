package com.takeruf.nagi.browser.engine

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Geometry-only selection notifications, accepted once after a real native primary release. */
internal class PagePointerSelection(private val view: WebView,
    private val show: (Float, Float) -> Unit, private val noSelection: () -> Unit) {
    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private val script = view.context.assets.open("browser/pointer-selection.js").bufferedReader().use { it.readText() }
    private var pending = false
    private var releasedAt = 0L
    private var upX = 0f
    private var upY = 0f
    private var lastDownTime = -1L
    private var primaryDown = -1L
    private var destroyed = false
    private var interaction = 0L
    private val timeout = Runnable { if (pending) { pending = false; noSelection() } }
    private val scriptHandler = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
        WebViewCompat.addWebMessageListener(view, "nagiSelection", setOf("*")) { _, message, origin, _, _ ->
            if (destroyed || !pending || origin.scheme !in setOf("http", "https") ||
                SystemClock.uptimeMillis() - releasedAt > 700) return@addWebMessageListener
            val raw = message.data.orEmpty()
            if (raw.length > 256) return@addWebMessageListener
            val data = runCatching { JSONObject(raw) }.getOrNull() ?: return@addWebMessageListener
            pending = false
            view.removeCallbacks(timeout)
            if (!data.optBoolean("selected")) { noSelection(); return@addWebMessageListener }
            val dx = data.optDouble("dx").toFloat()
            val dy = data.optDouble("dy").toFloat()
            @Suppress("DEPRECATION") val scale = view.scale
            val x = upX + dx * scale
            val y = upY + dy * scale
            if (x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < view.width && y < view.height) {
                val expected = interaction
                // Let a trailing UP finish before entering native touch selection.
                view.postDelayed({ if (!destroyed && interaction == expected && view.isShown && view.hasFocus() && view.hasWindowFocus()) show(x, y) }, 32)
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*")) else null
    } else null

    /** True for a primary release whose snapshot should await the selection result. */
    fun onMotion(event: MotionEvent): Boolean {
        if (!supported || destroyed) return false
        val precise = event.isFromSource(InputDevice.SOURCE_MOUSE) || event.isFromSource(InputDevice.SOURCE_TOUCHPAD)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            invalidate()
            primaryDown = if (precise && event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) event.downTime else -1L
        }
        if (action == MotionEvent.ACTION_SCROLL || action == MotionEvent.ACTION_CANCEL) { invalidate(); primaryDown = -1L }
        val release = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_BUTTON_RELEASE && event.actionButton == MotionEvent.BUTTON_PRIMARY
        if (!precise || !release || event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) return false
        // Android may deliver the generic button release on either side of UP.
        // Do not create two requests or capture a software snapshot between them.
        if (event.downTime == lastDownTime) return SystemClock.uptimeMillis() - releasedAt < 1_000
        // A secondary gesture has no primary down and must not request an automatic menu.
        if (primaryDown != event.downTime) return false
        lastDownTime = event.downTime
        pending = true
        releasedAt = SystemClock.uptimeMillis()
        upX = event.x; upY = event.y
        view.postDelayed(timeout, 700)
        return true
    }
    fun installFallback() { if (supported && !destroyed) view.evaluateJavascript(script, null) }
    fun invalidate() { interaction++; primaryDown = -1L; pending = false; view.removeCallbacks(timeout) }
    fun destroy() {
        destroyed = true; invalidate()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) scriptHandler?.remove()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "nagiSelection")
    }
}
