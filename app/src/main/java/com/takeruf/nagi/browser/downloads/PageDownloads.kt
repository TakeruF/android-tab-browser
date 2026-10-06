package com.takeruf.nagi.browser.downloads

import android.net.Uri
import android.util.Base64
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.BrowserHost
import com.takeruf.nagi.browser.engine.GeneratedDownload
import com.takeruf.nagi.ui.localization.NagiStrings
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import kotlin.coroutines.resume

/** A bounded, main-frame-only channel for page-generated files. No vault or app data is exposed. */
class PageDownloads(private val view: WebView, private val host: BrowserHost) {
    companion object { const val MAX_BYTES = 32L * 1024 * 1024 }
    private val strings = NagiStrings(view.context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val script = view.context.assets.open("browser/page-downloads.js").bufferedReader().use { it.readText() }
    private var generation = 0
    private var pendingId: String? = null
    private var transfer: Job? = null
    val isBusy get() = pendingId != null
    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(view, "nagiDownload", setOf("*")) { _, message, origin, mainFrame, _ ->
                if (mainFrame && validOrigin(origin, view.url.orEmpty().toUri())) {
                    if (message.data.orEmpty().length > 4096) return@addWebMessageListener
                    runCatching { receive(JSONObject(message.data.orEmpty()), origin) }
                        .onFailure { host.showMessage(strings(R.string.ui_generated_download_failed)) }
                }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, script, setOf("*"))
            }
        }
    }

    fun installFallback() { if (supported) view.evaluateJavascript(script, null) }
    fun request(url: String, name: String) {
        if (!supported || url.length > MAX_BYTES * 2) {
            host.showMessage(strings(R.string.ui_generated_download_failed)); return
        }
        view.evaluateJavascript("window.__nagiPageDownloads?.capture(${JSONObject.quote(url)},${JSONObject.quote(name)})", null)
    }

    private fun receive(message: JSONObject, origin: Uri) {
        if (message.optBoolean("error")) { host.showMessage(strings(R.string.ui_generated_download_failed)); return }
        val id = message.getString("id")
        if (!Regex("[a-zA-Z0-9-]{1,100}").matches(id)) return
        val size = message.getLong("size")
        if (size !in 0..MAX_BYTES || pendingId != null) { release(id); return }
        val mime = message.optString("mime").takeIf { Regex("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+").matches(it) }
            ?: "application/octet-stream"
        val request = GeneratedDownload(safeDownloadName(message.optString("name")), mime, size, origin.toString())
        val offeredGeneration = generation
        pendingId = id
        host.confirmGeneratedDownload(request) { accepted ->
            if (generation != offeredGeneration || pendingId != id) return@confirmGeneratedDownload
            if (!accepted) { release(id); pendingId = null; return@confirmGeneratedDownload }
            transfer = scope.launch {
                var file: File? = null
                try {
                    file = withContext(NonCancellable + Dispatchers.IO) { File.createTempFile("page-download-", ".tmp", view.context.cacheDir) }
                    val output = withContext(Dispatchers.IO) { file.outputStream() }
                    try {
                        var offset = 0L
                        while (offset < size) {
                            // Request the next chunk only after the preceding native IO finishes.
                            val encoded = readChunk(id, offset)
                            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                            require(bytes.isNotEmpty() && bytes.size <= 49152 && offset + bytes.size <= size)
                            withContext(Dispatchers.IO) { output.write(bytes) }
                            offset += bytes.size
                        }
                    } finally { withContext(NonCancellable + Dispatchers.IO) { output.close() } }
                    if (generation == offeredGeneration && pendingId == id) {
                        host.saveGeneratedDownload(request, file)
                        file = null // Host owns cleanup after copying to Downloads.
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    host.showMessage(strings(R.string.ui_generated_download_failed))
                } finally {
                    file?.delete()
                    if (generation == offeredGeneration) { release(id); pendingId = null }
                }
            }
        }
    }

    private suspend fun readChunk(id: String, offset: Long): String = withTimeout(10_000) {
        // evaluateJavascript doesn't await promises. Poll a bounded page-owned value.
        view.evaluateJavascript("""
            window.__nagiChunk=null;
            window.__nagiPageDownloads.read(${JSONObject.quote(id)},$offset).then(v=>window.__nagiChunk=v).catch(()=>window.__nagiChunk='');
        """.trimIndent(), null)
        var encoded: String? = null
        while (encoded == null) {
            val value = evaluate("typeof window.__nagiChunk==='string' ? (window.__nagiChunk.length<=65536 ? window.__nagiChunk : '') : null")
            if (value != null && value != "null") encoded = JSONTokener(value).nextValue() as? String ?: ""
            else delay(10)
        }
        encoded
    }
    private suspend fun evaluate(script: String): String? = suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(script) { if (continuation.isActive) continuation.resume(it) }
    }
    private fun release(id: String) { view.evaluateJavascript("window.__nagiPageDownloads?.release(${JSONObject.quote(id)});window.__nagiChunk=null", null) }
    fun invalidate() {
        pendingId?.let(::release)
        generation++; pendingId = null; transfer?.cancel(); transfer = null
    }
    fun destroy() { invalidate(); scope.cancel() }
}

internal fun validOrigin(source: Uri, page: Uri): Boolean {
    fun port(uri: Uri) = if (uri.port != -1) uri.port else if (uri.scheme == "https") 443 else 80
    return source.scheme in setOf("http", "https") && source.scheme == page.scheme &&
        source.host != null && source.host == page.host && port(source) == port(page)
}
