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

    @Test fun sameWidthHasTheSameModeFromEitherDirectionAndSavedState() {
        for (width in listOf(320f, 599f, 599.5f, 600f, 620f, 639f, 640f, 800f, 1280f)) {
            val expected = width < 600f
            for (previous in listOf(AdaptiveSidebarState(), AdaptiveSidebarState(compact = true),
                AdaptiveSidebarState(compact = true, compactCollapsed = false))) {
                assertEquals("width=$width, previous=$previous", expected, previous.forWidth(width).compact)
            }
            assertEquals(expected, AdaptiveSidebarState().forWidth(360f).forWidth(width).compact)
            assertEquals(expected, AdaptiveSidebarState().forWidth(1280f).forWidth(width).compact)
        }
    }

    @Test fun manualCompactOverrideIsClearedForNextResizeCycle() {
        val expanded = AdaptiveSidebarState().forWidth(480f).copy(compactCollapsed = false)
        assertFalse(expanded.forWidth(550f).isCollapsed(true))
        val wide = expanded.forWidth(800f)
        assertTrue(wide.isCollapsed(true))
        assertTrue(wide.forWidth(480f).isCollapsed(false))
    }
}
