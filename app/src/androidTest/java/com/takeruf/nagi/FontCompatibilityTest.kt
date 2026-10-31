package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Opt-in font diagnostic. CDP reads only synthetic text nodes, never login fields or page bodies. */
class FontCompatibilityTest {
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    @Test fun inspectJapaneseFallback() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in diagnostic only", args.getString("fontProbe") == "true")
        assumeTrue("Isolated profiles required", PrivateProfiles.supported())
        assumeTrue("Document-start marker required", WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val label = args.getString("fontCase") ?: "fixture-desktop"
        require(label in setOf("fixture-desktop", "fixture-mobile", "chatgpt-desktop", "chatgpt-mobile", "chatgpt-no-blocking", "docs-desktop", "controls", "controls-no-blocking", "copy-controls", "api-controls"))
        val plain = args.getString("fontPlain") == "true"
        require(!plain || label in setOf("fixture-desktop", "fixture-mobile")) { "Plain WebView is localhost fixture only" }
        val nativeSansKey = args.getString("fontNativeSans")
        val nativeSansOptions = mapOf("sans" to "sans-serif", "jp-family" to "Noto Sans CJK JP", "jp-postscript" to "NotoSansCJKJP-Regular")
        require(nativeSansKey == null || (plain && nativeSansKey in nativeSansOptions))
        val nativeSans = nativeSansOptions[nativeSansKey]
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "font-compatibility").apply { mkdirs() }
        val done = File(output, "done")
        val nonce = UUID.randomUUID().toString()
        done.delete()
        File(output, "ready.json").delete()
        File(output, "$label-state.json").delete()
        val html = """
            <!doctype html><html lang="ja"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Japanese font fixture</title><body style="font-size:32px">
            ${listOf("sans-serif", "serif", "system-ui", "Arial", "Roboto", "monospace", "'Missing UI', sans-serif").mapIndexed { index, family ->
                "<p id='font-$index' style=\"font-family:$family\">日本語漢字ひらがなカタカナ</p>"
            }.joinToString("\n")}
            </body></html>
        """.trimIndent()
        val server = FixtureServer(pages = mapOf("/font-fixture" to html))
        val controls = if (label.startsWith("controls") || label in setOf("copy-controls", "api-controls")) {
            val font = File("/system/fonts/Roboto-Regular.ttf")
            check(font.isFile) { "Controlled Latin font unavailable on this diagnostic device" }
            val italic = listOf("Roboto-Italic.ttf", "SourceSansPro-Italic.ttf")
                .map { File("/system/fonts/$it") }.firstOrNull { it.isFile }?.readBytes()
            if (label == "copy-controls") check(italic != null) { "Controlled italic font unavailable" }
            FontControlServer(font.readBytes(), italic)
        } else null
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var engine: WebViewBrowserEngine? = null
        var plainView: WebView? = null
        var profile: String? = null
        try {
            scenario.onActivity { activity ->
                WebView.setWebContentsDebuggingEnabled(true)
                profile = PrivateProfiles.create()
                if (plain) {
                    plainView = WebView(activity).apply {
                        PrivateProfiles.attach(this, profile!!)
                        settings.javaScriptEnabled = true
                        nativeSans?.let { settings.sansSerifFontFamily = it }
                        webViewClient = object : WebViewClient() {
                            private fun allowed(url: String) = url == "${server.origin}/font-fixture"
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !allowed(request.url.toString())
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                                if (allowed(request.url.toString())) null else WebResourceResponse("text/plain", "UTF-8", ByteArray(0).inputStream())
                        }
                    }
                } else {
                    engine = WebViewBrowserEngine(activity, host, host, { false }, !label.endsWith("mobile"),
                    siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true), privateProfileName = profile,
                    adBlocker = (activity.application as NagiApplication).container.adBlocker)
                    engine!!.configureContentBlocking(!label.endsWith("no-blocking"), emptySet())
                }
                val view = plainView ?: (engine!!.surface as WebView)
                // Identify only this isolated diagnostic WebView, including after redirects.
                WebViewCompat.addDocumentStartJavaScript(view,
                    "if(window.top===window)Object.defineProperty(window,'__nagiFontProbeNonce',{value:${JSONObject.quote(nonce)}});",
                    setOf("*"))
                activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
                val settings = view.settings
                File(output, "$label-settings.json").writeText(JSONObject()
                    .put("standard", settings.standardFontFamily).put("sansSerif", settings.sansSerifFontFamily)
                    .put("serif", settings.serifFontFamily).put("fixed", settings.fixedFontFamily)
                    .put("plainWebView", plain).put("desktop", engine?.state?.value?.desktopMode)
                    .put("nativeSansControl", nativeSans)
                    .put("uaPolicy", if (plain) "webview-default" else "nagi-mode").toString(2))
                val url = when {
                    label.startsWith("chatgpt") -> "https://chatgpt.com/"
                    label.startsWith("docs") -> "https://docs.google.com/document/u/0/"
                    label == "copy-controls" -> "${controls!!.origin}/font-copy-controls"
                    label == "api-controls" -> "${controls!!.origin}/font-api-controls"
                    controls != null -> "${controls.origin}/font-controls"
                    else -> "${server.origin}/font-fixture"
                }
                if (plain) plainView!!.loadUrl(url) else engine!!.loadUrl(url)
                File(output, "ready.json").writeText(JSONObject().put("case", label).put("nonce", nonce).toString())
            }
            withTimeout(120_000) { while (!done.exists()) delay(200) }
            instrumentation.runOnMainSync {
                File(output, "$label-state.json").writeText(JSONObject()
                    .put("blockedRequests", engine?.state?.value?.blockedRequests ?: 0).put("plainWebView", plain)
                    .put("serverRequests", controls?.counts()).toString(2))
            }
        } finally {
            instrumentation.runOnMainSync { engine?.destroy(); plainView?.destroy() }
            profile?.let { name ->
                val cleared = CompletableDeferred<Unit>()
                instrumentation.runOnMainSync { PrivateProfiles.clear(name) { cleared.complete(Unit) } }
                withTimeout(15_000) { cleared.await() }
            }
            scenario.close()
            server.close()
            controls?.close()
        }
    }
}
