package com.takeruf.nagi.browser.engine

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Ephemeral notice scope (document or SPA route). Contains no URLs, fields, cookies or tokens. */
data class HumanVerificationState(val documentId: Long = 0, val turnstileFailed: Boolean = false)

/** Read-only Turnstile failure observation; recovery always requires an explicit UI action. */
internal class PageHumanVerification(
    private val view: WebView,
    private val scope: CoroutineScope,
    private val onState: (HumanVerificationState) -> Unit,
) {
    private val script = view.context.assets.open("browser/page-human-verification.js")
        .bufferedReader().use { it.readText() }
    private val documentScript = if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*")) else null
    private var document = 0L
    private var loaded = false
    private var desktop = false
    private var visible = true
    private var destroyed = false
    private var polling: Job? = null
    private var failed = false

    fun reset() {
        document++
        loaded = false
        polling?.cancel(); polling = null
        publish(false)
    }

    fun documentFinished() {
        if (destroyed) return
        // Older providers can observe only notifications received after this fallback.
        if (documentScript == null) view.evaluateJavascript(script, null)
        loaded = true
        refresh()
    }

    fun locationChanged() {
        val wasLoaded = loaded
        reset()
        // pushState/replaceState keep the observer alive. Clear its old route's failure
        // before restarting observation so it cannot reappear on the destination.
        view.evaluateJavascript("typeof window.__nagiHumanVerification === 'function' && window.__nagiHumanVerification(true)", null)
        loaded = wasLoaded
        refresh()
    }

    fun setDesktopMode(value: Boolean) {
        desktop = value
        if (!value) publish(false)
        refresh()
    }

    fun setVisible(value: Boolean) { visible = value; refresh() }

    private fun publish(value: Boolean) {
        failed = value
        onState(HumanVerificationState(document, failed))
    }

    private fun refresh() {
        polling?.cancel(); polling = null
        if (destroyed || !loaded || !desktop || !visible) return
        val generation = document
        polling = scope.launch {
            while (isActive && generation == document) {
                val result = suspendCancellableCoroutine { continuation ->
                    view.evaluateJavascript("typeof window.__nagiHumanVerification === 'function' && window.__nagiHumanVerification() === true") {
                        if (continuation.isActive) continuation.resume(it == "true")
                    }
                }
                if (generation == document && failed != result) publish(result)
                delay(1000)
            }
        }
    }

    fun destroy() {
        destroyed = true
        polling?.cancel(); polling = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) documentScript?.remove()
    }
}
