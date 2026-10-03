package com.orbit.browser.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.File
import com.orbit.browser.browser.downloads.DownloadFilename

private class WebViewSnapshot(val bundle: Bundle) : EngineSnapshot

@SuppressLint("SetJavaScriptEnabled")
class WebViewBrowserEngine(
    context: Context, private val host: BrowserHost, private val fullscreenHost: FullscreenHost,
    private val openLinksInNewTab: () -> Boolean, desktopDefault: Boolean,
) : BrowserEngine, AndroidEngineSurface {
    private val webView = WebView(context)
    override val surface get() = webView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PageState(desktopMode = desktopDefault))
    override val state = mutableState.asStateFlow()
    private val eventChannel = Channel<EngineEvent>(Channel.UNLIMITED)
    override val events = eventChannel.receiveAsFlow()
    private val mobileUa = WebSettings.getDefaultUserAgent(context)
    private val faviconDirectory = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val pendingPermissions = mutableSetOf<PermissionRequest>()
    private val popupViews = mutableSetOf<WebView>()
    private var destroyed = false
    private var failedNavigation = false
    private var documentPage = mutableState.value
    private var downloadUrl: String? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true // Includes localStorage and IndexedDB in the WebView engine.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = true // System picker content URIs for file uploads.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        applyUserAgent(desktopDefault)
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                val scheme = request.url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    if (request.hasGesture() && openLinksInNewTab()) {
                        eventChannel.trySend(EngineEvent.OpenTab(url)); return true
                    }
                    return false
                }
                if (scheme == "about") return url != "about:blank"
                if (request.hasGesture() && scheme in setOf("mailto", "tel", "sms", "geo")) host.openExternal(url)
                else host.showMessage("This address type is not supported")
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                failedNavigation = false
                update { it.copy(url = url, isLoading = true, progress = 0, error = null) }
                metadata()
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url == downloadUrl) return
                update { it.copy(url = url, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
                metadata()
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (url == downloadUrl) return
                update { it.copy(url = url, title = if (url == "about:blank") "New tab" else view.title?.takeIf { t -> t.isNotBlank() } ?: url,
                    isLoading = false, progress = 100, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
                documentPage = state.value
                CookieManager.getInstance().flush(); metadata()
                if (!failedNavigation) eventChannel.trySend(EngineEvent.Visited(state.value))
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    failedNavigation = true
                    update { it.copy(isLoading = false, error = error.description.toString()) }
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                // Keep server-rendered error pages visible instead of covering them with an app error.
                if (request.isForMainFrame) failedNavigation = errorResponse.statusCode >= 400
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Never bypass certificate validation.
                failedNavigation = true
                update { it.copy(isLoading = false, error = "The site's certificate could not be verified.") }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { update { it.copy(progress = newProgress) } }
            override fun onReceivedTitle(view: WebView, title: String?) {
                update { it.copy(title = if (it.url == "about:blank") "New tab" else title?.takeIf(String::isNotBlank) ?: it.url) }; metadata()
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                if (icon == null) return
                val pageUrl = state.value.url
                scope.launch {
                    val path = withContext(Dispatchers.IO) {
                        runCatching {
                            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(pageUrl.toByteArray())
                                .joinToString("") { "%02x".format(it) }
                            val file = File(faviconDirectory, "$hash.png")
                            file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            file.absolutePath
                        }.getOrNull()
                    }
                    if (state.value.url == pageUrl && path != null) {
                        update { it.copy(faviconUrl = path) }; metadata()
                    }
                }
            }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null); fileCallback = callback
                host.chooseFiles(FileSelectionRequest(params.acceptTypes.filter(String::isNotBlank),
                    params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)) { paths ->
                    if (fileCallback === callback) {
                        callback.onReceiveValue(paths?.map(Uri::parse)?.toTypedArray()); fileCallback = null
                    }
                }
                return true
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture || popupViews.size >= 2) return false
                val popup = WebView(context)
                popupViews.add(popup)
                popup.postDelayed({ if (popupViews.remove(popup)) {
                    popup.destroy(); host.showMessage("Blank script-generated popups are not supported in this MVP")
                } }, 5_000)
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        if (request.isForMainFrame) dispatchPopup(request.url.toString(), v)
                        return true
                    }
                    override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                        if (url != "about:blank") dispatchPopup(url, v)
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = popup
                resultMsg.sendToTarget()
                return true
            }
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                fullscreenHost.showFullscreen(view) { callback.onCustomViewHidden() }
            }
            override fun onHideCustomView() { fullscreenHost.hideFullscreen() }
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.scheme != "https") { request.deny(); return }
                val supported = request.resources.mapNotNull { resource -> when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.MICROPHONE
                    else -> null
                } }.toSet()
                if (supported.isEmpty()) { request.deny(); return }
                pendingPermissions.add(request)
                host.requestPermission(request.origin.toString(), supported) { granted ->
                    if (!destroyed && pendingPermissions.remove(request)) {
                        val resources = request.resources.filter { resource -> when (resource) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.CAMERA in granted
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.MICROPHONE in granted
                            else -> false
                        } }.toTypedArray()
                        if (resources.isEmpty()) request.deny() else request.grant(resources)
                    }
                }
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) { pendingPermissions.remove(request) }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (Uri.parse(origin).scheme != "https") { callback.invoke(origin, false, false); return }
                host.requestPermission(origin, setOf(SitePermission.LOCATION)) { granted ->
                    callback.invoke(origin, !destroyed && SitePermission.LOCATION in granted, false)
                }
            }
        }
        webView.setDownloadListener { url, userAgent, disposition, mimeType, _ ->
            // An attachment is not a document navigation. Keep the source page restorable.
            downloadUrl = url
            update { documentPage.copy(isLoading = false, progress = 100, desktopMode = it.desktopMode) }
            metadata()
            if (url.startsWith("https://") || url.startsWith("http://")) host.download(
                DownloadRequest(url, userAgent, disposition, mimeType,
                    CookieManager.getInstance().getCookie(url), DownloadFilename.fromDisposition(disposition)
                        ?: URLUtil.guessFileName(url, disposition, mimeType)))
            else host.showMessage("Blob and data downloads are not supported in this MVP")
        }
        webView.setFindListener { active, count, _ -> update { it.copy(findMatches = count, activeFindMatch = active) } }
    }
    private fun dispatchPopup(url: String, popup: WebView) {
        if (!popupViews.remove(popup)) return
        if (url.startsWith("http://") || url.startsWith("https://")) eventChannel.trySend(EngineEvent.OpenTab(url))
        else host.showMessage("This popup address is not supported")
        popup.post { popup.stopLoading(); popup.destroy() }
    }
    private fun update(change: (PageState) -> PageState) { if (!destroyed) mutableState.update(change) }
    private fun metadata() { eventChannel.trySend(EngineEvent.Metadata(state.value)) }
    override fun loadUrl(url: String) {
        if (destroyed) return
        if (url != "about:blank" && !url.startsWith("http://") && !url.startsWith("https://")) return
        downloadUrl = null
        update { it.copy(url = url, isLoading = url != "about:blank", progress = 0,
            error = null, faviconUrl = null) }; webView.loadUrl(url)
    }
    override fun reload() { if (!destroyed) { update { it.copy(isLoading = true, progress = 0, error = null) }; webView.reload() } }
    override fun goBack() { if (canGoBack()) { update { it.copy(isLoading = true) }; webView.goBack() } }
    override fun goForward() { if (canGoForward()) { update { it.copy(isLoading = true) }; webView.goForward() } }
    override fun canGoBack() = !destroyed && webView.canGoBack()
    override fun canGoForward() = !destroyed && webView.canGoForward()
    override fun evaluateJavascript(script: String) { if (!destroyed) webView.evaluateJavascript(script, null) }
    private fun applyUserAgent(desktop: Boolean) {
        val version = Regex("Chrome/([0-9.]+)").find(mobileUa)?.groupValues?.get(1) ?: "130.0.0.0"
        webView.settings.userAgentString = if (desktop)
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36" else mobileUa
    }
    override fun setDesktopMode(enabled: Boolean) {
        if (state.value.desktopMode == enabled) return
        applyUserAgent(enabled); update { it.copy(desktopMode = enabled) }; reload()
    }
    override fun findInPage(query: String) { webView.findAllAsync(query) }
    override fun findNext(forward: Boolean) { webView.findNext(forward) }
    override fun clearFind() { webView.clearMatches(); update { it.copy(findMatches = 0, activeFindMatch = 0) } }
    override fun setVisible(visible: Boolean) { if (visible) webView.onResume() else webView.onPause() }
    override fun saveState(): EngineSnapshot? {
        if (destroyed) return null
        val bundle = Bundle()
        return if (webView.saveState(bundle) != null) WebViewSnapshot(bundle) else null
    }
    override fun restoreState(snapshot: EngineSnapshot): Boolean {
        if (snapshot !is WebViewSnapshot) return false
        val restored = runCatching { webView.restoreState(snapshot.bundle) != null }.getOrDefault(false)
        if (restored) {
            update { it.copy(url = webView.url ?: "about:blank", title = webView.title ?: "New tab",
                canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
            documentPage = state.value
        }
        return restored
    }
    override fun destroy() {
        if (destroyed) return
        destroyed = true
        fileCallback?.onReceiveValue(null); fileCallback = null
        pendingPermissions.forEach { it.deny() }; pendingPermissions.clear()
        popupViews.forEach { it.destroy() }; popupViews.clear()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading(); webView.onPause(); webView.destroy()
        scope.cancel(); eventChannel.close()
    }
}
