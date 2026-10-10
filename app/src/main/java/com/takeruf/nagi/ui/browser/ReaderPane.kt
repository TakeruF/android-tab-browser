package com.takeruf.nagi.ui.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.BrowserEngine
import com.takeruf.nagi.browser.engine.WebViewBrowserEngine
import com.takeruf.nagi.browser.reader.ReaderArticle
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import org.jsoup.nodes.Entities

/** Sanitized article in a separate, script-disabled surface. Original DOM stays live. */
@Composable
fun ReaderPane(article: ReaderArticle, engine: BrowserEngine) {
    val strings = rememberNagiStrings()
    var fontSize by remember(article.sourceUrl) { mutableIntStateOf(20) }
    val background = MaterialTheme.colorScheme.surface.toArgb()
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    val adapter = engine as? WebViewBrowserEngine
    val html = remember(article, fontSize, background, foreground, link) {
        fun color(argb: Int) = "#%06x".format(argb and 0xffffff)
        """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src http: https:; style-src 'unsafe-inline'">
            <style>html{color-scheme:light dark}body{margin:auto;padding:24px;max-width:760px;
            font: ${fontSize}px/1.75 serif;background:${color(background)};color:${color(foreground)};overflow-wrap:anywhere}
            h1{font-size:1.6em;line-height:1.3}img{max-width:100%;height:auto}a{color:${color(link)}}
            pre{white-space:pre-wrap}table{display:block;overflow:auto}blockquote{margin-inline:16px}</style></head>
            <body><h1>${Entities.escape(article.title)}</h1><p>${Entities.escape(article.byline)}</p>${article.content}</body></html>""".trimIndent()
    }
    Column(Modifier.fillMaxSize().testTag("reader-pane")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(strings(R.string.ui_reader_mode), Modifier.weight(1f))
            TextButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(14) }, enabled = fontSize > 14,
                modifier = Modifier.testTag("reader-font-smaller")) { Text("A−") }
            TextButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(32) }, enabled = fontSize < 32,
                modifier = Modifier.testTag("reader-font-larger")) { Text("A+") }
            TextButton(onClick = engine::toggleReader, modifier = Modifier.testTag("exit-reader")) { Text(strings(R.string.ui_exit_reader)) }
        }
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
            WebView(context).apply {
                adapter?.privateProfileName?.let { com.takeruf.nagi.browser.privacy.PrivateProfiles.attach(this, it) }
                adapter?.attachReaderSurface(this)
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url.toString()
                        if (request.isForMainFrame && request.url.scheme in setOf("http", "https")) engine.loadUrl(url)
                        return true
                    }
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        return if (adapter?.blockingActive(article.sourceUrl) == true)
                            adapter?.adBlocker?.intercept(request, article.sourceUrl) else null
                    }
                }
            }
        }, onRelease = { adapter?.releaseReaderSurface(it); it.stopLoading(); it.destroy() }, update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.setBackgroundColor(background)
                view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        })
    }
}
