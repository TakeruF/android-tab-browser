package com.takeruf.nagi

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.takeruf.nagi.ui.sidebar.*
import org.junit.Assert.*
import org.junit.Test

class TabSplitDragStateTest {
    @Test fun wholePairDragReordersInsteadOfSplittingAndRejectsFavoriteTargets() {
        val drag = SidebarDragState()
        drag.bounds("target", SidebarDestination(SidebarSection.TODAY, "c"), Rect(0f, 0f, 200f, 48f), replacementRight = true)
        drag.start(SidebarDragItem("a", "A / B", null,
            pairRight = com.takeruf.nagi.domain.model.BrowserTab("b", "personal")), Offset(50f, 24f))
        assertEquals(SidebarDestination(SidebarSection.TODAY, "c"), drag.destination)
        drag.move(Offset(150f, 30f))
        assertEquals(SidebarDestination(SidebarSection.TODAY, "c", after = true), drag.destination)
        drag.bounds("favorite", SidebarDestination(SidebarSection.FAVORITES, "favorite"), Rect(210f, 0f, 260f, 48f))
        drag.move(Offset(230f, 24f))
        assertNull(drag.destination)
        drag.bounds("pinned", SidebarDestination(SidebarSection.PINNED), Rect(0f, 55f, 200f, 100f))
        drag.move(Offset(50f, 75f))
        assertEquals(SidebarSection.PINNED, drag.destination?.section)
    }
    @Test fun groupedTabsOnlyOfferReorderingAcrossTheWholeRow() {
        for (section in listOf(SidebarSection.TODAY, SidebarSection.PINNED)) {
            val drag = SidebarDragState()
            drag.bounds("group-member", SidebarDestination(section, "b"), Rect(0f, 0f, 100f, 48f), canSplit = false)
            drag.start(SidebarDragItem("a", "A", null), Offset(25f, 20f))
            for (x in listOf(25f, 75f)) {
                drag.move(Offset(x, 20f))
                assertEquals(SidebarDestination(section, "b"), drag.destination)
                drag.move(Offset(x, 28f))
                assertEquals(SidebarDestination(section, "b", after = true), drag.destination)
            }
        }
    }

    @Test fun groupedTabCenterReplacesItsPaneWhileEdgesReorder() {
        for (section in listOf(SidebarSection.TODAY, SidebarSection.PINNED)) {
            for (toRight in listOf(false, true)) {
                val drag = SidebarDragState()
                drag.bounds("member", SidebarDestination(section, "b"), Rect(0f, 0f, 100f, 48f),
                    canSplit = false, replacementRight = toRight)
                drag.start(SidebarDragItem("a", "A", null), Offset(25f, 24f))
                val replacement = SidebarDestination(if (toRight) SidebarSection.SPLIT_RIGHT else SidebarSection.SPLIT_LEFT, "b")
                assertEquals(replacement, drag.destination)
                drag.move(Offset(75f, 24f))
                assertEquals(replacement, drag.destination)
                drag.move(Offset(50f, 5f))
                assertEquals(SidebarDestination(section, "b"), drag.destination)
                drag.move(Offset(50f, 43f))
                assertEquals(SidebarDestination(section, "b", after = true), drag.destination)
                drag.start(SidebarDragItem("b", "B", null), Offset(50f, 24f))
                assertEquals(section, drag.destination?.section)
                drag.start(SidebarDragItem("favorite", "Favorite", null, favorite = true), Offset(50f, 24f))
                assertEquals(section, drag.destination?.section)
                drag.cancel()
                assertNull(drag.destination)
            }
        }
    }

    @Test fun centerCombinesWhileEdgesReorderInBothSections() {
        for (section in listOf(SidebarSection.TODAY, SidebarSection.PINNED)) {
            val drag = SidebarDragState()
            drag.bounds("target", SidebarDestination(section, "b"), Rect(0f, 0f, 200f, 48f))
            drag.start(SidebarDragItem("a", "A", null), Offset(100f, 24f))
            assertEquals(SidebarDestination(SidebarSection.SPLIT_TAB, "b", after = true), drag.destination)
            drag.move(Offset(50f, 24f))
            assertEquals(SidebarDestination(SidebarSection.SPLIT_TAB, "b"), drag.destination)
            drag.move(Offset(150f, 24f))
            assertEquals(SidebarDestination(SidebarSection.SPLIT_TAB, "b", after = true), drag.destination)
            drag.move(Offset(100f, 5f))
            assertEquals(SidebarDestination(section, "b"), drag.destination)
            drag.move(Offset(100f, 43f))
            assertEquals(SidebarDestination(section, "b", after = true), drag.destination)
            drag.move(Offset(300f, 24f))
            assertNull(drag.destination)
        }
    }

    @Test fun selfAndFavoriteDropsDoNotCombineAndCancelClearsTarget() {
        val drag = SidebarDragState()
        drag.bounds("target", SidebarDestination(SidebarSection.TODAY, "a"), Rect(0f, 0f, 200f, 48f))
        drag.start(SidebarDragItem("a", "A", null), Offset(100f, 24f))
        assertEquals(SidebarSection.TODAY, drag.destination?.section)
        drag.start(SidebarDragItem("favorite", "Favorite", null, favorite = true), Offset(100f, 24f))
        assertEquals(SidebarSection.TODAY, drag.destination?.section)
        drag.cancel()
        assertNull(drag.destination)
        assertNull(drag.item)
    }
}
