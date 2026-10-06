package com.takeruf.nagi

import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.tabs.EnginePool
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class EnginePoolTest {
    private class Snapshot(val url: String) : EngineSnapshot
    private class FakeEngine : BrowserEngine {
        override val state = MutableStateFlow(PageState())
        override val events = emptyFlow<EngineEvent>()
        var destroyed = false; var displayed = false; var restored = false
        var loadCount = 0; var reloadCount = 0; var destroyCount = 0
        override fun loadUrl(url: String) { loadCount++; state.value = state.value.copy(url = url) }
        override fun reload() { reloadCount++ } ; override fun goBack() {}; override fun goForward() {}
        override fun canGoBack() = false; override fun canGoForward() = false
        override fun evaluateJavascript(script: String) {}
        override fun setDesktopMode(enabled: Boolean) {}; override fun findInPage(query: String) {}
        override fun findNext(forward: Boolean) {}; override fun clearFind() {}
        override fun setVisible(visible: Boolean) { this.displayed = visible }
        override fun saveState() = Snapshot(state.value.url)
        override fun restoreState(snapshot: EngineSnapshot): Boolean {
            restored = true; loadUrl((snapshot as Snapshot).url); return true
        }
        override fun destroy() { destroyed = true; destroyCount++ }
    }
    @Test fun switchingManyTabsPreservesLivePageWithoutReloadOrRestore() {
        val created = mutableListOf<FakeEngine>()
        val pool = EnginePool(factory = { _, _ -> FakeEngine().also { created += it } })
        val ids = (1..25).map { "tab-$it" }
        val original = ids.associateWith { id ->
            pool.setVisible(setOf(id))
            pool.acquire(id, "https://$id.com", false) as FakeEngine
        }
        original.getValue(ids.first()).state.value = PageState(url = "https://navigated.com", title = "Unsaved page")
        // Hiding the whole browser or moving between Spaces must also retain sessions.
        pool.setVisible(emptySet())
        pool.retainTabIds(ids.toSet())
        for (id in ids.reversed()) {
            pool.setVisible(setOf(id))
            assertSame(original.getValue(id), pool.acquire(id, "https://fallback.com", true))
        }
        assertEquals(ids.size, pool.size)
        assertEquals(ids.size, created.size)
        assertEquals("Unsaved page", original.getValue(ids.first()).state.value.title)
        assertEquals("https://navigated.com", original.getValue(ids.first()).state.value.url)
        created.forEach {
            assertFalse(it.destroyed); assertFalse(it.restored)
            assertEquals(1, it.loadCount); assertEquals(0, it.reloadCount)
        }
    }
    @Test fun keepsBothSplitPanesAndPausesHiddenSessions() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() })
        pool.setVisible(setOf("a", "b"))
        val a = pool.acquire("a", "a", false) as FakeEngine
        val b = pool.acquire("b", "b", false) as FakeEngine
        val c = pool.acquire("c", "c", false) as FakeEngine
        pool.acquire("d", "d", false)
        assertTrue(a.displayed); assertTrue(b.displayed); assertFalse(c.displayed)
        assertFalse(a.destroyed); assertFalse(b.destroyed); assertFalse(c.destroyed)
        pool.setVisible(emptySet()); assertFalse(a.displayed); assertFalse(b.displayed)
        pool.setVisible(setOf("c", "d"))
        assertTrue(c.displayed); assertFalse(a.displayed); assertFalse(b.displayed)
    }
    @Test fun closingOrArchivingTabRemovesOnlyItsEngine() {
        val removed = mutableListOf<String>()
        val pool = EnginePool(factory = { _, _ -> FakeEngine() }, onRemove = { removed += it })
        pool.setVisible(setOf("a"))
        val a = pool.acquire("a", "a", false) as FakeEngine
        val b = pool.acquire("b", "b", false) as FakeEngine
        pool.retainTabIds(setOf("b"))
        assertTrue(a.destroyed); assertFalse(b.destroyed); assertEquals(1, pool.size)
        assertNull(pool.peek("a")); assertSame(b, pool.peek("b"))
        assertEquals(listOf("a"), removed)
        val next = pool.acquire("a", "fallback", false) as FakeEngine
        assertNotSame(a, next); assertFalse(next.restored)
        assertEquals("fallback", next.state.value.url)
    }
    @Test fun disposingDestroysAllSessionsOnceAndAllowsFreshSessions() {
        val created = mutableListOf<FakeEngine>()
        val removed = mutableListOf<String>()
        val pool = EnginePool(factory = { _, _ -> FakeEngine().also { created += it } },
            onRemove = { removed += it })
        pool.setVisible(setOf("a"))
        for (id in listOf("a", "b", "c", "d")) pool.acquire(id, id, false)
        pool.destroyAll(); pool.destroyAll()
        assertEquals(0, pool.size)
        assertEquals(listOf("a", "b", "c", "d"), removed)
        created.forEach { assertTrue(it.destroyed); assertEquals(1, it.destroyCount) }
        val next = pool.acquire("a", "new", false) as FakeEngine
        assertFalse(next.displayed); assertFalse(next.restored)
        assertEquals("new", next.state.value.url)
    }
}
