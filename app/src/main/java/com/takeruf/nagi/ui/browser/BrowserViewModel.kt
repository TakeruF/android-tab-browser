package com.takeruf.nagi.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.takeruf.nagi.AppContainer
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BrowserUiState(val workspace: WorkspaceSnapshot = WorkspaceSnapshot(),
    val settings: BrowserSettings = BrowserSettings(), val ready: Boolean = false, val startupError: String? = null) {
    val currentSpace get() = workspace.spaces.firstOrNull { it.id == settings.selectedSpaceId } ?: workspace.spaces.firstOrNull()
    val visibleTabs get() = workspace.tabs.filter { it.spaceId == currentSpace?.id && it.closedAt == null && it.archivedAt == null }
    val activeTab get() = visibleTabs.firstOrNull { it.id == currentSpace?.activeTabId } ?: visibleTabs.firstOrNull()
    val favorites get() = workspace.bookmarks.filter { it.isFavorite }
}

private data class SpacePreview(val id: String, val name: String, val icon: String, val color: Long)

/** Workspace state and transient appearance previews. No View, Activity or WebView reference. */
class BrowserViewModel(private val container: AppContainer) : ViewModel() {
    private val initialized = MutableStateFlow(false)
    private val startupError = MutableStateFlow<String?>(null)
    private val spacePreview = MutableStateFlow<SpacePreview?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val state = combine(container.workspace.snapshot, container.settings.settings, initialized, startupError, spacePreview) { workspace, settings, ready, error, preview ->
        val displayed = if (preview == null) workspace else workspace.copy(spaces = workspace.spaces.map { space ->
            if (space.id == preview.id) space.copy(name = preview.name, icon = preview.icon, color = preview.color) else space
        })
        BrowserUiState(displayed, settings, ready, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState())
    init { viewModelScope.launch {
        try { container.workspace.ready.await(); initialized.value = true }
        catch (e: Exception) { startupError.value = e.message ?: "Could not open workspace" }
    } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.emit(e.message ?: "Action could not be completed") }
    }
    fun newTab(url: String = "about:blank") = action { state.value.currentSpace?.let { container.tabs.create(it.id, url) } }
    fun newTabInSpace(spaceId: String, url: String) = action { container.tabs.create(spaceId, url, select = false) }
    suspend fun createTab(url: String = "about:blank", select: Boolean = true): String? = state.value.currentSpace?.let { container.tabs.create(it.id, url, select) }
    fun selectTab(id: String) = action { container.tabs.select(id) }
    fun closeTab(id: String) = action { container.tabs.close(id) }
    fun closeSplitTab(id: String, remainingId: String, onClosed: () -> Unit) = action {
        // Select the surviving pane explicitly, even when another sidebar tab is closer.
        container.tabs.select(remainingId)
        container.tabs.close(id)
        onClosed()
    }
    fun closeTabOnBack(id: String) = action { container.tabs.close(id, selectNext = true) }
    fun restoreClosed() = action { if (container.tabs.restoreClosed() == null) messages.emit("No closed tabs to restore") }
    fun togglePin(id: String) = action { container.tabs.togglePin(id) }
    fun accessTab(id: String) = action { container.tabs.recordAccess(id) }
    fun reorder(id: String, target: String) = action { container.tabs.reorder(id, target) }
    fun moveTab(id: String, spaceId: String) = action { container.tabs.moveToSpace(id, spaceId) }
    fun createSpace(name: String, icon: String = "◉", color: Long? = null) = action { container.spaces.create(name, icon, color) }
    fun previewSpace(id: String, name: String, icon: String, color: Long) {
        spacePreview.value = SpacePreview(id, name, icon, color)
    }
    fun cancelSpacePreview(id: String) {
        if (spacePreview.value?.id == id) spacePreview.value = null
    }
    fun editSpace(id: String, name: String, icon: String, color: Long) = action {
        val preview = spacePreview.value
        try {
            container.spaces.edit(id, name, icon, color)
            // Keep the preview until Room publishes the saved appearance, avoiding an old-color flash.
            container.workspace.snapshot.first { snapshot ->
                snapshot.spaces.none { it.id == id } || snapshot.spaces.any {
                    it.id == id && it.name == name.trim() && it.icon == icon && it.color == color
                }
            }
        } finally {
            if (spacePreview.value == preview) cancelSpacePreview(id)
        }
    }
    fun renameSpace(id: String, name: String) = action { container.spaces.rename(id, name) }
    fun deleteSpace(id: String) = action { container.spaces.delete(id) }
    fun selectSpace(id: String) = action { container.spaces.select(id) }
    fun removeBookmark(id: String) = action { container.library.removeBookmark(id) }
    fun clearHistory() = action { container.library.clearHistory() }
    fun deleteHistory(id: Long) = action { container.library.deleteHistory(id) }
    fun updateSettings(change: (BrowserSettings) -> BrowserSettings) = action { container.settings.update(change) }
    fun saveEngine(engine: SearchEngine) = action { container.engines.save(engine) }
    fun deleteEngine(id: String) = action { container.engines.delete(id) }
    fun defaultAiEngine(id: String) = action { container.engines.setDefaultAi(id) }
    fun defaultEngine(id: String) = action { container.engines.setDefault(id) }
    fun automaticSearchRegion(enabled: Boolean) = action {
        container.settings.update { it.copy(automaticSearchRegion = enabled) }
        if (enabled) container.regionalSearch.refresh(force = true)
    }
    fun refreshSearchRegion() = action { container.regionalSearch.refresh(force = true) }
    fun dropTab(id: String, pinned: Boolean, target: String?, after: Boolean) = action { container.tabs.drop(id, pinned, target, after) }
    fun favoriteTab(id: String, target: String? = null, after: Boolean = false) = action { container.tabs.moveToFavorite(id, target, after) }
    fun dropFavorite(id: String, pinned: Boolean, target: String?, after: Boolean) = action {
        state.value.currentSpace?.let { container.tabs.dropFavorite(id, it.id, pinned, target, after) }
    }
    fun reorderFavorite(id: String, target: String?, after: Boolean) = action { container.tabs.reorderFavorite(id, target, after) }
    fun archiveNow() = action { container.tabs.archiveNow() }
    /** Same order and opening behavior as the sidebar, including shared Favorites. */
    fun selectNumberedTab(number: Int): Boolean {
        val target = state.value.numberedTabTarget(number) ?: return false
        when (target) {
            is NumberedTabTarget.Favorite -> newTab(target.url)
            is NumberedTabTarget.Tab -> selectTab(target.id)
        }
        return true
    }
    fun cycleTab(forward: Boolean): String? {
        val tabs = state.value.visibleTabs
        if (tabs.isEmpty()) return null
        val index = tabs.indexOfFirst { it.id == state.value.activeTab?.id }.coerceAtLeast(0)
        val id = tabs[Math.floorMod(index + if (forward) 1 else -1, tabs.size)].id
        selectTab(id)
        return id
    }
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BrowserViewModel::class.java))
            return BrowserViewModel(container) as T
        }
    }
}
