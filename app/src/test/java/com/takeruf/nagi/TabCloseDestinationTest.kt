package com.takeruf.nagi

import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.domain.model.TabCloseDestination
import org.junit.Assert.*
import org.junit.Test

class TabCloseDestinationTest {
    @Test fun returnsTheLiveOpenerBeforeTheNearestNeighbor() {
        val opener = BrowserTab("opener", "space", position = 0)
        val neighbor = BrowserTab("neighbor", "space", position = 9)
        val closing = BrowserTab("closing", "space", position = 10, parentTabId = opener.id)
        assertEquals(opener, TabCloseDestination.next(closing, listOf(opener, neighbor, closing)))
    }
    @Test fun ignoresClosedArchivedAndOtherSpaceTargetsAndFallsBackToTheNearestTab() {
        val closing = BrowserTab("closing", "space", position = 10, parentTabId = "closed")
        val next = BrowserTab("next", "space", position = 8)
        val tabs = listOf(BrowserTab("closed", "space", closedAt = 1),
            BrowserTab("archived", "space", position = 9, archivedAt = 1),
            BrowserTab("other", "other", position = 10), closing, next)
        assertEquals(next, TabCloseDestination.next(closing, tabs))
        assertNull(TabCloseDestination.next(closing, tabs - next))
    }
}
