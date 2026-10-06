package com.takeruf.nagi.browser.tabs

import com.takeruf.nagi.AppContainer
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.domain.model.BrowserTab
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Activity-lifetime sessions, independent of Room models and ViewModel UI state. */
class BrowserSessionController(private val container: AppContainer,
    factory: (String, Boolean) -> BrowserEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val collectors = mutableMapOf<String, Job>()
    private val mutablePages = MutableStateFlow<Map<String, PageState>>(emptyMap())
    val pages = mutablePages.asStateFlow()
    val generation = MutableStateFlow(0)
    val openedTabs = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val pool = EnginePool(factory = factory, onCreate = { id, engine ->
        collectors[id] = scope.launch {
            launch { engine.state.collect { page -> mutablePages.update { it + (id to page) } } }
            engine.events.collect { event -> when (event) {
                is EngineEvent.Metadata -> withContext(Dispatchers.IO) {
                    container.tabs.updatePage(id, event.page.url, event.page.title, event.page.faviconUrl)
                }
                is EngineEvent.Visited -> withContext(Dispatchers.IO) {
                    container.library.recordVisit(event.page.url, event.page.title, event.page.faviconUrl)
                }
                is EngineEvent.OpenTab -> {
                    val tab = container.workspace.dao.tab(id)
                    if (tab != null && tab.closedAt == null) {
                        val newId = container.tabs.create(tab.spaceId, event.url, parentTabId = id)
                        openedTabs.emit(newId)
                    }
                }
            } }
        }
    }, onRemove = { id -> collectors.remove(id)?.cancel(); mutablePages.update { it - id } })

    fun acquire(tab: BrowserTab, desktopDefault: Boolean) = pool.acquire(tab.id, tab.url, desktopDefault)
    fun navigate(tab: BrowserTab, url: String, desktopDefault: Boolean) {
        acquire(tab, desktopDefault).loadUrl(url)
    }
    fun dispose() { pool.destroyAll(); scope.cancel() }
    fun reset() { pool.destroyAll(); generation.value++ }
}
