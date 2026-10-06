package com.takeruf.nagi.browser.tabs

import com.takeruf.nagi.browser.engine.*

/** Main-thread confined sessions with conservative suspension of old, hidden pages. */
class EnginePool(
    private val factory: (String, Boolean) -> BrowserEngine,
    private val onCreate: (String, BrowserEngine) -> Unit = { _, _ -> },
    private val onRemove: (String) -> Unit = {},
    private val onSuspend: (String, PageState) -> Unit = { _, _ -> },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Saved(val snapshot: EngineSnapshot?, val page: PageState)
    private val engines = LinkedHashMap<String, BrowserEngine>()
    private val saved = LinkedHashMap<String, Saved>()
    private val accessed = mutableMapOf<String, Long>()
    private val checking = mutableSetOf<String>()
    private var visible = emptySet<String>()
    private var protected = emptySet<String>()
    val size get() = engines.size
    val suspendedIds get() = saved.keys.toSet()
    fun peek(id: String): BrowserEngine? = engines[id]
    fun protect(ids: Set<String>) { protected = ids }

    fun setVisible(ids: Set<String>) {
        val now = clock()
        (visible + ids).forEach { accessed[it] = now }
        visible = ids
        engines.forEach { (id, engine) -> engine.setVisible(id in ids) }
    }
    fun acquire(id: String, url: String, desktop: Boolean): BrowserEngine {
        accessed[id] = clock()
        engines[id]?.let { return it }
        val previous = saved.remove(id)
        val engine = factory(id, previous?.page?.desktopMode ?: desktop)
        engines[id] = engine
        onCreate(id, engine)
        if (previous?.snapshot == null || !engine.restoreState(previous.snapshot)) engine.loadUrl(previous?.page?.url ?: url)
        engine.setVisible(id in visible)
        return engine
    }
    fun suspendBackground(maxLive: Int = 6, idleMillis: Long = 10 * 60_000L) {
        val now = clock()
        engines.keys.filter { it !in visible && it !in protected && it !in checking && now - (accessed[it] ?: now) >= idleMillis }
            .sortedBy { accessed[it] }.forEach { id ->
                if (engines.size <= maxLive) return
                val engine = engines[id] ?: return@forEach
                val accessedAt = accessed[id]
                checking.add(id)
                engine.canSuspend { safe ->
                    checking.remove(id)
                    if (safe && engines[id] === engine && id !in visible && id !in protected &&
                        accessed[id] == accessedAt && engines.size > maxLive) {
                        val page = engine.state.value
                        val snapshot = engine.saveState()
                        // Cap history snapshots while keeping a cheap URL record for every sleeping tab.
                        if (saved.values.count { it.snapshot != null } >= 32) {
                            saved.entries.firstOrNull { it.value.snapshot != null }?.let { it.setValue(it.value.copy(snapshot = null)) }
                        }
                        saved[id] = Saved(snapshot, page)
                        remove(id)
                        onSuspend(id, page)
                    }
                }
            }
    }
    fun retainTabIds(ids: Set<String>) {
        engines.keys.filter { it !in ids }.toList().forEach(::remove)
        saved.keys.filter { it !in ids }.toList().forEach { saved.remove(it); onRemove(it) }
        accessed.keys.retainAll(ids)
        visible = visible.intersect(ids); protected = protected.intersect(ids)
    }
    private fun remove(id: String) {
        val engine = engines.remove(id) ?: return
        checking.remove(id)
        onRemove(id)
        engine.destroy()
    }
    fun destroyAll() {
        engines.keys.toList().forEach(::remove)
        saved.keys.toList().forEach(onRemove)
        saved.clear(); accessed.clear(); checking.clear()
        visible = emptySet(); protected = emptySet()
    }
}
