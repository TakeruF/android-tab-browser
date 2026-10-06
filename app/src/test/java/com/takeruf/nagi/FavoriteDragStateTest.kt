package com.takeruf.nagi

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.takeruf.nagi.ui.sidebar.*
import org.junit.Assert.*
import org.junit.Test

class FavoriteDragStateTest {
    private fun state() = SidebarDragState().apply {
        syncFavorites(listOf("a", "b", "c", "d"))
        bounds("grid", SidebarDestination(SidebarSection.FAVORITES), Rect(0f, 0f, 160f, 120f))
        listOf("a", "b", "c", "d").forEachIndexed { index, id ->
            val x = (index % 2) * 80f
            val y = (index / 2) * 60f
            bounds(id, SidebarDestination(SidebarSection.FAVORITES, id), Rect(x, y, x + 74f, y + 54f))
        }
        start(SidebarDragItem("a", "A", null, favorite = true), Offset(15f, 20f), Rect(0f, 0f, 74f, 54f))
    }

    @Test fun reordersAcrossRowsWithoutOscillatingAndCanReverse() {
        val drag = state()
        repeat(5) { drag.move(Offset(110f, 80f)) }
        assertEquals(listOf("b", "c", "d", "a"), drag.favoriteOrder)
        assertEquals(SidebarDestination(SidebarSection.FAVORITES, "d", true), drag.destination)
        drag.move(Offset(110f, 20f))
        assertEquals(listOf("b", "a", "c", "d"), drag.favoriteOrder)
        assertEquals(SidebarDestination(SidebarSection.FAVORITES, "c"), drag.destination)
        drag.move(Offset(15f, 20f))
        assertEquals(listOf("a", "b", "c", "d"), drag.favoriteOrder)
        assertEquals(Offset(15f, 20f), drag.grabOffset)
        assertEquals(Offset(74f, 54f), drag.sourceSize)
    }

    @Test fun gapsUseNearestSlotAndCancelOrLeavingGridDiscardsPreview() {
        val drag = state()
        drag.move(Offset(78f, 80f))
        assertEquals(listOf("b", "c", "d", "a"), drag.favoriteOrder)
        drag.move(Offset(200f, 200f))
        assertNull(drag.favoriteOrder)
        assertNull(drag.destination)
        drag.move(Offset(110f, 80f))
        drag.cancel()
        assertNull(drag.favoriteOrder)
        assertNull(drag.item)
    }

    @Test fun committedPreviewSurvivesReleaseUntilSavedOrderArrives() {
        val drag = state()
        drag.move(Offset(110f, 80f))
        drag.commitFavoriteOrder()
        assertEquals(Offset(80f, 60f), drag.favoriteLanding!!.topLeft)
        drag.cancel()
        drag.syncFavorites(listOf("a", "b", "c", "d"))
        assertEquals(listOf("b", "c", "d", "a"), drag.favoriteOrder)
        drag.syncFavorites(listOf("b", "c", "d", "a"))
        assertNull(drag.favoriteOrder)
    }
}
