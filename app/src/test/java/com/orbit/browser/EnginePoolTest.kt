package com.orbit.browser

import com.orbit.browser.browser.engine.*
import com.orbit.browser.browser.tabs.EnginePool
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class EnginePoolTest {
    private class Snapshot(val url: String) : EngineSnapshot
    private class FakeEngine : BrowserEngine {
        override val state = MutableStateFlow(PageState())
        override val events = emptyFlow<EngineEvent>()
        var destroyed = false; var displayed = false; var restored = false
        override fun loadUrl(url: String) { state.value = state.value.copy(url = url) }
        override fun reload() {} ; override fun goBack() {}; override fun goForward() {}
        override fun canGoBack() = false; override fun canGoForward() = false
        override fun evaluateJavascript(script: String) {}
        override fun setDesktopMode(enabled: Boolean) {}; override fun findInPage(query: String) {}
        override fun findNext(forward: Boolean) {}; override fun clearFind() {}
        override fun setVisible(visible: Boolean) { this.displayed = visible }
        override fun saveState() = Snapshot(state.value.url)
        override fun restoreState(snapshot: EngineSnapshot): Boolean {
            restored = true; loadUrl((snapshot as Snapshot).url); return true
        }
        override fun destroy() { destroyed = true }
    }
    @Test fun evictsLruAndRestoresEngineOwnedSnapshot() {
        val created = mutableListOf<FakeEngine>()
        val pool = EnginePool(factory = { _, _ -> FakeEngine().also { created += it } })
        pool.setVisible(setOf("a")); pool.acquire("a", "https://a.com", false)
        pool.setVisible(setOf("b")); pool.acquire("b", "https://b.com", false)
        pool.setVisible(setOf("c")); pool.acquire("c", "https://c.com", false)
        pool.setVisible(setOf("d")); pool.acquire("d", "https://d.com", false)
        assertEquals(3, pool.size); assertTrue(created.first().destroyed)
        pool.setVisible(setOf("a"))
        val a = pool.acquire("a", "https://fallback.com", false) as FakeEngine
        assertTrue(a.restored); assertEquals("https://a.com", a.state.value.url)
    }
    @Test fun protectsBothSplitPanesAndPausesHiddenSessions() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() })
        pool.setVisible(setOf("a", "b"))
        val a = pool.acquire("a", "a", false) as FakeEngine
        val b = pool.acquire("b", "b", false) as FakeEngine
        val c = pool.acquire("c", "c", false) as FakeEngine
        pool.acquire("d", "d", false)
        assertTrue(a.displayed); assertTrue(b.displayed); assertFalse(c.displayed); assertTrue(c.destroyed)
        assertFalse(a.destroyed); assertFalse(b.destroyed)
        pool.setVisible(emptySet()); assertFalse(a.displayed); assertFalse(b.displayed)
    }
    @Test fun closingTabRemovesItsEngineAndSnapshot() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() })
        pool.setVisible(setOf("a")); val a = pool.acquire("a", "a", false) as FakeEngine
        pool.retainTabIds(emptySet()); assertTrue(a.destroyed); assertEquals(0, pool.size)
        val next = pool.acquire("a", "fallback", false) as FakeEngine
        assertFalse(next.restored); assertEquals("fallback", next.state.value.url)
    }
    @Test fun boundsSavedSnapshotsAsWellAsLiveEngines() {
        val pool = EnginePool(capacity = 2, snapshotCapacity = 1, factory = { _, _ -> FakeEngine() })
        for (id in listOf("a", "b", "c", "d")) { pool.setVisible(setOf(id)); pool.acquire(id, id, false) }
        pool.setVisible(setOf("a")); val a = pool.acquire("a", "new", false) as FakeEngine
        assertFalse(a.restored)
        pool.destroyAll(); assertEquals(0, pool.size); assertTrue(a.destroyed)
    }
}
