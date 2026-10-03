package com.orbit.browser.browser.tabs

import com.orbit.browser.browser.engine.*

/** Main-thread confined LRU. Visible split panes are protected from eviction. */
class EnginePool(private val capacity: Int = 3,
    private val snapshotCapacity: Int = 20,
    private val factory: (String, Boolean) -> BrowserEngine,
    private val onCreate: (String, BrowserEngine) -> Unit = { _, _ -> },
    private val onRemove: (String) -> Unit = {},
) {
    private val engines = LinkedHashMap<String, BrowserEngine>(capacity, 0.75f, true)
    private val snapshots = LinkedHashMap<String, EngineSnapshot>(snapshotCapacity, 0.75f, true)
    private var visible = emptySet<String>()
    init { require(capacity >= 2); require(snapshotCapacity >= 0) }
    val size get() = engines.size
    fun peek(id: String): BrowserEngine? = engines.entries.firstOrNull { it.key == id }?.value

    fun setVisible(ids: Set<String>) {
        require(ids.size <= capacity)
        visible = ids
        engines.forEach { (id, engine) -> engine.setVisible(id in ids) }
        trim()
    }
    fun acquire(id: String, url: String, desktop: Boolean): BrowserEngine {
        engines[id]?.let { return it }
        while (engines.size >= capacity) {
            val victim = engines.keys.firstOrNull { it !in visible } ?: error("All sessions are visible")
            evict(victim, save = true)
        }
        val engine = factory(id, desktop)
        engines[id] = engine
        onCreate(id, engine)
        val restored = snapshots.remove(id)?.let { engine.restoreState(it) } ?: false
        if (!restored) engine.loadUrl(url)
        engine.setVisible(id in visible)
        return engine
    }
    fun retainTabIds(ids: Set<String>) {
        engines.keys.filter { it !in ids }.toList().forEach { evict(it, save = false) }
        snapshots.keys.retainAll(ids)
    }
    private fun trim() {
        while (engines.size > capacity) engines.keys.firstOrNull { it !in visible }?.let { evict(it, true) } ?: break
    }
    private fun evict(id: String, save: Boolean) {
        val engine = engines.remove(id) ?: return
        if (save && snapshotCapacity > 0) engine.saveState()?.let {
            snapshots[id] = it
            while (snapshots.size > snapshotCapacity) snapshots.remove(snapshots.keys.first())
        }
        onRemove(id); engine.destroy()
    }
    fun destroyAll() {
        engines.keys.toList().forEach { evict(it, false) }; snapshots.clear(); visible = emptySet()
    }
}
