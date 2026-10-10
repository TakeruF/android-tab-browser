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
        var safeToSuspend = true
        var deferredCheck: ((Boolean) -> Unit)? = null
        var deferCheck = false
        override fun canSuspend(result: (Boolean) -> Unit) { if (deferCheck) deferredCheck = result else result(safeToSuspend) }
        override fun loadUrl(url: String) { loadCount++; state.value = state.value.copy(url = url, visitBlockingExceptionSite =
            com.takeruf.nagi.browser.blocking.blockingSite(url)?.takeIf { it in visitSites }) }
        private var visitSites = emptySet<String>()
        override fun configureVisitBlockingExceptions(sites: Set<String>) {
            visitSites = sites
            state.value = state.value.copy(visitBlockingExceptionSite =
                com.takeruf.nagi.browser.blocking.blockingSite(state.value.url)?.takeIf { it in sites })
        }
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

    @Test fun oldBackgroundPagesSleepAndRestoreTheirNavigatedHistory() {
        var now = 0L
        val pool = EnginePool(factory = { _, _ -> FakeEngine() }, clock = { now })
        val a = pool.acquire("a", "https://a.com", false) as FakeEngine
        a.state.value = PageState(url = "https://a.com/next", desktopMode = true)
        pool.setVisitBlockingPaused(a.state.value.url, true)
        now++
        pool.setVisible(setOf("b"))
        val b = pool.acquire("b", "https://b.com", false)
        pool.suspendBackground(maxLive = 1)
        assertFalse(a.destroyed)
        now += 10 * 60_000L
        pool.suspendBackground(maxLive = 1)
        assertTrue(a.destroyed); assertSame(b, pool.peek("b"))
        assertEquals(setOf("a"), pool.suspendedIds)
        pool.setVisible(setOf("a"))
        val restored = pool.acquire("a", "https://stale.com", false) as FakeEngine
        assertTrue(restored.restored)
        assertEquals("https://a.com/next", restored.state.value.url)
        assertEquals("a.com", restored.state.value.visitBlockingExceptionSite)
        assertTrue(pool.suspendedIds.isEmpty())
    }
    @Test fun siteVisitSharesNewArticleTabsAndExpiresOnlyAfterLastTabLeaves() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() })
        val home = pool.acquire("home", "https://www.qq.com", false) as FakeEngine
        pool.setVisitBlockingPaused(home.state.value.url, true)
        assertEquals("qq.com", home.state.value.visitBlockingExceptionSite)
        val article = pool.acquire("article", "https://news.qq.com/a/123", false) as FakeEngine
        assertEquals("qq.com", article.state.value.visitBlockingExceptionSite)
        val unrelated = pool.acquire("other", "https://qq.com.evil.com", false) as FakeEngine
        assertNull(unrelated.state.value.visitBlockingExceptionSite)
        home.loadUrl("https://example.com")
        pool.onPageChanged("home", home.state.value.url)
        assertEquals("qq.com", article.state.value.visitBlockingExceptionSite)
        // A background article not acquired yet must also keep the visit alive.
        pool.retainTabIds(setOf("home", "background"), mapOf("background" to "https://sports.qq.com/a/456"))
        val background = pool.acquire("background", "https://sports.qq.com/a/456", false) as FakeEngine
        assertEquals("qq.com", background.state.value.visitBlockingExceptionSite)
        pool.retainTabIds(setOf("home"))
        val fresh = pool.acquire("fresh", "https://news.qq.com/a/789", false) as FakeEngine
        assertNull(fresh.state.value.visitBlockingExceptionSite)
    }
    @Test fun explicitResumeAndWorkspaceResetEndSharedVisitExceptions() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() })
        val first = pool.acquire("first", "https://qq.com", false) as FakeEngine
        val second = pool.acquire("second", "https://news.qq.com/a/123", false) as FakeEngine
        pool.setVisitBlockingPaused(first.state.value.url, true)
        pool.setVisitBlockingPaused(second.state.value.url, false)
        assertNull(first.state.value.visitBlockingExceptionSite)
        assertNull(second.state.value.visitBlockingExceptionSite)
        pool.setVisitBlockingPaused(first.state.value.url, true)
        pool.destroyAll()
        assertNull(pool.acquire("first", "https://qq.com", false).state.value.visitBlockingExceptionSite)
    }
    @Test fun displayedProtectedAndUnsafePagesCannotBeSuspended() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() }, clock = { 0 })
        pool.setVisible(setOf("left", "right")); pool.protect(setOf("left", "right", "protected"))
        val pages = listOf("left", "right", "protected", "form", "safe").associateWith { pool.acquire(it, it, false) as FakeEngine }
        pages.getValue("form").safeToSuspend = false
        pool.suspendBackground(maxLive = 0, idleMillis = 0)
        assertEquals(setOf("safe"), pool.suspendedIds)
        pages.filterKeys { it != "safe" }.values.forEach { assertFalse(it.destroyed) }
        pool.setVisible(emptySet())
        pool.suspendBackground(maxLive = 0, idleMillis = 0)
        assertFalse(pages.getValue("left").destroyed)
    }
    @Test fun returningToTabDuringEligibilityCheckKeepsItLive() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() }, clock = { 0 })
        val a = pool.acquire("a", "a", false) as FakeEngine
        a.deferCheck = true
        pool.suspendBackground(maxLive = 0, idleMillis = 0)
        pool.setVisible(setOf("a"))
        a.deferredCheck!!(true)
        assertFalse(a.destroyed)
        assertTrue(pool.suspendedIds.isEmpty())
    }
    @Test fun closingSleepingTabDiscardsSnapshotAndCreatesFreshPageOnReopen() {
        val pool = EnginePool(factory = { _, _ -> FakeEngine() }, clock = { 0 })
        pool.acquire("a", "old", false)
        pool.suspendBackground(maxLive = 0, idleMillis = 0)
        pool.retainTabIds(emptySet())
        val next = pool.acquire("a", "new", false) as FakeEngine
        assertFalse(next.restored); assertEquals("new", next.state.value.url)
    }
}
