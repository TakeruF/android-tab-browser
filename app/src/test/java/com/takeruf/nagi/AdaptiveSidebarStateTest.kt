package com.takeruf.nagi

import com.takeruf.nagi.ui.sidebar.AdaptiveSidebarState
import org.junit.Assert.*
import org.junit.Test

class AdaptiveSidebarStateTest {
    @Test fun narrowWindowTemporarilyCollapsesAndRestoresPreference() {
        val wide = AdaptiveSidebarState().forWidth(1000f)
        val narrow = wide.forWidth(480f)
        assertTrue(narrow.isCollapsed(false))
        assertFalse(narrow.forWidth(1000f).isCollapsed(false))
        assertTrue(narrow.forWidth(1000f).isCollapsed(true))
    }

    @Test fun hysteresisAvoidsTogglingNearBoundary() {
        var state = AdaptiveSidebarState().forWidth(600f)
        assertFalse(state.compact)
        state = state.forWidth(599f)
        for (width in listOf(600f, 620f, 639f, 599f)) {
            state = state.forWidth(width)
            assertTrue(state.compact)
        }
        assertFalse(state.forWidth(640f).compact)
    }

    @Test fun manualCompactOverrideIsClearedForNextResizeCycle() {
        val expanded = AdaptiveSidebarState().forWidth(480f).copy(compactCollapsed = false)
        assertFalse(expanded.forWidth(550f).isCollapsed(true))
        val wide = expanded.forWidth(800f)
        assertTrue(wide.isCollapsed(true))
        assertTrue(wide.forWidth(480f).isCollapsed(false))
    }
}
