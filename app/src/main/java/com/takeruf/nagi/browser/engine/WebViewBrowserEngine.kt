package com.takeruf.nagi.browser.engine

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.NagiStrings

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.webkit.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.UserAgentMetadata
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import com.takeruf.nagi.browser.favicon.FaviconStore
import com.takeruf.nagi.browser.favicon.faviconOrigin
import org.json.JSONArray
import org.json.JSONObject
import com.takeruf.nagi.browser.downloads.DownloadFilename

private class WebViewSnapshot(val bundle: Bundle, val scrollX: Int, val scrollY: Int, val url: String,
    val desktopMode: Boolean, val zoomBaseScale: Float = 0f,
    val zoomPercent: Int = 100) : EngineSnapshot

@SuppressLint("SetJavaScriptEnabled")
class WebViewBrowserEngine(
    private val context: Context, private val host: BrowserHost, private val fullscreenHost: FullscreenHost,
    private val openLinksInNewTab: () -> Boolean, desktopDefault: Boolean,
    private val nativePageDrag: () -> Boolean = { false },
    private val siteDisplayModes: SiteDisplayModeStore = SiteDisplayModeStore(context),
    private val defaultDesktopMode: () -> Boolean = { desktopDefault },
    private val tabId: String = java.util.UUID.randomUUID().toString(),
    internal val adBlocker: com.takeruf.nagi.browser.blocking.AdBlocker? = null,
    val privateProfileName: String? = null,
    private val translationFactory: () -> com.takeruf.nagi.browser.translation.TextTranslationBackend = {
        com.takeruf.nagi.browser.translation.MlKitTextTranslationBackend()
    },
    private val alwaysTranslateRules: () -> Map<String, String> = { emptyMap() },
    private val rememberAlwaysTranslate: (String, String) -> Unit = { _, _ -> },
    private val passwordAutofillApproved: Boolean = PasswordAutofillAvailability.enabled,
) : BrowserEngine, AndroidEngineSurface {
    private val strings = NagiStrings(context)
    private val webTheme = android.view.ContextThemeWrapper(context, R.style.Theme_Nagi_Web_Light)
    private var zoomInteractionVersion = 0L
    private var nativeDragHost: String? = null
    private var nativeDragActive = false
    private val secondaryClick = NativeSecondaryClick(context)
    private val webView: WebView = object : WebView(webTheme) {
        private var floatingSelectionMode: android.view.ActionMode? = null
        // Some touchpads send their mouse cursor/drag through generic motion.
        // Keep pixel/gesture scrolling on the original touchpad source.
        private fun mousePointer(event: android.view.MotionEvent): android.view.MotionEvent? =
            if (event.isFromSource(android.view.InputDevice.SOURCE_TOUCHPAD) &&
                event.actionMasked != android.view.MotionEvent.ACTION_SCROLL && event.pointerCount == 1 &&
                event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_MOUSE)
                android.view.MotionEvent.obtain(event).apply { source = android.view.InputDevice.SOURCE_MOUSE }
            else null
        override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
            mousePointer(event)?.let { mouse ->
                try { return dispatchTouchEvent(mouse) } finally { mouse.recycle() }
            }
            if (secondaryClick.onMotion(event) { super.dispatchTouchEvent(it) }) return true
            val selectionRelease = pointerSelection.onMotion(event)
            // A scaled software draw can change native selection-handle geometry.
            // Keep the existing snapshot while WebView owns a selection toolbar.
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && floatingSelectionMode == null && !selectionRelease) historyPreviews.interaction()
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                nativeDragHost = if (nativePageDrag()) Uri.parse(state.value.url).host else null
                zoomInteractionVersion++; cancelZoomWork(); cancelSnapshotPresentationRestore()
            }
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && !nativeDragActive) nativeDragHost = null
            return super.dispatchTouchEvent(event)
        }
        override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
            mousePointer(event)?.let { mouse ->
                try {
                    return when (mouse.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE,
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> dispatchTouchEvent(mouse)
                        else -> dispatchGenericMotionEvent(mouse)
                    }
                } finally { mouse.recycle() }
            }
            if (secondaryClick.onMotion(event) { super.dispatchTouchEvent(it) }) return true
            pointerSelection.onMotion(event)
            return super.dispatchGenericMotionEvent(event)
        }
        override fun onDetachedFromWindow() {
            pointerSelection.invalidate()
            secondaryClick.reset()
            super.onDetachedFromWindow()
        }
        override fun startActionMode(callback: android.view.ActionMode.Callback, type: Int): android.view.ActionMode? {
            if (type != android.view.ActionMode.TYPE_FLOATING) return super.startActionMode(callback, type)
            val observed = object : android.view.ActionMode.Callback2() {
                override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = callback.onCreateActionMode(mode, menu)
                override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = callback.onPrepareActionMode(mode, menu)
                override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem) = callback.onActionItemClicked(mode, item)
                override fun onDestroyActionMode(mode: android.view.ActionMode) {
                    if (floatingSelectionMode === mode) {
                        floatingSelectionMode = null
                        secondaryClick.selectionFinished()
                    }
                    callback.onDestroyActionMode(mode)
                }
                override fun onGetContentRect(mode: android.view.ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
                    if (callback is android.view.ActionMode.Callback2) callback.onGetContentRect(mode, view, outRect)
                    else super.onGetContentRect(mode, view, outRect)
                }
            }
            val mode = super.startActionMode(observed, type)
            if (mode != null) {
                floatingSelectionMode = mode
                secondaryClick.selectionStarted()
            }
            return mode
        }
        override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
            if (floatingSelectionMode == null && event.action == android.view.KeyEvent.ACTION_DOWN && when (event.keyCode) {
                    android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER -> true
                    android.view.KeyEvent.KEYCODE_SPACE -> !onCheckIsTextEditor()
                    else -> false
                }) historyPreviews.interaction()
            return super.dispatchKeyEvent(event)
        }
        override fun onDragEvent(event: android.view.DragEvent): Boolean {
            val nativeStart = event.action == android.view.DragEvent.ACTION_DRAG_STARTED && nativeDragHost != null
            if (event.action == android.view.DragEvent.ACTION_DRAG_STARTED) {
                nativeDragHost?.let { host ->
                    nativeDragActive = true
                    updateDragShadow(LinkDragShadow(context, host, dragPreviewColors, fitCanvas = true))
                }
            }
            if (event.action == android.view.DragEvent.ACTION_DRAG_ENDED) {
                nativeDragActive = false
                nativeDragHost = null
            }
            return super.onDragEvent(event) || nativeStart
        }
        override fun onCreateInputConnection(outAttrs: android.view.inputmethod.EditorInfo): android.view.inputmethod.InputConnection? {
            val connection = super.onCreateInputConnection(outAttrs)
            com.takeruf.nagi.browser.privacy.PrivateKeyboard.apply(outAttrs, privateProfileName != null)
            return connection
        }

        override fun onProvideAutofillVirtualStructure(structure: android.view.ViewStructure, flags: Int) {
            if (!supportsPasswordAutofill) return
            PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.STRUCTURE_REQUESTED)
            super.onProvideAutofillVirtualStructure(FocusedAutofillStructure(structure) { id ->
                autofillVirtualId = id
                PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.FOCUSED_FIELD_OBSERVED)
            }, flags)
        }

        override fun autofill(values: android.util.SparseArray<android.view.autofill.AutofillValue>) {
            PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.FILL_DELIVERED, values.size())
            super.autofill(values)
        }
    }
    private var darkTheme = false
    private var pageDarkeningEnabled = false
    private var darkModeExcludedHosts = emptySet<String>()
    override val supportsPageDarkening = PageDarkening.supported
    private var dragPreviewColors: LinkDragPreviewColors? = null
    private val cookies = if (privateProfileName == null) CookieManager.getInstance()
        else PrivateProfiles.attach(webView, privateProfileName)
    private val pageVideo = PageVideo(webView, fullscreenHost, tabId)
    private val pageDownloads = com.takeruf.nagi.browser.downloads.PageDownloads(webView, host)
    private var mediaPermissionGranted = false
    private var fullscreenActive = false
    private var restoringScroll: Pair<Int, Int>? = null
    override val surface get() = webView
    private val historyPreviews = WebViewHistoryPreviews(webView)
    private val pointerSelection = PagePointerSelection(webView,
        show = { x, y -> secondaryClick.showSelectionMenu(x, y, webView::dispatchTouchEvent, selectionOnly = true) },
        noSelection = { historyPreviews.interaction() })
    private val visualGeneration = MutableStateFlow(0L)
    override val historyVisualGeneration = visualGeneration.asStateFlow()
    override fun historyPagePreview(back: Boolean): HistoryPagePreview? {
        if (destroyed || state.value.readerArticle != null) return null
        // Native history may contain script-created entries which WebView disallows traversing.
        if (if (back) !webView.canGoBack() else !webView.canGoForward()) return null
        return historyPreviews.preview(back)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(PageState(desktopMode = desktopDefault))
    override val state = mutableState.asStateFlow()
    private val translationNavigation = com.takeruf.nagi.browser.translation.TranslationNavigationState()
    private var translationWifiOnly = true
    private var automaticTranslationGeneration = -1L
    private val pageTranslation = com.takeruf.nagi.browser.translation.PageTranslation(webView, scope, translationFactory) { translation ->
        if (translation.translatedNodes > 0 && translation.source != null && translation.target != null) {
            translationNavigation.didTranslate(translation.source, translation.target, translationWifiOnly)
        }
        update { it.copy(translation = translation) }
    }
    private val eventChannel = Channel<EngineEvent>(Channel.UNLIMITED)
    override val events = eventChannel.receiveAsFlow()
    private val mobileUa = SiteUserAgent.mobile(WebSettings.getDefaultUserAgent(context))
    private val defaultUaMetadata = if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
        WebSettingsCompat.getUserAgentMetadata(webView.settings) else null
    private val favicons = if (privateProfileName == null) FaviconStore.get(context) else null
    private val readerScript by lazy { context.assets.open("browser/Readability.js").bufferedReader().use { it.readText() } }
    private val adSlotScript by lazy { context.assets.open("browser/AdSlotCollapse.js").bufferedReader().use { it.readText() } }
    private var cosmeticRefreshJob: Job? = null
    @Volatile private var adBlockingEnabled = true
    @Volatile private var excludedHosts = emptySet<String>()
    @Volatile private var visitExceptionSites = emptySet<String>()
    private data class BlockingDocument(val url: String, val count: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(),
        val blockedUrls: MutableSet<String> = linkedSetOf())
    @Volatile private var blockingDocument = BlockingDocument("about:blank")
    private var faviconJob: Job? = null
    private var navigationGeneration = 0L
    private var navigationRequestGeneration = 0L
    // Only explicit URL loads are known GETs. Native form submissions keep WebView's
    // reload/resubmission path, including while an older document remains in history.
    private data class PendingExplicitLoad(val url: String, val extraHeaders: Map<String, String>, val currentUrl: String = url)
    private var pendingExplicitLoad: PendingExplicitLoad? = null
    private var iconGeneration = 0L
    private var receivedDocumentIcon = false
    private var faviconDocumentTheme = false
    private var observedIconTheme = false
    private var observedIcon: Bitmap? = null
    private val pendingPermissions = mutableSetOf<PermissionRequest>()
    private val pendingPermissionResults = mutableMapOf<PermissionRequest, (Set<SitePermission>) -> Unit>()
    private var geolocationPermissionResult: ((Set<SitePermission>) -> Unit)? = null
    private val pendingPopups = mutableSetOf<PendingBrowserPopup>()
    private val popupChildren = mutableSetOf<WebViewBrowserEngine>()
    private var popupOpener: WebViewBrowserEngine? = null
    private var isPopupWindow = false
    private var destroyed = false
    private var autofillRequestJob: Job? = null
    private val autofillManager = context.getSystemService(android.view.autofill.AutofillManager::class.java)
    // Keep WebView's opaque native field ID, including when the provider
    // declines the automatic request. It is invalidated on navigation.
    private var autofillVirtualId: Int? = null
    private var manualAutofillPending = false
    private var autofillTouchAnchor: android.graphics.PointF? = null
    private val humanVerification = PageHumanVerification(webView, scope) { status ->
        update { it.copy(humanVerification = status) }
    }
    private var failedNavigation = false
    private var documentPage = mutableState.value
    private var downloadUrl: String? = null
    private var hasCommittedDocument = false
    private var pendingDownloads = 0
    // Density-independent native page scales. Device DPI is not a user zoom.
    private var zoomBaseScale = 0f
    private var zoomBaselineGeneration = -1L
    private var pageScale = 0f
    private var restoredZoomBaseScale: Float? = null
    private var restoredZoomPercent: Int? = null
    private var restoredZoomUrl: String? = null
    private var zoomRestoreJob: Job? = null
    private var zoomRestoreEpoch = 0L
    private enum class ZoomCommand { IN, OUT, RESET }
    private val zoomCommands = ArrayDeque<ZoomCommand>()
    private var zoomJob: Job? = null
    private var zoomScaleVersion = 0L
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var loadingTimeout: Job? = null
    private var readerSurface: WebView? = null
    internal fun attachReaderSurface(view: WebView) {
        readerSurface = view
        view.setFindListener { active, count, _ -> update { it.copy(findMatches = count, activeFindMatch = active) } }
    }
    internal fun releaseReaderSurface(view: WebView) {
        if (readerSurface === view) readerSurface = null
    }

    init {
        // Let WebView expose its virtual HTML fields and website origin to the
        // device's AutofillService. Hints belong to those fields, not this container.
        webView.id = R.id.browser_web_view
        webView.importantForAutofill = if (supportsPasswordAutofill) android.view.View.IMPORTANT_FOR_AUTOFILL_YES
            else android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        if (supportsPasswordAutofill) autofillManager?.let { manager ->
            PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.ENGINE_ENABLED)
            WebViewAutofillCallbacks.register(manager, webView) { id, event ->
                autofillVirtualId = id
                if (manualAutofillPending && event == android.view.autofill.AutofillManager.AutofillCallback.EVENT_INPUT_UNAVAILABLE)
                    host.showMessage(strings(R.string.ui_autofill_no_suggestions))
                manualAutofillPending = false
            }
            webView.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN)
                    autofillTouchAnchor = android.graphics.PointF(event.x, event.y)
                false
            }
        }
        if (privateProfileName != null) webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
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
        // WebView defaults to disabling WebAuthn. Browser mode preserves website origins,
        // RP-ID validation and the system credential UI; never inject a JavaScript credential bridge.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(webView.settings,
                WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
        }
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, false)
        applyUserAgent(if (desktopDefault) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE)
        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.setOnLongClickListener {
            if (secondaryClick.selectionOnly) false
            else if (secondaryClick.longPressActive || !nativePageDrag()) showPageContextMenu()
            else if (startLinkDrag()) true
            else {
                nativeDragHost = Uri.parse(webView.hitTestResult?.extra.orEmpty()).host
                    ?: Uri.parse(state.value.url).host
                val interaction = zoomInteractionVersion
                val generation = navigationGeneration
                val handler = android.os.Handler(android.os.Looper.getMainLooper()) { message ->
                    val host = Uri.parse(message.data.getString("url").orEmpty()).host
                    if (!destroyed && generation == navigationGeneration && interaction == zoomInteractionVersion && nativeDragHost != null && host != null) {
                        nativeDragHost = host
                        if (nativeDragActive) webView.updateDragShadow(LinkDragShadow(webView.context, host, dragPreviewColors, fitCanvas = true))
                    }
                    true
                }
                webView.requestFocusNodeHref(handler.obtainMessage())
                false
            }
        }
        webView.setOnContextClickListener { showPageContextMenu() }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val source = blockingDocument
                if (!blockingActive(source.url)) return null
                val response = adBlocker?.intercept(request, source.url) ?: return null
                val count = source.count.incrementAndGet()
                // Do not expose resource URLs from cross-origin child documents to
                // the top page. Missing referrers rely on cosmetic evidence instead.
                val referer = request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value
                if (referer != null && sameOrigin(referer, source.url)) synchronized(source.blockedUrls) {
                    if (source.blockedUrls.size < 256) source.blockedUrls.add(request.url.toString())
                }
                scope.launch { if (blockingDocument === source) {
                    update { it.copy(blockedRequests = count) }
                    if (cosmeticRefreshJob?.isActive != true) cosmeticRefreshJob = scope.launch {
                        delay(100)
                        if (blockingDocument === source && !destroyed) installCosmeticFiltering()
                    }
                } }
                return response
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val requestGeneration = ++navigationRequestGeneration
                val url = request.url.toString()
                val scheme = request.url.scheme?.lowercase()
                if (request.hasGesture() && host.openExternalLink(url, ::loadUrl)) return true
                if (scheme == "http" || scheme == "https") {
                    if (request.hasGesture() && openLinksInNewTab() && !(isPopupWindow && !hasCommittedDocument)) {
                        eventChannel.trySend(EngineEvent.OpenTab(url)); return true
                    }
                    pendingExplicitLoad?.let { pending ->
                        if (request.isRedirect && request.method.equals("GET", true)) pendingExplicitLoad = pending.copy(currentUrl = url)
                        else if (request.hasGesture() || !request.method.equals("GET", true)) pendingExplicitLoad = null
                    }
                    if (!isSameDocumentFragment(url) && !request.isRedirect) {
                        if (request.hasGesture()) translationNavigation.followLink()
                        else if (!state.value.isLoading) translationNavigation.reset()
                    }
                    val mode = displayModeFor(url)
                    if (request.method.equals("GET", true) && !isSameDocumentFragment(url) &&
                        mode != null && mode != currentDisplayMode()) {
                        // The UA setter can reload the old visible URL while WebView is
                        // processing a link or redirect. Cancel this request first, then
                        // start the target with its chosen UA after the callback returns.
                        val referer = request.requestHeaders?.entries?.firstOrNull {
                            it.key.equals("Referer", true)
                        }?.value?.takeIf(String::isNotBlank)
                        val headers = referer?.let { mapOf("Referer" to it) }.orEmpty()
                        view.post {
                            if (!destroyed && requestGeneration == navigationRequestGeneration) loadUrl(url, headers, preserveTranslation = true)
                        }
                        return true
                    }
                    historyPreviews.leaving()
                    applyPageDarkening(view, url)
                    // POST navigations keep their method/body and the current document UA.
                    return false
                }
                if (scheme == "about") return url != "about:blank"
                host.showMessage(strings(R.string.ui_this_address_type_is_not_supported))
                return true
            }
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                if (url == "about:blank" && state.value.url != "about:blank") return
                secondaryClick.reset()
                pointerSelection.invalidate()
                historyPreviews.started()
                if (pendingExplicitLoad != null && pendingExplicitLoad?.currentUrl != url) pendingExplicitLoad = null
                applyPageDarkening(view, url)
                cancelZoomWork()
                if (url != "about:blank" && restoredZoomUrl?.substringBefore('#')?.let { it != url.substringBefore('#') } == true) cancelSnapshotPresentationRestore()
                if (fullscreenActive) fullscreenHost.hideVideo(tabId)
                pageVideo.invalidate()
                // Interception can run before onPageStarted, especially for local/cached
                // resources. Keep that document's counts instead of resetting them here.
                if (blockingDocument.url != url) blockingDocument = BlockingDocument(url)
                navigationGeneration++
                autofillVirtualId = null
                manualAutofillPending = false
                autofillTouchAnchor = null
                humanVerification.reset()
                translationNavigation.documentStarted()
                pageTranslation.reset(restore = true)
                pageDownloads.invalidate()
                mediaPermissionGranted = false
                faviconJob?.cancel()
                receivedDocumentIcon = false
                faviconDocumentTheme = darkTheme
                observedIconTheme = darkTheme
                observedIcon?.recycle()
                observedIcon = null
                failedNavigation = false
                update { it.copy(url = url, isLoading = true, progress = 0, error = null,
                    visitBlockingExceptionSite = visitExceptionFor(url),
                    blockedRequests = blockingDocument.count.get(), readerArticle = null, readerLoading = false,
                    faviconUrl = if (faviconOrigin(it.url) != null && faviconOrigin(it.url) == faviconOrigin(url)) it.faviconUrl else null) }
                // onPageStarted's bitmap can be WebView's cached icon from another
                // visit/theme. Only onReceivedIcon supplies a new themed observation.
                metadata()
                watchLoading()
            }
            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                if (url == downloadUrl) return
                // An initial/cancelled blank page must not replace a requested web page
                // in PageState or Room. Start/finish callbacks enforce the same boundary.
                if (url == "about:blank" && state.value.url != "about:blank") return
                applyPageDarkening(view, url)
                val sameDocument = historyPreviews.historyUpdated()
                if (url != state.value.url) humanVerification.locationChanged()
                update { it.copy(url = url, visitBlockingExceptionSite = visitExceptionFor(url), canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
                if (sameDocument) {
                    loadingTimeout?.cancel()
                    update { it.copy(isLoading = false, progress = 100, title = view.title?.takeIf(String::isNotBlank) ?: url) }
                    documentPage = state.value
                    recordVisit()
                    // pushState/popstate may render after the history callback.
                    val generation = navigationGeneration
                    view.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (!destroyed && generation == navigationGeneration && url == state.value.url && url == view.url)
                                visualGeneration.value++
                        }
                    })
                }
                metadata()
            }
            override fun onPageCommitVisible(view: WebView, url: String) {
                if (url == view.url && url == state.value.url) {
                    historyPreviews.committed()
                    visualGeneration.value++
                }
                if (url != "about:blank" && url != downloadUrl && url == view.url && url == state.value.url) {
                    hasCommittedDocument = true
                    pendingExplicitLoad = null
                }
                if (url == view.url && url == state.value.url) installCosmeticFiltering()
            }
            override fun onPageFinished(view: WebView, url: String) {
                // Redirects/cancelled loads can finish after a newer document has started.
                if (url != view.url || url != state.value.url) return
                if (url == "about:blank" && state.value.url != "about:blank") return
                if (url == downloadUrl) return
                historyPreviews.committed()
                visualGeneration.value++
                pendingExplicitLoad = null
                if (url != "about:blank") hasCommittedDocument = true
                loadingTimeout?.cancel()
                update { it.copy(url = url, title = if (url == "about:blank" && !it.rendersBlankDocument) "New tab" else view.title?.takeIf { t -> t.isNotBlank() } ?: url,
                    isLoading = false, progress = 100, canGoBack = view.canGoBack(), canGoForward = view.canGoForward()) }
                documentPage = state.value
                if (url != "about:blank" && restoredZoomPercent == null) captureZoomBaseline()
                pageDownloads.installFallback()
                pageVideo.installFallback()
                pointerSelection.installFallback()
                humanVerification.documentFinished()
                installCosmeticFiltering()
                if (restoredZoomPercent == null) restoringScroll?.let { (x, y) -> view.post { view.scrollTo(x, y) }; restoringScroll = null }
                if (privateProfileName == null) cookies.flush()
                metadata()
                if (!failedNavigation) discoverFavicon(view, url)
                recordVisit()
                continuePageTranslation(url)
            }
            override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
                if (newScale.isFinite() && newScale > 0f) {
                    pageScale = newScale / zoomDensity()
                    zoomScaleVersion++
                    updateZoomState()
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    loadingTimeout?.cancel()
                    failedNavigation = true
                    val kind = when (error.errorCode) {
                        WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_IO -> PageErrorKind.CONNECTION
                        WebViewClient.ERROR_TIMEOUT -> PageErrorKind.TIMEOUT
                        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> PageErrorKind.CERTIFICATE
                        else -> PageErrorKind.OTHER
                    }
                    update { it.copy(isLoading = false, error = error.description.toString(), errorKind = kind) }
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                // Keep server-rendered error pages visible instead of covering them with an app error.
                if (request.isForMainFrame) failedNavigation = errorResponse.statusCode >= 400
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Never bypass certificate validation.
                // A rejected image/frame certificate must not replace a healthy main page.
                if (!CertificateHostRecovery.matchesMainDocument(error.url, state.value.url)) return
                wwwRetryUrl(error)?.let { retry -> loadUrl(retry); return }
                loadingTimeout?.cancel()
                failedNavigation = true
                update { it.copy(isLoading = false, error = strings(R.string.ui_the_site_s_certificate_could_not_be_verified),
                    errorKind = PageErrorKind.CERTIFICATE) }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { update { it.copy(progress = newProgress) } }
            override fun onReceivedTitle(view: WebView, title: String?) {
                update { it.copy(title = if (it.url == "about:blank" && !it.rendersBlankDocument) "New tab" else title?.takeIf(String::isNotBlank) ?: it.url) }; metadata()
                // SPA routers often set the video/article title after pushState.
                if (!state.value.isLoading && documentPage.url == state.value.url && view.url == state.value.url) {
                    documentPage = state.value
                    recordVisit()
                }
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                val url = view.url ?: return
                if (icon == null || url != state.value.url || destroyed || favicons == null) return
                receivedDocumentIcon = true
                faviconJob?.cancel()
                // Providers can replay the unchanged bitmap during a configuration change.
                // It is still the previous observation, not a newly learned opposite variant.
                val scaled = Bitmap.createScaledBitmap(icon, 64, 64, true)
                val sample = scaled.copy(Bitmap.Config.ARGB_8888, false)
                if (scaled !== icon) scaled.recycle()
                val replay = observedIconTheme != darkTheme &&
                    (observedIcon == null || observedIcon?.sameAs(sample) == true)
                if (!replay) {
                    observedIcon?.recycle()
                    observedIcon = sample
                    observedIconTheme = darkTheme
                }
                if (replay) { sample.recycle(); return }
                saveFavicon(url, icon)
            }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null); fileCallback = callback
                host.chooseFiles(FileSelectionRequest(params.acceptTypes.filter(String::isNotBlank),
                    params.mode == FileChooserParams.MODE_OPEN_MULTIPLE, params.isCaptureEnabled)) { paths ->
                    if (fileCallback === callback) {
                        callback.onReceiveValue(paths?.map(Uri::parse)?.toTypedArray()); fileCallback = null
                    }
                }
                return true
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (destroyed || !isUserGesture || pendingPopups.size + popupChildren.size >= 2) return false
                val popupId = java.util.UUID.randomUUID().toString()
                val child = WebViewBrowserEngine(context, host, fullscreenHost, openLinksInNewTab,
                    state.value.desktopMode, nativePageDrag, siteDisplayModes, defaultDesktopMode,
                    popupId, adBlocker, privateProfileName, translationFactory, alwaysTranslateRules,
                    rememberAlwaysTranslate, passwordAutofillApproved)
                child.isPopupWindow = true
                child.update { it.copy(rendersBlankDocument = true) }
                child.popupOpener = this@WebViewBrowserEngine
                child.webView.settings.userAgentString = view.settings.userAgentString
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                    WebSettingsCompat.setUserAgentMetadata(child.webView.settings,
                        WebSettingsCompat.getUserAgentMetadata(view.settings))
                }
                cookies.setAcceptThirdPartyCookies(child.webView, cookies.acceptThirdPartyCookies(view))
                child.setDarkTheme(darkTheme)
                child.configurePageDarkening(pageDarkeningEnabled, darkModeExcludedHosts)
                child.configureContentBlocking(adBlockingEnabled, excludedHosts)
                child.configureVisitBlockingExceptions(visitExceptionSites)
                val request = object : PendingBrowserPopup {
                    override val tabId = popupId
                    override fun attach(): BrowserEngine? {
                        if (!pendingPopups.remove(this) || destroyed) return null
                        popupChildren.add(child)
                        return child
                    }
                    override fun cancel() {
                        if (pendingPopups.remove(this)) child.destroy()
                    }
                }
                pendingPopups.add(request)
                if (eventChannel.trySend(EngineEvent.OpenPopup(request)).isFailure) {
                    request.cancel()
                    return false
                }
                // Supply the actual child during the native creation callback. Chromium
                // keeps the opener, document writes, POST bodies and window.close().
                (resultMsg.obj as WebView.WebViewTransport).webView = child.webView
                resultMsg.sendToTarget()
                child.webView.post {
                    if (!child.destroyed) child.webView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                        override fun onComplete(requestId: Long) {
                            if (child.destroyed || child.state.value.url != "about:blank") return
                            // document.write can finish before Java receives title/load callbacks.
                            child.update { it.copy(title = child.webView.title ?: it.title, isLoading = false) }
                            child.metadata()
                        }
                    })
                }
                view.postDelayed({ request.cancel() }, 10_000)
                return true
            }
            override fun onCloseWindow(window: WebView) {
                if (isPopupWindow && window === webView) eventChannel.trySend(EngineEvent.CloseWindow)
            }
            override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                fullscreenActive = true
                pageVideo.active = true
                fullscreenHost.showVideo(view, tabId, pageVideo.playback, false, pageVideo::control) {
                    fullscreenActive = false
                    pageVideo.release()
                    callback.onCustomViewHidden()
                }
            }
            override fun onHideCustomView() { fullscreenActive = false; pageVideo.release(); fullscreenHost.hideVideo(tabId) }
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.scheme != "https") { request.deny(); return }
                val supported = request.resources.mapNotNull { resource -> when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.MICROPHONE
                    else -> null
                } }.toSet()
                if (supported.isEmpty()) { request.deny(); return }
                pendingPermissions.add(request)
                val result: (Set<SitePermission>) -> Unit = { granted ->
                    pendingPermissionResults.remove(request)
                    if (!destroyed && pendingPermissions.remove(request)) {
                        val resources = request.resources.filter { resource -> when (resource) {
                            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SitePermission.CAMERA in granted
                            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SitePermission.MICROPHONE in granted
                            else -> false
                        } }.toTypedArray()
                        if (resources.isEmpty()) request.deny() else { mediaPermissionGranted = true; request.grant(resources) }
                    }
                }
                pendingPermissionResults[request] = result
                host.requestPermission(request.origin.toString(), supported, result)
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                pendingPermissions.remove(request)
                pendingPermissionResults.remove(request)?.let(host::cancelPermission)
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (Uri.parse(origin).scheme != "https") { callback.invoke(origin, false, false); return }
                geolocationPermissionResult?.let(host::cancelPermission)
                lateinit var result: (Set<SitePermission>) -> Unit
                result = { granted ->
                    if (geolocationPermissionResult === result) {
                        geolocationPermissionResult = null
                        callback.invoke(origin, !destroyed && SitePermission.LOCATION in granted, false)
                    }
                }
                geolocationPermissionResult = result
                host.requestPermission(origin, setOf(SitePermission.LOCATION), result)
            }
            override fun onGeolocationPermissionsHidePrompt() {
                val result = geolocationPermissionResult
                geolocationPermissionResult = null
                result?.let(host::cancelPermission)
            }
        }
        webView.setDownloadListener { url, userAgent, disposition, mimeType, _ ->
            // An attachment is not a document navigation. Keep the source page restorable.
            downloadUrl = url
            pendingExplicitLoad = null
            update { documentPage.copy(isLoading = false, progress = 100, desktopMode = it.desktopMode,
                translation = it.translation, visitBlockingExceptionSite = visitExceptionFor(documentPage.url)) }
            metadata()
            val offeredGeneration = navigationGeneration
            if (url.startsWith("https://") || url.startsWith("http://")) pendingDownloads++
            if (url.startsWith("https://") || url.startsWith("http://")) host.download(
                DownloadRequest(url, userAgent, disposition, mimeType,
                    cookies.getCookie(url), DownloadFilename.fromDisposition(disposition)
                        ?: URLUtil.guessFileName(url, disposition, mimeType))) { result ->
                pendingDownloads--
                if (result != DownloadResult.FAILED && !destroyed && !hasCommittedDocument && navigationGeneration == offeredGeneration) {
                    eventChannel.trySend(EngineEvent.DownloadHandledWithoutDocument)
                }
            }
            else if (url.startsWith("blob:") || url.startsWith("data:")) pageDownloads.request(url,
                DownloadFilename.fromDisposition(disposition) ?: URLUtil.guessFileName(url, disposition, mimeType))
            else host.showMessage(strings(R.string.ui_this_address_type_is_not_supported))
        }
        webView.setFindListener { active, count, _ -> update { it.copy(findMatches = count, activeFindMatch = active) } }
        adBlocker?.let { blocker -> scope.launch {
            blocker.status.first { it != "loading" }
            if (!destroyed) installCosmeticFiltering()
        } }
    }
    private fun update(change: (PageState) -> PageState) { if (!destroyed) mutableState.update(change) }
    override val supportsPageZoom: Boolean get() = true

    @Suppress("DEPRECATION")
    private fun captureZoomBaseline() {
        if (zoomBaselineGeneration == navigationGeneration) { updateZoomState(); return }
        zoomBaselineGeneration = navigationGeneration
        pageScale = webView.scale.takeIf { it.isFinite() && it > 0f }?.div(zoomDensity()) ?: pageScale
        zoomBaseScale = pageScale
        updateZoomState()
        val generation = navigationGeneration
        val interaction = zoomInteractionVersion
        // The initial overview scale can arrive after onPageFinished. Establish
        // 100% from the drawn document, but never rebase after a user interaction.
        webView.postVisualStateCallback(generation, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (destroyed || generation != navigationGeneration || interaction != zoomInteractionVersion || state.value.isLoading) return
                val scale = webView.scale
                if (scale.isFinite() && scale > 0f) { pageScale = scale / zoomDensity(); zoomBaseScale = pageScale; updateZoomState() }
            }
        })
    }

    private fun zoomDensity() = webView.resources.displayMetrics.density.coerceAtLeast(0.01f)

    @Suppress("DEPRECATION")
    private fun updateZoomState() {
        if (destroyed || zoomBaseScale <= 0f || pageScale <= 0f) return
        val percent = kotlin.math.round(pageScale / zoomBaseScale * 100f).toInt().coerceAtLeast(1)
        update { it.copy(pageZoomPercent = percent, canZoomIn = restoredZoomPercent == null && webView.canZoomIn(),
            canZoomOut = restoredZoomPercent == null && webView.canZoomOut()) }
        if (documentPage.url == state.value.url) documentPage = documentPage.copy(
            pageZoomPercent = state.value.pageZoomPercent, canZoomIn = state.value.canZoomIn, canZoomOut = state.value.canZoomOut)
    }

    override fun zoomIn() = changeZoom(ZoomCommand.IN)
    override fun zoomOut() = changeZoom(ZoomCommand.OUT)
    override fun resetZoom() = changeZoom(ZoomCommand.RESET)

    private fun cancelZoomWork() { zoomCommands.clear(); zoomJob?.cancel(); zoomJob = null }

    private fun cancelSnapshotPresentationRestore() {
        if (restoredZoomPercent == null && zoomRestoreJob == null) return
        zoomRestoreEpoch++
        zoomRestoreJob?.cancel(); zoomRestoreJob = null
        restoredZoomBaseScale = null; restoredZoomPercent = null; restoredZoomUrl = null; restoringScroll = null
        updateZoomState()
    }

    private suspend fun awaitZoomScaleSettled(before: Long) {
        withTimeoutOrNull(1_000) {
            while (zoomScaleVersion == before) delay(16)
            var observed = zoomScaleVersion
            var stableAt = android.os.SystemClock.uptimeMillis()
            while (android.os.SystemClock.uptimeMillis() - stableAt < 96) {
                delay(16)
                if (observed != zoomScaleVersion) { observed = zoomScaleVersion; stableAt = android.os.SystemClock.uptimeMillis() }
            }
        }
    }

    private suspend fun awaitZoomFrame() = suspendCancellableCoroutine<Unit> { continuation ->
        webView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { if (continuation.isActive) continuation.resumeWith(Result.success(Unit)) }
        })
        webView.invalidate()
    }

    @Suppress("DEPRECATION")
    private fun restoreSnapshotPresentation(snapshot: WebViewSnapshot) {
        val epoch = ++zoomRestoreEpoch
        val requestGeneration = navigationRequestGeneration
        val interaction = zoomInteractionVersion
        zoomRestoreJob = scope.launch {
            try {
                withTimeoutOrNull(30_000) {
                    // Some providers restore a document without onPageFinished.
                    // Await its rendered frame rather than treating URL metadata as a loaded page.
                    var generation: Long
                    do {
                        while (state.value.isLoading) delay(16)
                        generation = navigationGeneration
                        awaitZoomFrame()
                    } while (state.value.isLoading || generation != navigationGeneration)
                    fun stillCurrent() = !destroyed && epoch == zoomRestoreEpoch &&
                        requestGeneration == navigationRequestGeneration && generation == navigationGeneration && interaction == zoomInteractionVersion
                    if (!stillCurrent()) return@withTimeoutOrNull
                    pageScale = webView.scale / zoomDensity()
                    zoomBaseScale = restoredZoomBaseScale ?: pageScale
                    zoomBaselineGeneration = generation
                    val target = zoomBaseScale * snapshot.zoomPercent / 100f
                    if (pageScale > 0f && target > 0f && kotlin.math.abs(target / pageScale - 1f) > 0.005f) {
                        val before = zoomScaleVersion
                        webView.zoomBy((target / pageScale).coerceIn(0.01f, 100f))
                        awaitZoomScaleSettled(before)
                        awaitZoomFrame()
                    }
                    if (stillCurrent()) {
                        pageScale = webView.scale / zoomDensity()
                        // Restore native pixel scroll only after zoom settles; a late
                        // zoom anchor must not move a page away from its saved position.
                        webView.scrollTo(snapshot.scrollX, snapshot.scrollY)
                    }
                }
            } finally {
                if (epoch == zoomRestoreEpoch) {
                    zoomRestoreJob = null
                    restoredZoomBaseScale = null; restoredZoomPercent = null; restoredZoomUrl = null; restoringScroll = null
                    updateZoomState()
                }
            }
        }
    }

    private fun changeZoom(command: ZoomCommand) {
        cancelSnapshotPresentationRestore()
        if (destroyed || state.value.isLoading || state.value.readerArticle != null || state.value.readerLoading ||
            state.value.error != null || state.value.url == "about:blank") return
        zoomInteractionVersion++
        if (command == ZoomCommand.RESET) zoomCommands.clear()
        if (zoomCommands.size < 32) zoomCommands.addLast(command)
        if (zoomJob?.isActive == true) return
        val generation = navigationGeneration
        zoomJob = scope.launch {
            while (zoomCommands.isNotEmpty() && !destroyed && generation == navigationGeneration &&
                !state.value.isLoading && state.value.readerArticle == null && !state.value.readerLoading) {
                val before = zoomScaleVersion
                val changed = when (zoomCommands.removeFirst()) {
                    ZoomCommand.IN -> webView.zoomIn()
                    ZoomCommand.OUT -> webView.zoomOut()
                    ZoomCommand.RESET -> if (pageScale > 0f && zoomBaseScale > 0f && kotlin.math.abs(pageScale / zoomBaseScale - 1f) > 0.005f) {
                        webView.zoomBy((zoomBaseScale / pageScale).coerceIn(0.01f, 100f)); true
                    } else false
                }
                // A reset queued immediately after + must use the scale delivered
                // by that zoom, not the preceding UI-thread value. Native zoom
                // animations are serialized until their scale callback settles.
                if (changed) awaitZoomScaleSettled(before)
                updateZoomState()
            }
        }
    }
    private fun watchLoading() {
        loadingTimeout?.cancel()
        loadingTimeout = scope.launch {
            delay(30_000)
            if (state.value.isLoading) {
                failedNavigation = true
                webView.stopLoading()
                update { it.copy(isLoading = false, error = strings(R.string.ui_loading_timeout), errorKind = PageErrorKind.TIMEOUT) }
                metadata()
            }
        }
    }
    private fun startLinkDrag(): Boolean {
        val hit = webView.hitTestResult ?: return false
        // Native image dragging owns image/content URIs. Supply a URL explicitly for text links,
        // since a WebView may otherwise export only the link's visible label as plain text.
        if (hit.type != WebView.HitTestResult.SRC_ANCHOR_TYPE) return false
        val url = hit.extra?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) } ?: return false
        nativeDragHost = null
        return webView.startDragAndDrop(android.content.ClipData.newPlainText("Link", url),
            LinkDragShadow(webView.context, Uri.parse(url).host.orEmpty(), dragPreviewColors),
            null, android.view.View.DRAG_FLAG_GLOBAL)
    }
    override fun setDragPreviewColors(background: Int, content: Int, outline: Int, accent: Int) {
        dragPreviewColors = LinkDragPreviewColors(background, content, outline, accent)
    }
    private fun showPageContextMenu(): Boolean {
        val hit = webView.hitTestResult ?: return false
        val extra = hit.extra ?: return false
        fun http(value: String?) = value?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        fun show(link: String?, image: String?) {
            if (destroyed) return
            val actions = buildList {
                http(link)?.let { url ->
                    add(PageContextAction(strings(R.string.ui_open_link_new_tab)) { eventChannel.trySend(EngineEvent.OpenTab(url)) })
                    add(PageContextAction(strings(R.string.ui_copy_link)) { host.copyLink(url) })
                    add(PageContextAction(strings(R.string.ui_share_link)) { host.shareLink(url) })
                }
                http(image)?.let { url ->
                    add(PageContextAction(strings(R.string.ui_open_image_new_tab)) { eventChannel.trySend(EngineEvent.OpenTab(url)) })
                    add(PageContextAction(strings(R.string.ui_copy_image_link)) { host.copyLink(url) })
                    add(PageContextAction(strings(R.string.ui_save_image)) {
                        host.download(DownloadRequest(url, webView.settings.userAgentString, null, null,
                            cookies.getCookie(url), URLUtil.guessFileName(url, null, null)))
                    })
                }
            }
            if (actions.isNotEmpty()) host.showContextMenu(link ?: image.orEmpty(), actions)
        }
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> { if (http(extra) == null) false else { show(extra, null); true } }
            WebView.HitTestResult.IMAGE_TYPE -> { if (http(extra) == null) false else { show(null, extra); true } }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val generation = navigationGeneration
                val handler = android.os.Handler(android.os.Looper.getMainLooper()) { message ->
                    if (generation == navigationGeneration) show(http(message.data.getString("url")), http(message.data.getString("src")))
                    true
                }
                webView.requestFocusNodeHref(handler.obtainMessage()); true
            }
            else -> false
        }
    }
    private fun metadata() { eventChannel.trySend(EngineEvent.Metadata(state.value)) }
    private fun recordVisit() {
        if (!failedNavigation) eventChannel.trySend(EngineEvent.Visited(state.value))
    }
    private fun saveFavicon(pageUrl: String, icon: Bitmap) {
        val favicons = favicons ?: return
        val navigation = navigationGeneration
        val revision = ++iconGeneration
        val dark = darkTheme
        scope.launch {
            val path = favicons.save(pageUrl, icon, dark)
            if (navigation == navigationGeneration && revision == iconGeneration && dark == darkTheme && state.value.url == pageUrl && path != null) {
                update { it.copy(faviconUrl = path) }; metadata()
            }
        }
    }
    private fun discoverFavicon(view: WebView, pageUrl: String) {
        val favicons = favicons ?: return
        if (faviconOrigin(pageUrl) == null || receivedDocumentIcon || faviconDocumentTheme != darkTheme) return
        val navigation = navigationGeneration
        val revision = iconGeneration
        val dark = darkTheme
        view.evaluateJavascript("""
            JSON.stringify(Array.from(document.querySelectorAll('link[rel][href]'))
              .filter(l => l.rel.toLowerCase().split(/\s+/).some(r =>
                r === 'icon' || r === 'apple-touch-icon' || r === 'apple-touch-icon-precomposed'))
              .filter(l => !l.media || window.matchMedia(l.media).matches)
              .sort((a,b) => Number(!a.rel.toLowerCase().split(/\s+/).includes('icon')) -
                             Number(!b.rel.toLowerCase().split(/\s+/).includes('icon')))
              .map(l => l.href).slice(0, 8))
        """.trimIndent()) { result ->
            if (navigation != navigationGeneration || revision != iconGeneration || dark != darkTheme ||
                receivedDocumentIcon || state.value.url != pageUrl || destroyed) return@evaluateJavascript
            val links = runCatching {
                val decoded = JSONArray("[$result]").getString(0)
                val array = JSONArray(decoded)
                (0 until array.length()).map { array.getString(it) }
            }.getOrDefault(emptyList())
            faviconJob?.cancel()
            faviconJob = scope.launch {
                val path = favicons.resolve(pageUrl, state.value.faviconUrl, links, dark)
                if (navigation == navigationGeneration && revision == iconGeneration && dark == darkTheme && state.value.url == pageUrl && path != null) {
                    update { it.copy(faviconUrl = path) }; metadata()
                }
            }
        }
    }
    override fun loadUrl(url: String) = loadUrl(url, emptyMap())
    private fun cancelPointerSelectionMenu() {
        pointerSelection.invalidate()
        if (secondaryClick.selectionOnly) secondaryClick.reset()
    }
    private fun loadUrl(url: String, extraHeaders: Map<String, String>, preserveTranslation: Boolean = false) {
        if (destroyed) return
        if (url != "about:blank" && !url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        cancelPointerSelectionMenu()
        historyPreviews.leaving(traverse = false)
        val sameZoomDocument = isSameDocumentFragment(url)
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        restoredZoomBaseScale = null
        if (url == "about:blank") { zoomBaseScale = 0f; pageScale = 0f }
        navigationRequestGeneration++
        pendingExplicitLoad = PendingExplicitLoad(url, extraHeaders.toMap())
        if (!preserveTranslation) translationNavigation.reset()
        humanVerification.reset()
        blockingDocument = BlockingDocument(url)
        pageTranslation.reset(restore = true)
        applySiteDisplayMode(url)
        applyPageDarkening(webView, url)
        restoringScroll = null
        downloadUrl = null
        navigationGeneration++
        if (sameZoomDocument) zoomBaselineGeneration = navigationGeneration
        faviconJob?.cancel()
        update { it.copy(url = url, isLoading = url != "about:blank", progress = 0,
            rendersBlankDocument = if (url == "about:blank") false else it.rendersBlankDocument,
            pageZoomPercent = if (url == "about:blank") 100 else it.pageZoomPercent,
            canZoomIn = false, canZoomOut = false,
            readerArticle = null, readerLoading = false,
            blockedRequests = 0, visitBlockingExceptionSite = visitExceptionFor(url),
            title = if (url == "about:blank") "New tab" else url, error = null,
            faviconUrl = if (faviconOrigin(it.url) != null && faviconOrigin(it.url) == faviconOrigin(url)) it.faviconUrl else null) }
        metadata() // Persist the requested URL even when the initial network request stalls.
        if (url != "about:blank") watchLoading() else loadingTimeout?.cancel()
        if (extraHeaders.isEmpty()) webView.loadUrl(url) else webView.loadUrl(url, extraHeaders)
    }
    override fun reload() { if (!destroyed) {
        cancelPointerSelectionMenu()
        // reload() targets the committed native history entry. Before the requested
        // GET commits, that entry may still be the previous page or initial blank tab.
        pendingExplicitLoad?.takeIf { webView.copyBackForwardList().currentItem?.url != it.currentUrl }?.let { requested ->
            webView.stopLoading()
            translationNavigation.followLink()
            // Restart the original request; WebView follows its redirects again. Do not
            // attach the original headers directly to a potentially different origin.
            loadUrl(requested.url, requested.extraHeaders, preserveTranslation = true)
            return
        }
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        humanVerification.reset()
        translationNavigation.followLink()
        pageTranslation.reset(restore = true)
        navigationRequestGeneration++
        blockingDocument = BlockingDocument(state.value.url)
        applySiteDisplayMode(state.value.url)
        update { it.copy(isLoading = true, progress = 0, error = null, readerArticle = null, readerLoading = false, blockedRequests = 0) }; watchLoading(); webView.reload()
    } }
    override fun canScrollHorizontallyAt(x: Float, y: Float, direction: Int, result: (Boolean) -> Unit) {
        if (destroyed || webView.width == 0 || webView.height == 0) { result(true); return }
        // CSS coordinates account for desktop viewport scaling and pinch zoom. Walk
        // ancestors so a carousel or nested scroller gets the gesture before history.
        webView.evaluateJavascript("""(() => {
            const v = window.visualViewport;
            const x = ${x / webView.width} * (v ? v.width : innerWidth) + (v ? v.offsetLeft : 0);
            const y = ${y / webView.height} * (v ? v.height : innerHeight) + (v ? v.offsetTop : 0);
            let e = document.elementFromPoint(x, y);
            while (e) {
                if (e.tagName === 'IFRAME') return true;
                const s = getComputedStyle(e);
                if ((e === document.scrollingElement || /auto|scroll/.test(s.overflowX)) && e.scrollWidth > e.clientWidth + 1) {
                    if (s.direction === 'rtl') return true;
                    if (${direction} < 0 ? e.scrollLeft > 1 : e.scrollLeft < e.scrollWidth - e.clientWidth - 1) return true;
                }
                e = e.parentElement || (e.getRootNode && e.getRootNode().host);
            }
            return false;
        })()""") { result(it != "false") }
    }

    override fun goBack() { cancelPointerSelectionMenu(); if (state.value.readerArticle != null) { toggleReader(); return }; if (canGoBack()) {
        historyPreviews.leaving(traverse = true)
        pendingExplicitLoad = null
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        translationNavigation.followLink()
        navigationRequestGeneration++
        applyHistoryDisplayMode(-1)
        historyUrl(-1)?.let { target ->
            applyPageDarkening(webView, target)
            // An explicit history traversal to the native new-tab entry is legitimate.
            // Keep the stale-blank callback guard for pending HTTP loads/reloads.
            if (target == "about:blank") update { it.copy(url = target) }
        }
        update { it.copy(isLoading = true, error = null) }; watchLoading(); webView.goBack()
    } }
    override fun goForward() { if (canGoForward()) {
        cancelPointerSelectionMenu()
        historyPreviews.leaving(traverse = true)
        pendingExplicitLoad = null
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        translationNavigation.followLink()
        navigationRequestGeneration++
        applyHistoryDisplayMode(1)
        historyUrl(1)?.let { applyPageDarkening(webView, it) }
        update { it.copy(isLoading = true, error = null) }; watchLoading(); webView.goForward()
    } }
    override fun canGoBack() = !destroyed && (state.value.readerArticle != null || webView.canGoBack())
    override fun canGoForward() = !destroyed && webView.canGoForward()
    override fun evaluateJavascript(script: String) { if (!destroyed) webView.evaluateJavascript(script, null) }
    override val supportsPasswordAutofill get() = passwordAutofillApproved && privateProfileName == null
    override fun requestPasswordAutofill() {
        if (destroyed || privateProfileName != null) return
        if (!supportsPasswordAutofill) {
            host.showMessage(strings(R.string.ui_autofill_pending))
            return
        }
        if (state.value.isLoading || state.value.error != null || state.value.readerArticle != null) return
        val manager = autofillManager
        if (manager?.isEnabled != true) {
            PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.SERVICE_DISABLED)
            host.showMessage(strings(R.string.ui_autofill_enable_first))
            return
        }
        // Wait until the page menu's window is dismissed. WebView supplies the
        // real origin and virtual form fields; never synthesize HTML field IDs.
        val generation = navigationGeneration
        autofillRequestJob?.cancel()
        autofillRequestJob = scope.launch {
            val focused = withTimeoutOrNull(2_000) {
                while (!webView.hasWindowFocus()) delay(16)
                true
            } == true
            if (!focused || destroyed || generation != navigationGeneration || !webView.isAttachedToWindow) {
                PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.WINDOW_NOT_READY)
                return@launch
            }
            if (!webView.onCheckIsTextEditor()) {
                PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.NO_TEXT_EDITOR)
                host.showMessage(strings(R.string.ui_autofill_focus_field))
                return@launch
            }
            webView.requestFocus()
            val virtualId = autofillVirtualId
            if (virtualId == null) {
                PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.NO_FOCUSED_ID)
                host.showMessage(strings(R.string.ui_autofill_field_unidentified))
                return@launch
            }
            // WebView keeps the HTML field rectangle internal. Anchor the manual
            // suggestion at the user's native touch, in screen coordinates (also in Split).
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            val anchor = autofillTouchAnchor ?: android.graphics.PointF(webView.width / 2f, webView.height / 2f)
            val x = location[0] + anchor.x.toInt()
            val y = location[1] + anchor.y.toInt()
            val bounds = android.graphics.Rect(x, y, x + 1, y + 1)
            host.showMessage(strings(R.string.ui_autofill_requested))
            manualAutofillPending = true
            PasswordAutofillDiagnostics.record(PasswordAutofillDiagnostics.Event.MANUAL_REQUEST_CALLED)
            manager.requestAutofill(webView, virtualId, bounds)
        }
    }
    private fun visitExceptionFor(url: String): String? =
        com.takeruf.nagi.browser.blocking.blockingSite(url)?.takeIf { it in visitExceptionSites }

    private fun sameOrigin(first: String, second: String): Boolean = runCatching {
        val a = Uri.parse(first); val b = Uri.parse(second)
        fun port(uri: Uri) = uri.port.takeIf { it >= 0 } ?: if (uri.scheme.equals("https", true)) 443 else 80
        a.scheme.equals(b.scheme, true) && a.host != null && a.host.equals(b.host, true) && port(a) == port(b)
    }.getOrDefault(false)

    internal fun blockingActive(url: String): Boolean = adBlockingEnabled &&
        SiteDisplayModeStore.siteHost(url)?.let { it !in excludedHosts } == true && visitExceptionFor(url) == null

    override fun configureVisitBlockingExceptions(sites: Set<String>) {
        if (destroyed || visitExceptionSites == sites) return
        visitExceptionSites = sites.toSet()
        update { it.copy(visitBlockingExceptionSite = visitExceptionFor(it.url)) }
        installCosmeticFiltering()
    }

    override fun setDarkTheme(dark: Boolean) {
        if (destroyed || darkTheme == dark) return
        darkTheme = dark
        iconGeneration++
        faviconJob?.cancel()
        receivedDocumentIcon = false
        // WebView reads isLightTheme on configuration changes and updates native
        // prefers-color-scheme, including CSS, matchMedia listeners and child frames.
        webTheme.theme.setTo(webTheme.resources.newTheme().apply {
            applyStyle(if (dark) R.style.Theme_Nagi_Web_Dark else R.style.Theme_Nagi_Web_Light, true)
        })
        val configuration = android.content.res.Configuration(webTheme.resources.configuration).apply {
            uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        // Keep the default canvas white for transparent pages; authors own CSS backgrounds.
        webView.dispatchConfigurationChanged(configuration)
        applyPageDarkening(webView, state.value.url)
    }

    override fun configurePageDarkening(enabled: Boolean, excludedHosts: Set<String>) {
        if (destroyed || (pageDarkeningEnabled == enabled && darkModeExcludedHosts == excludedHosts)) return
        pageDarkeningEnabled = enabled
        darkModeExcludedHosts = excludedHosts.toSet()
        applyPageDarkening(webView, state.value.url)
    }

    private fun applyPageDarkening(view: WebView, url: String) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) return
        val host = SiteDisplayModeStore.siteHost(url)
        val allowed = darkTheme && pageDarkeningEnabled && host != null && host !in darkModeExcludedHosts
        if (WebSettingsCompat.isAlgorithmicDarkeningAllowed(view.settings) != allowed) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, allowed)
        }
    }

    private fun historyUrl(offset: Int): String? = webView.copyBackForwardList().let {
        it.getItemAtIndex(it.currentIndex + offset)?.url
    }

    override fun configureContentBlocking(enabled: Boolean, excludedHosts: Set<String>) {
        val changed = adBlockingEnabled != enabled || this.excludedHosts != excludedHosts
        adBlockingEnabled = enabled; this.excludedHosts = excludedHosts.toSet()
        if (changed && !destroyed) installCosmeticFiltering()
    }

    private fun installCosmeticFiltering() {
        val url = state.value.url
        val enabled = blockingActive(url) && adBlocker != null
        val selectors = if (enabled) adBlocker?.cosmeticSelectors(url).orEmpty() else emptyList()
        val css = if (enabled) selectors.joinToString("\n") { "$it { display: none !important; }" } +
            "\n[data-nagi-adblock-collapse=\"1\"],[data-nagi-adblock-resource=\"1\"] { display: none !important; }" else ""
        val source = blockingDocument
        val blockedUrls = if (enabled && source.url.substringBefore('#') == url.substringBefore('#'))
            synchronized(source.blockedUrls) { source.blockedUrls.toList() } else emptyList()
        webView.evaluateJavascript("""
            (() => { if (location.href !== ${JSONObject.quote(url)}) return;
              $adSlotScript
              let style = document.getElementById('__nagi_adblock_style');
              const css = ${JSONObject.quote(css)};
              if (!css) { if (style) style.remove(); }
              else { if (!style) { style = document.createElement('style'); style.id = '__nagi_adblock_style';
                (document.head || document.documentElement).appendChild(style); }
                style.textContent = css; }
              window.__nagiAdSlots.update(${JSONArray(selectors)}, ${JSONArray(blockedUrls)}, $enabled); })()
        """.trimIndent(), null)
    }

    override fun toggleReader() {
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        if (destroyed) return
        if (state.value.readerArticle != null) { update { it.copy(readerArticle = null) }; return }
        if (state.value.readerLoading || state.value.isLoading || !state.value.url.startsWith("http")) return
        translationNavigation.reset()
        pageTranslation.reset(restore = true)
        val generation = navigationGeneration
        val url = state.value.url
        update { it.copy(readerLoading = true) }
        webView.evaluateJavascript("""
            (() => { $readerScript
              try { const article = new Readability(document.cloneNode(true), { maxElemsToParse: 20000 }).parse();
                return article && article.content.length <= 2000000 ? JSON.stringify({
                    title: article.title, byline: article.byline || '', content: article.content }) : null;
              } catch (_) { return null; }
            })()
        """.trimIndent()) { result ->
            if (destroyed || generation != navigationGeneration || state.value.url != url) return@evaluateJavascript
            scope.launch {
                val article = withContext(Dispatchers.Default) { runCatching {
                    val data = JSONObject(JSONArray("[$result]").getString(0))
                    val content = com.takeruf.nagi.browser.reader.ReaderSanitizer.clean(data.getString("content"), url)
                    if (content.isBlank()) null else com.takeruf.nagi.browser.reader.ReaderArticle(
                        data.optString("title"), data.optString("byline"), content, url)
                }.getOrNull() }
                if (!destroyed && generation == navigationGeneration && state.value.url == url) {
                    update { it.copy(readerArticle = article, readerLoading = false) }
                    if (article == null) host.showMessage(strings(R.string.ui_reader_unavailable))
                }
            }
        }
    }
    private fun applyUserAgent(mode: SiteDisplayMode) {
        humanVerification.setDesktopMode(mode == SiteDisplayMode.DESKTOP)
        val desktop = mode == SiteDisplayMode.DESKTOP
        val version = Regex("Chrome/([0-9.]+)").find(mobileUa)?.groupValues?.get(1) ?: "130.0.0.0"
        webView.settings.userAgentString = when (mode) {
            SiteDisplayMode.DESKTOP -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
            SiteDisplayMode.MOBILE -> mobileUa
        }
        // Client Hints can otherwise keep reporting the underlying tablet/phone
        // even after the UA string changes. Keep both signals in the selected mode.
        defaultUaMetadata?.let { defaults ->
            val metadata = UserAgentMetadata.Builder(defaults)
            metadata.setMobile(!desktop)
            if (desktop) metadata.setPlatform("Linux").setPlatformVersion("")
                .setArchitecture("x86").setBitness(64).setModel("").setWow64(false)
            WebSettingsCompat.setUserAgentMetadata(webView.settings, metadata.build())
        }
    }
    private fun currentDisplayMode(): SiteDisplayMode =
        if (state.value.desktopMode) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE
    private fun displayModeFor(url: String): SiteDisplayMode? =
        if (SiteDisplayModeStore.siteHost(url) == null) null else siteDisplayModes.mode(url)
            ?: if (defaultDesktopMode()) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE
    private fun applySiteDisplayMode(url: String) {
        val mode = displayModeFor(url) ?: return
        if (currentDisplayMode() == mode) return
        webView.stopLoading()
        update { it.copy(desktopMode = mode == SiteDisplayMode.DESKTOP) }
        applyUserAgent(mode)
    }
    private fun applyHistoryDisplayMode(offset: Int) {
        val history = webView.copyBackForwardList()
        history.getItemAtIndex(history.currentIndex + offset)?.url?.let {
            blockingDocument = BlockingDocument(it)
            if (!isSameDocumentFragment(it)) applySiteDisplayMode(it)
        }
    }
    private fun isSameDocumentFragment(url: String): Boolean {
        val current = webView.url ?: return false
        return (url.contains('#') || current.contains('#')) &&
            url.substringBefore('#') == current.substringBefore('#')
    }
    override fun setDesktopMode(enabled: Boolean) {
        if (destroyed) return
        siteDisplayModes.remember(state.value.url, enabled)
        changeDisplayMode(if (enabled) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE)
    }
    private fun changeDisplayMode(mode: SiteDisplayMode) {
        if (currentDisplayMode() == mode) return
        // Changing the UA during a load already restarts WebView's request. Stop it first
        // so this explicit reload is the only request triggered by the display toggle.
        webView.stopLoading()
        update { it.copy(desktopMode = mode == SiteDisplayMode.DESKTOP) }
        applyUserAgent(mode)
        reload()
    }
    override fun retryGoogleSignInInMobileMode() {
        if (destroyed || !state.value.desktopMode) return
        val currentUrl = state.value.url
        val retryUrl = GoogleSignInRecovery.retryUrl(currentUrl) ?: return
        siteDisplayModes.remember(currentUrl, false)
        // loadUrl applies the remembered mode before requesting the sign-in entry point.
        loadUrl(retryUrl)
    }
    /** Only a pure host mismatch on the main document qualifies; any other certificate fault still fails. */
    private fun wwwRetryUrl(error: SslError): String? {
        if (!error.hasError(SslError.SSL_IDMISMATCH)) return null
        val otherFaults = listOf(SslError.SSL_NOTYETVALID, SslError.SSL_EXPIRED, SslError.SSL_UNTRUSTED,
            SslError.SSL_DATE_INVALID, SslError.SSL_INVALID)
        if (otherFaults.any(error::hasError)) return null
        val errorHost = runCatching { java.net.URI(error.url).host }.getOrNull() ?: return null
        val pageHost = runCatching { java.net.URI(state.value.url).host }.getOrNull() ?: return null
        if (!errorHost.equals(pageHost, ignoreCase = true)) return null
        val certificate = error.certificate ?: return null
        val names = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            runCatching {
                certificate.x509Certificate?.subjectAlternativeNames.orEmpty()
                    .filter { it.getOrNull(0) == 2 }.mapNotNull { it.getOrNull(1) as? String }
            }.getOrDefault(emptyList())
        } else emptyList()
        return CertificateHostRecovery.wwwRetryUrl(state.value.url, names.ifEmpty { listOfNotNull(certificate.issuedTo?.cName) })
    }
    override fun findInPage(query: String) { (readerSurface ?: webView).findAllAsync(query) }
    override fun findNext(forward: Boolean) { (readerSurface ?: webView).findNext(forward) }
    override fun clearFind() { (readerSurface ?: webView).clearMatches(); update { it.copy(findMatches = 0, activeFindMatch = 0) } }
    private fun continuePageTranslation(url: String) {
        if (automaticTranslationGeneration == navigationGeneration) return
        automaticTranslationGeneration = navigationGeneration
        val continuation = translationNavigation.takeContinuation()
        if (failedNavigation || state.value.error != null || !(url.startsWith("http://") || url.startsWith("https://"))) return
        if (continuation == null && alwaysTranslateRules().isEmpty()) return
        pageTranslation.startAutomatic { language ->
            com.takeruf.nagi.browser.translation.TranslationNavigationState.choose(language, continuation, alwaysTranslateRules())
                ?.also { translationWifiOnly = it.wifiOnly }
        }
    }
    override fun translatePage(source: String?, target: String, wifiOnly: Boolean, alwaysTranslate: Boolean) {
        if (destroyed || state.value.isLoading || state.value.readerLoading || state.value.error != null ||
            !(state.value.url.startsWith("http://") || state.value.url.startsWith("https://"))) return
        update { it.copy(readerArticle = null) }
        translationNavigation.reset()
        translationWifiOnly = wifiOnly
        pageTranslation.start(source, target, wifiOnly) { language ->
            if (alwaysTranslate) rememberAlwaysTranslate(language, target)
        }
    }
    override fun restoreOriginalPage() { if (!destroyed) {
        translationNavigation.reset()
        pageTranslation.reset(restore = true)
    } }
    override fun setVisible(visible: Boolean) {
        if (!visible) cancelPointerSelectionMenu()
        pageTranslation.setVisible(visible)
        humanVerification.setVisible(visible)
        if (visible) webView.onResume() else webView.onPause()
    }
    override fun saveState(): EngineSnapshot? {
        if (destroyed) return null
        val bundle = Bundle()
        return if (webView.saveState(bundle) != null) WebViewSnapshot(bundle, webView.scrollX, webView.scrollY,
            state.value.url, state.value.desktopMode, zoomBaseScale, state.value.pageZoomPercent) else null
    }
    override fun restoreState(snapshot: EngineSnapshot): Boolean {
        if (snapshot !is WebViewSnapshot) return false
        pendingExplicitLoad = null
        cancelZoomWork(); cancelSnapshotPresentationRestore()
        navigationRequestGeneration++
        blockingDocument = BlockingDocument(snapshot.url)
        // Restore the cached document in the mode in which it was fetched. Current
        // window defaults and per-site choices apply on the next navigation/reload,
        // not by replacing a page merely because it is selected or restored.
        update { it.copy(desktopMode = snapshot.desktopMode) }
        applyUserAgent(currentDisplayMode())
        restoringScroll = snapshot.scrollX to snapshot.scrollY
        restoredZoomBaseScale = snapshot.zoomBaseScale.takeIf { it > 0f }
        restoredZoomPercent = snapshot.zoomPercent
        restoredZoomUrl = snapshot.url
        if (snapshot.zoomBaseScale > 0f) zoomBaseScale = snapshot.zoomBaseScale
        applyPageDarkening(webView, snapshot.url)
        val restored = runCatching { webView.restoreState(snapshot.bundle) != null }.getOrDefault(false)
        if (!restored) { restoringScroll = null; restoredZoomBaseScale = null; restoredZoomPercent = null; restoredZoomUrl = null }
        if (restored) {
            update { it.copy(url = webView.url ?: "about:blank", visitBlockingExceptionSite = visitExceptionFor(webView.url ?: "about:blank"), title = webView.title ?: "New tab",
                canGoBack = webView.canGoBack(), canGoForward = webView.canGoForward()) }
            documentPage = state.value
            restoreSnapshotPresentation(snapshot)
        }
        return restored
    }
    private fun keepsPopupAlive(): Boolean = popupOpener != null || popupChildren.isNotEmpty() ||
        pendingPopups.isNotEmpty() || (state.value.rendersBlankDocument && state.value.url == "about:blank")

    override fun canSuspend(result: (Boolean) -> Unit) {
        if (destroyed || keepsPopupAlive() || state.value.isLoading || restoringScroll != null || fileCallback != null || pendingPermissions.isNotEmpty() ||
            mediaPermissionGranted || fullscreenActive || pendingDownloads > 0 || zoomJob?.isActive == true || restoredZoomPercent != null || pageDownloads.isBusy || pageTranslation.active) { result(false); return }
        val generation = navigationGeneration
        webView.evaluateJavascript("""
            (function(){
              if (window.__nagiEdited) return false;
              if (window.__nagiPageDownloads && window.__nagiPageDownloads.isBusy()) return false;
              if (document.querySelector('dialog[open]')) return false;
              if (Array.from(document.querySelectorAll('audio,video')).some(e=>!e.paused&&!e.ended)) return false;
              if (document.querySelector('[contenteditable]:not([contenteditable="false"])')) return false;
              return !Array.from(document.querySelectorAll('input,textarea,select')).some(e=>{
                if(e.type==='hidden'||e.type==='submit'||e.type==='button') return false;
                if(e.type==='file') return e.files&&e.files.length>0;
                if(e.type==='checkbox'||e.type==='radio') return e.checked!==e.defaultChecked;
                if(e.tagName==='SELECT') return Array.from(e.options).some(o=>o.selected!==o.defaultSelected);
                return e.value!==e.defaultValue || (e.type==='password'&&e.value.length>0);
              });
            })()
        """.trimIndent()) { value -> result(!destroyed && generation == navigationGeneration && value == "true" &&
            !keepsPopupAlive() &&
            !state.value.isLoading && fileCallback == null && pendingPermissions.isEmpty() && !mediaPermissionGranted &&
            !fullscreenActive && pendingDownloads == 0 && zoomJob?.isActive != true && restoredZoomPercent == null && !pageDownloads.isBusy && !pageTranslation.active) }
    }
    override fun destroy() {
        observedIcon?.recycle()
        observedIcon = null
        if (destroyed) return
        secondaryClick.reset()
        pointerSelection.destroy()
        pageTranslation.reset(restore = false)
        destroyed = true
        historyPreviews.clear()
        if (privateProfileName == null) autofillManager?.let { WebViewAutofillCallbacks.unregister(it, webView) }
        humanVerification.destroy()
        if (fullscreenActive) fullscreenHost.hideVideo(tabId)
        pageVideo.destroy()
        pageDownloads.destroy()
        fileCallback?.onReceiveValue(null); fileCallback = null
        pendingPermissions.forEach { it.deny() }; pendingPermissions.clear()
        val cancelledPermissionResults = pendingPermissionResults.values.toList()
        pendingPermissionResults.clear()
        cancelledPermissionResults.forEach(host::cancelPermission)
        val cancelledGeolocationResult = geolocationPermissionResult
        geolocationPermissionResult = null
        cancelledGeolocationResult?.let(host::cancelPermission)
        pendingPopups.toList().forEach { it.cancel() }
        popupOpener?.popupChildren?.remove(this)
        popupOpener = null
        popupChildren.forEach { it.popupOpener = null }
        popupChildren.clear()
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.stopLoading(); webView.onPause(); webView.destroy()
        scope.cancel(); eventChannel.close()
    }
}
