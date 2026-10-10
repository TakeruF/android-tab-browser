package com.takeruf.nagi.browser.engine

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.takeruf.nagi.R
import org.json.JSONObject

/** Media-only messages from each frame. Pages get no app data or native privileged operations. */
internal class PageVideo(private val view: WebView, private val host: BrowserHost,
    private val fullscreenHost: FullscreenHost, private val tabId: String) {
    private val script = view.context.assets.open("browser/page-video.js").bufferedReader().use { it.readText() }
    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private val proxies = linkedMapOf<String, JavaScriptReplyProxy>()
    private var selectedFrame: String? = null
    private var playbackFrame: String? = null
    private var controlProxy: JavaScriptReplyProxy? = null
    var playback = VideoPlayback()
        private set
    var popupRequested = false
        private set
    var active = false
    private var enabled = true
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
    fun setEnabled(value: Boolean) {
        enabled = value
        proxies.values.forEach(::configure)
        if (!value) popupRequested = false
    }
    private fun configure(proxy: JavaScriptReplyProxy) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching { proxy.postMessage(JSONObject().put("type", "config").put("enabled", enabled)
            .put("label", view.context.getString(R.string.ui_video_popout)).toString()) }
    }
    private fun receive(data: JSONObject, proxy: JavaScriptReplyProxy) {
        val frame = data.optString("frame")
        if (!Regex("[a-z0-9]{1,32}").matches(frame)) return
        if (frame !in proxies && proxies.size >= 64) return
        proxies[frame] = proxy
        val type = data.optString("type")
        if (type == "ready") { configure(proxy); return }
        if (type == "popup-failed") {
            popupRequested = false
            if (!active) selectedFrame = null
            host.showMessage(view.context.getString(R.string.ui_video_popup_failed))
            return
        }
        if (type == "popup" && enabled) {
            popupRequested = true
            selectedFrame = frame
            controlProxy = proxy
            if (active) fullscreenHost.minimizeVideo(tabId)
        }
        if (type !in setOf("state", "popup")) return
        if (data.optBoolean("fullscreen") && selectedFrame == null) selectedFrame = frame
        if (selectedFrame != null && selectedFrame != frame) return
        // Hidden/non-video frames must not erase the playing frame's metadata.
        if (selectedFrame == null && !data.optBoolean("hasVideo") && playbackFrame != frame) return
        playbackFrame = frame
        playback = VideoPlayback(data.optBoolean("playing"), data.optInt("width", 16).coerceIn(1, 65536),
            data.optInt("height", 9).coerceIn(1, 65536), data.optBoolean("hasVideo"))
        controlProxy = proxy
        if (active) fullscreenHost.updateVideo(tabId, playback)
    }
    fun control(action: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        runCatching { controlProxy?.postMessage(JSONObject().put("type", "control").put("action", action).toString()) }
    }
    fun release() { active = false; popupRequested = false; selectedFrame = null }
    fun invalidate() { release(); proxies.clear(); controlProxy = null; playbackFrame = null; playback = VideoPlayback() }
    fun destroy() { destroyed = true; invalidate(); if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) WebViewCompat.removeWebMessageListener(view, "nagiVideo") }
}
