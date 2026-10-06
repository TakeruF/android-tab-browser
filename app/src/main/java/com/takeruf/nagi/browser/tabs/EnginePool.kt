package com.takeruf.nagi.browser.tabs

import com.takeruf.nagi.browser.engine.*

/** Main-thread confined sessions. Open tabs keep their live page until explicitly removed. */
class EnginePool(
    private val factory: (String, Boolean) -> BrowserEngine,
    private val onCreate: (String, BrowserEngine) -> Unit = { _, _ -> },
    private val onRemove: (String) -> Unit = {},
) {
    private val engines = LinkedHashMap<String, BrowserEngine>()
    private var visible = emptySet<String>()
    val size get() = engines.size
    fun peek(id: String): BrowserEngine? = engines[id]

    fun setVisible(ids: Set<String>) {
        visible = ids
        engines.forEach { (id, engine) -> engine.setVisible(id in ids) }
    }
    fun acquire(id: String, url: String, desktop: Boolean): BrowserEngine {
        engines[id]?.let { return it }
        val engine = factory(id, desktop)
        engines[id] = engine
        onCreate(id, engine)
        engine.loadUrl(url)
        engine.setVisible(id in visible)
        return engine
    }
    fun retainTabIds(ids: Set<String>) {
        engines.keys.filter { it !in ids }.toList().forEach(::remove)
        visible = visible.intersect(ids)
    }
    private fun remove(id: String) {
        val engine = engines.remove(id) ?: return
        onRemove(id)
        engine.destroy()
    }
    fun destroyAll() {
        engines.keys.toList().forEach(::remove)
        visible = emptySet()
    }
}
