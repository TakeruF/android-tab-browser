package com.takeruf.nagi.browser.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PageState(
    val url: String = "about:blank", val title: String = "New tab", val faviconUrl: String? = null,
    val progress: Int = 100, val isLoading: Boolean = false,
    val canGoBack: Boolean = false, val canGoForward: Boolean = false,
    val desktopMode: Boolean = false, val error: String? = null,
    val findMatches: Int = 0, val activeFindMatch: Int = 0,
    val isSuspended: Boolean = false,
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
    /** Conservative eligibility check; adapters without a check keep their live page. */
    fun canSuspend(result: (Boolean) -> Unit) { result(false) }
    fun destroy()
}

enum class SitePermission { CAMERA, MICROPHONE, LOCATION }
data class FileSelectionRequest(val mimeTypes: List<String>, val multiple: Boolean, val capture: Boolean = false)
data class GeneratedDownload(val name: String, val mimeType: String, val size: Long, val origin: String)
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
    fun confirmGeneratedDownload(request: GeneratedDownload, result: (Boolean) -> Unit) { result(false) }
    fun saveGeneratedDownload(request: GeneratedDownload, file: java.io.File) { file.delete() }
    fun openExternal(url: String)
    fun openExternalLink(url: String, fallback: (String) -> Unit): Boolean = false
    fun showMessage(message: String)
}
