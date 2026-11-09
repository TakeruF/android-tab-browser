package com.takeruf.nagi.browser.engine

import android.webkit.WebView
import android.view.ViewGroup
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.takeruf.nagi.R
import org.json.JSONObject

/** Media-only messages from each frame. Pages get no app data or native privileged operations. */
internal class PageVideo(private val view: WebView,
    private val fullscreenHost: FullscreenHost, private val tabId: String) {
    private val script = view.context.assets.open("browser/page-video.js").bufferedReader().use { it.readText() }
    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private val proxies = linkedMapOf<String, JavaScriptReplyProxy>()
    private var selectedFrame: String? = null
    private var playbackFrame: String? = null
    private var controlProxy: JavaScriptReplyProxy? = null
    var playback = VideoPlayback()
        private set
    var active = false
    private var inlineActive = false
    private var sourceParent: ViewGroup? = null
    private var sourceLayout: ViewGroup.LayoutParams? = null
    private var destroyed = false
    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, "nagiVideo", setOf("*")) { _, message, origin, _, proxy ->
                if (destroyed || origin.scheme !in setOf("http", "https")) return@addWebMessageListener
                runCatching {
                    val data = message.data.orEmpty()
                    if (data.length <= 1024) receive(JSONObject(data), proxy)
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
            }
        }
    }
    fun installFallback() { if (supported) view.evaluateJavascript(script, null) }
    private fun receive(data: JSONObject, proxy: JavaScriptReplyProxy) {
        val frame = data.optString("frame")
        if (!Regex("[a-z0-9]{1,32}").matches(frame)) return
        if (frame !in proxies && proxies.size >= 64) return
        proxies[frame] = proxy
        val type = data.optString("type")
        if (type != "state") return
        if (data.optBoolean("fullscreen") && selectedFrame == null) selectedFrame = frame
        if (selectedFrame != null && selectedFrame != frame) return
        // Hidden/non-video frames must not erase the playing frame's metadata.
        if (selectedFrame == null && !data.optBoolean("hasVideo") && playbackFrame != frame) return
        playbackFrame = frame
        fun seconds(name: String) = data.optDouble(name, 0.0).takeIf { it.isFinite() }
            ?.coerceIn(0.0, 315_360_000.0) ?: 0.0
        playback = VideoPlayback(data.optBoolean("playing"), data.optInt("width", 16).coerceIn(1, 65536),
            data.optInt("height", 9).coerceIn(1, 65536), data.optBoolean("hasVideo"), seconds("currentTime"), seconds("duration"),
            autoPopoutAllowed = data.optBoolean("autoPopoutAllowed"))
        controlProxy = proxy
        if (active) fullscreenHost.updateVideo(tabId, playback)
        fullscreenHost.updateInlineVideo(view, tabId, playback, ::prepareInline, ::restoreInline, ::control)
    }
    private fun prepareInline() {
        if (inlineActive) return
        sourceParent = view.parent as? ViewGroup
        sourceLayout = view.layoutParams
        view.setTag(R.id.video_presented_surface, true)
        selectedFrame = playbackFrame
        inlineActive = true
        active = true
        view.onResume()
        // A child frame may expand only during an app-initiated presentation.
        val session = java.util.UUID.randomUUID().toString().replace("-", "")
        val preparation = JSONObject().put("type", "prepare-surface").put("session", session).toString()
        proxies.values.toList().forEach { proxy -> runCatching { proxy.postMessage(preparation) } }
        // Fit the video in JavaScript without changing the page's zoom. WebView scale
        // callbacks and viewport relayout are asynchronous across reparenting.
        control("present")
    }
    private fun restoreInline() {
        view.setTag(R.id.video_presented_surface, null)
        proxies.values.toList().forEach { proxy -> runCatching {
            proxy.postMessage(JSONObject().put("type", "control").put("action", "restore").toString())
        } }
        inlineActive = false
        release()
        val destination = (view.getTag(R.id.video_source_container) as? ViewGroup) ?: sourceParent
        view.setTag(R.id.video_source_container, null)
        destination?.takeIf { it.isAttachedToWindow && view.parent !== it }?.let { parent ->
            (view.parent as? ViewGroup)?.removeView(view)
            parent.addView(view, sourceLayout)
        }
        sourceParent = null
        sourceLayout = null
    }

    fun control(action: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching { controlProxy?.postMessage(JSONObject().put("type", "control").put("action", action).toString()) }
    }
    fun release() { active = false; selectedFrame = null }
    fun invalidate() { fullscreenHost.removeInlineVideo(tabId); release(); proxies.clear(); controlProxy = null; playbackFrame = null; playback = VideoPlayback() }
    fun destroy() { destroyed = true; invalidate(); if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "nagiVideo") }
}
