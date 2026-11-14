package com.takeruf.nagi

import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.ui.browser.newTabShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test

class NewTabShortcutsTest {
    private val left = BrowserTab("left", "space", isPinned = true, splitRightTabId = "right")
    private val right = BrowserTab("right", "space", isPinned = true)

    @Test fun savedPairOccupiesOneSlotAlongsidePinnedTabs() {
        val pins = (1..5).map { BrowserTab("pin-$it", "space", isPinned = true) }
        val shortcuts = newTabShortcuts(pins + listOf(left, right), currentTabId = "new")
        assertEquals(listOf("pin-1", "pin-2", "pin-3", left.id), shortcuts.map { it.tab.id })
        assertEquals(right, shortcuts.last().rightTab)
    }

    @Test fun skipsCurrentTabAndCurrentPairButKeepsPinsWithoutAnOpenPartner() {
        val current = BrowserTab("current", "space", isPinned = true)
        assertEquals(emptyList<String>(), newTabShortcuts(listOf(current), current.id).map { it.tab.id })
        assertEquals(emptyList<String>(), newTabShortcuts(listOf(left, right), right.id).map { it.tab.id })
        assertEquals(listOf(left.id), newTabShortcuts(listOf(left), current.id).map { it.tab.id })
    }
}
