package com.takeruf.nagi.browser.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PageState(
    val url: String = "about:blank", val title: String = "New tab", val faviconUrl: String? = null,
    val progress: Int = 100, val isLoading: Boolean = false,
    val canGoBack: Boolean = false, val canGoForward: Boolean = false,
    val desktopMode: Boolean = false, val error: String? = null,
    val findMatches: Int = 0, val activeFindMatch: Int = 0,
)

sealed interface EngineEvent {
    data class Metadata(val page: PageState) : EngineEvent
    data class Visited(val page: PageState) : EngineEvent
    data class OpenTab(val url: String) : EngineEvent
}

/** Opaque engine-owned state. A Chromium adapter can supply its own snapshot type. */
interface EngineSnapshot

interface BrowserEngine {
    val state: StateFlow<PageState>
    val events: Flow<EngineEvent>
    fun loadUrl(url: String)
    fun reload()
    fun goBack()
    fun goForward()
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun evaluateJavascript(script: String)
    fun setDesktopMode(enabled: Boolean)
    fun findInPage(query: String)
    fun findNext(forward: Boolean)
    fun clearFind()
    fun setVisible(visible: Boolean)
    fun saveState(): EngineSnapshot?
    fun restoreState(snapshot: EngineSnapshot): Boolean
    fun destroy()
}

enum class SitePermission { CAMERA, MICROPHONE, LOCATION }
data class FileSelectionRequest(val mimeTypes: List<String>, val multiple: Boolean)
data class DownloadRequest(val url: String, val userAgent: String, val contentDisposition: String?,
    val mimeType: String?, val cookies: String?, val suggestedName: String)
data class PageContextAction(val label: String, val execute: () -> Unit)

interface BrowserHost {
    fun showContextMenu(title: String, actions: List<PageContextAction>) {}
    fun copyLink(url: String) {}
    fun shareLink(url: String) {}
    fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit)
    fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit)
    fun download(request: DownloadRequest)
    fun openExternal(url: String)
    fun showMessage(message: String)
}
