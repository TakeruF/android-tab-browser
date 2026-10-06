package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.domain.model.Bookmark
import com.takeruf.nagi.data.room.BookmarkEntity
import com.takeruf.nagi.data.room.entity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class SidebarDragTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var alpha: String
    private lateinit var beta: String
    private lateinit var gamma: String
    private var originalFavorites = emptyList<BookmarkEntity>()
    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            // Earlier browsing tests add favorites. Keep the drag geometry deterministic,
            // then restore those entries rather than accumulating fixture state.
            originalFavorites = container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }
            originalFavorites.forEach { container.library.removeBookmark(it.id) }
            listOf("Gmail", "ChatGPT", "GitHub").forEachIndexed { index, title ->
                container.workspace.dao.putBookmark(Bookmark("drag-fixture-$index", "https://example.com/$title", title,
                    isFavorite = true, createdAt = index.toLong()).entity())
            }
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create("Research")
            spaceId = container.settings.settings.first().selectedSpaceId
            alpha = container.tabs.create(spaceId, "https://example.com/alpha", false)
            beta = container.tabs.create(spaceId, "https://example.com/beta", false)
            gamma = container.tabs.create(spaceId, "https://example.com/gamma", false)
            listOf(alpha to "Design references", beta to "Reading list", gamma to "Project notes").forEach { (id, title) ->
                container.tabs.updatePage(id, "https://example.com/$id", title, null)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tab Project notes").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() = runBlocking {
        container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }.forEach { container.library.removeBookmark(it.id) }
        originalFavorites.forEach { container.workspace.dao.putBookmark(it) }
        if (::spaceId.isInitialized) container.spaces.delete(spaceId)
        if (::beta.isInitialized && container.workspace.dao.tab(beta)?.spaceId == "work") container.tabs.close(beta)
    }
    private fun tab(title: String) = compose.onNodeWithContentDescription("Tab $title")
    private fun saveScreen(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun drag(source: SemanticsNodeInteraction, destination: SemanticsNodeInteraction, after: Boolean = false, cancel: Boolean = false) {
        val bounds = source.fetchSemanticsNode().boundsInRoot
        val target = destination.fetchSemanticsNode().boundsInRoot
        val end = Offset(target.center.x - bounds.left, (if (after) target.bottom - 5f else target.top + 5f) - bounds.top)
        source.performTouchInput {
            down(Offset(bounds.width * 0.45f, bounds.height / 2f)); advanceEventTime(650)
            val start = Offset(bounds.width * 0.45f, bounds.height / 2f)
            for (i in 1..16) moveTo(start + (end - start) * (i / 16f), delayMillis = 20)
            if (cancel) cancel() else up()
        }
    }
    @Test fun droppingOnAnotherTabCombinesThosePagesAndPreservesOrder() {
        val original = runBlocking { container.workspace.dao.tabs(spaceId).map { it.id to it.isPinned } }
        fun centerDrop(cancelled: Boolean = false, mouse: Boolean = false, toRight: Boolean = true) {
            val source = compose.onNodeWithTag("sidebar-tab-$beta")
            val from = source.fetchSemanticsNode().boundsInRoot
            val to = compose.onNodeWithTag("sidebar-tab-$alpha").fetchSemanticsNode().boundsInRoot
            val end = Offset(if (toRight) to.center.x else to.left + to.width * 0.25f, to.center.y) - from.topLeft
            if (mouse) source.performMouseInput {
                moveTo(center); press(); moveTo(end, delayMillis = 100); release()
            } else {
                source.performTouchInput {
                    down(center); advanceEventTime(650); moveTo(end, delayMillis = 100)
                }
                compose.onNodeWithTag("sidebar-split-drop-$alpha").assertIsDisplayed()
                val highlight = compose.onNodeWithTag("sidebar-split-drop-$alpha").fetchSemanticsNode().boundsInRoot
                val preview = compose.onNodeWithTag("tab-drag-preview").fetchSemanticsNode().boundsInRoot
                assertTrue("The split highlight fills the row", highlight.height >= from.height - 1f)
                assertEquals(to.width / 2f, highlight.width, 1f)
                assertEquals(if (toRight) to.center.x else to.left, highlight.left, 1f)
                assertEquals(from.width, preview.width, 1f)
                assertEquals((from.left + end.x - from.width / 2f).coerceAtLeast(0f), preview.left, 1f)
                assertTrue("The raised card leaves the rounded destination visible", preview.bottom < to.bottom - 1f)
                if (!cancelled) saveScreen(if (toRight) "tab-on-tab-hover" else "tab-on-tab-hover-left")
                source.performTouchInput { if (cancelled) cancel() else up() }
            }
        }
        centerDrop(cancelled = true)
        compose.onNodeWithTag("sidebar-split-group").assertDoesNotExist()
        centerDrop()
        compose.onNodeWithTag("browser-pane-left-$alpha").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-split-group").assertIsDisplayed()
        assertEquals(original, runBlocking { container.workspace.dao.tabs(spaceId).map { it.id to it.isPinned } })
        saveScreen("tab-on-tab-split")
        // Existing members offer replacement previews; cancelling preserves the pair.
        for (id in listOf(alpha, beta)) {
            val source = compose.onNodeWithTag("sidebar-tab-$gamma")
            val from = source.fetchSemanticsNode().boundsInRoot
            val to = compose.onNodeWithTag("sidebar-tab-$id").fetchSemanticsNode().boundsInRoot
            source.performTouchInput {
                down(center); advanceEventTime(650); moveTo(to.center - from.topLeft, delayMillis = 100)
            }
            compose.onNodeWithTag("tab-drag-preview").assertExists()
            compose.onNodeWithTag("sidebar-split-drop-$id").assertDoesNotExist()
            compose.onNodeWithTag("sidebar-replace-drop-$id").assertIsDisplayed()
            saveScreen("grouped-tab-drop-$id")
            source.performTouchInput { cancel() }
            source.performMouseInput {
                moveTo(center); press(); moveTo(to.center - from.topLeft, delayMillis = 100)
            }
            compose.onNodeWithTag("sidebar-split-drop-$id").assertDoesNotExist()
            compose.onNodeWithTag("sidebar-replace-drop-$id").assertIsDisplayed()
            source.performMouseInput { cancel() }
            compose.onNodeWithTag("browser-pane-left-$alpha").assertIsDisplayed()
            compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        }
    }

    @Test fun droppingOnSplitMembersReplacesOnlyThatSideAndRestoresAfterRecreation() {
        val original = runBlocking { container.workspace.dao.tabs(spaceId).map { it.id to it.isPinned } }
        fun dropCenter(sourceId: String, targetId: String, mouse: Boolean) {
            val source = compose.onNodeWithTag("sidebar-tab-$sourceId")
            val from = source.fetchSemanticsNode().boundsInRoot
            val to = compose.onNodeWithTag("sidebar-tab-$targetId").fetchSemanticsNode().boundsInRoot
            val end = to.center - from.topLeft
            if (mouse) source.performMouseInput {
                moveTo(center); press(); moveTo(end, delayMillis = 100); release()
            } else {
                source.performTouchInput { down(center); advanceEventTime(650); moveTo(end, delayMillis = 100) }
                compose.onNodeWithTag("sidebar-replace-drop-$targetId").assertIsDisplayed()
                compose.onNodeWithText("Release to replace this tab").assertDoesNotExist()
                saveScreen("split-replace-hover")
                source.performTouchInput { up() }
            }
            compose.waitForIdle()
        }
        dropCenter(beta, alpha, mouse = true)
        dropCenter(gamma, beta, mouse = false)
        compose.onNodeWithTag("browser-pane-left-$alpha").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$gamma").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-tab-$beta").assertExists()
        // Replacing a saved pair also works while a standalone tab is selected.
        compose.onNodeWithTag("sidebar-tab-$beta").performClick()
        dropCenter(beta, alpha, mouse = true)
        compose.onNodeWithTag("browser-pane-left-$beta").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$gamma").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-tab-$alpha").assertExists()
        assertEquals(original, runBlocking { container.workspace.dao.tabs(spaceId).map { it.id to it.isPinned } })
        saveScreen("split-replaced")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-right-$gamma").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("browser-pane-left-$beta").assertIsDisplayed()
    }

    @Test fun dragTabsToEitherHalfSplitsReplacesAndSwapsWithoutMovingTabs() {
        val originalLeft = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        drag(compose.onNodeWithTag("sidebar-tab-$beta"), compose.onNodeWithTag("split-drop-right"), cancel = true)
        compose.onNodeWithTag("browser-pane-right-$beta").assertDoesNotExist()
        drag(compose.onNodeWithTag("sidebar-tab-$beta"), compose.onNodeWithTag("split-drop-right"))
        compose.onNodeWithTag("browser-pane-left-$originalLeft").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        saveScreen("tab-drop-right-split")
        drag(compose.onNodeWithTag("sidebar-tab-$alpha"), compose.onNodeWithTag("split-drop-left"))
        compose.waitUntil { compose.onAllNodesWithTag("browser-pane-left-$alpha").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        // Dropping the current right tab on the left swaps both panes.
        drag(compose.onNodeWithTag("sidebar-tab-$beta"), compose.onNodeWithTag("split-drop-left"))
        compose.waitUntil { compose.onAllNodesWithTag("browser-pane-left-$beta").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser-pane-right-$alpha").assertIsDisplayed()
        // A mouse drag can replace the right pane without a long press.
        val source = compose.onNodeWithTag("sidebar-tab-$gamma").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("split-drop-right").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("sidebar-tab-$gamma").performMouseInput {
            moveTo(center); press()
            val end = target.center - source.topLeft
            for (i in 1..16) moveTo(center + (end - center) * (i / 16f), delayMillis = 20)
            release()
        }
        compose.waitUntil { compose.onAllNodesWithTag("browser-pane-right-$gamma").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser-pane-left-$beta").assertIsDisplayed()
        assertEquals(spaceId, runBlocking { container.workspace.dao.tab(gamma)!!.spaceId })
        assertFalse(runBlocking { container.workspace.dao.tab(gamma)!!.isPinned })
        saveScreen("tab-drop-mouse-split")
    }

    @Test fun splitSidebarGroupsPagesInPaneOrderAndFocusesWithoutReplacingThem() {
        val left = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        drag(compose.onNodeWithTag("sidebar-tab-$beta"), compose.onNodeWithTag("split-drop-right"))
        compose.onNodeWithTag("sidebar-split-group").assertIsDisplayed()
        val leftBounds = compose.onNodeWithTag("sidebar-tab-$left").fetchSemanticsNode().boundsInRoot
        val rightBounds = compose.onNodeWithTag("sidebar-tab-$beta").fetchSemanticsNode().boundsInRoot
        assertEquals(leftBounds.top, rightBounds.top, 1f)
        assertEquals(leftBounds.width, rightBounds.width, 1f)
        assertTrue(leftBounds.right < rightBounds.left)
        compose.onNode(isSelectable() and hasAnyAncestor(hasTestTag("sidebar-tab-$beta")), useUnmergedTree = true).assertIsSelected().performClick()
        compose.onNodeWithTag("browser-pane-left-$left").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        compose.onNode(isSelectable() and hasAnyAncestor(hasTestTag("sidebar-tab-$left")), useUnmergedTree = true).performClick().assertIsSelected()
        compose.onNode(isSelectable() and hasAnyAncestor(hasTestTag("sidebar-tab-$beta")), useUnmergedTree = true).assertIsNotSelected()
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        // The group follows the left tab's section even when only the right tab is pinned.
        runBlocking { container.tabs.togglePin(beta) }
        compose.onNodeWithTag("sidebar-split-group").assertIsDisplayed()
        compose.onAllNodesWithTag("sidebar-tab-$beta").assertCountEquals(1)
        saveScreen("split-sidebar-light")
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK, sidebarWidth = 220f) } }
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("sidebar").captureToImage().toPixelMap()[5, 100].luminance() < 0.2f &&
                compose.onNodeWithTag("sidebar").fetchSemanticsNode().boundsInRoot.width < 450f
        }
        compose.onNodeWithTag("sidebar-split-group").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-tab-$left").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-tab-$beta").assertIsDisplayed()
        saveScreen("split-sidebar-dark-narrow")
        runBlocking { container.tabs.close(beta) }
        compose.waitUntil { compose.onAllNodesWithTag("sidebar-split-group").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("sidebar-tab-$left").assertIsDisplayed()
        compose.onNodeWithTag("browser-pane-left-$left").assertIsDisplayed()
    }

    @Test fun dropOnLeftStartsSplitWithOriginalPageOnRight() {
        val originalLeft = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        drag(compose.onNodeWithTag("sidebar-tab-$alpha"), compose.onNodeWithTag("split-drop-left"))
        compose.waitUntil { compose.onAllNodesWithTag("browser-pane-left-$alpha").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("browser-pane-right-$originalLeft").assertIsDisplayed()
        saveScreen("tab-drop-left-split")
    }

    private fun selectedSpace() = runBlocking { container.settings.settings.first().selectedSpaceId }
    private fun swipeSpace(node: SemanticsNodeInteraction, forward: Boolean, canceled: Boolean = false) {
        node.performTouchInput {
            val start = Offset(width * (if (forward) 0.8f else 0.2f), height / 2f)
            val end = Offset(width * (if (forward) 0.2f else 0.8f), height / 2f)
            down(start)
            for (i in 1..10) moveTo(start + (end - start) * (i / 10f), delayMillis = 20)
            if (canceled) cancel() else up()
        }
    }
    @Test fun tappingSpaceSlidesContentAndCanBeReversedBeforeSettling() {
        val previous = runBlocking { container.workspace.dao.spaces().let { it[it.indexOfFirst { space -> space.id == spaceId } - 1] } }
        // Other classes retain QA Spaces. Reveal this pair before freezing animations.
        compose.onNodeWithContentDescription("Switch to Research").performScrollTo()
        val before = tab("Project notes").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("Switch to ${previous.name}").performClick()
            compose.waitUntil { selectedSpace() == previous.id }
            compose.mainClock.advanceTimeBy(80)
            val during = tab("Project notes").fetchSemanticsNode().boundsInRoot
            assertTrue("A tap should slide the outgoing Space", during.left > before.left + 20f)
            compose.onNodeWithContentDescription("Switch to Research").performClick()
            compose.waitUntil { selectedSpace() == spaceId }
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitForIdle()
        assertEquals(before.left, tab("Project notes").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals(spaceId, selectedSpace())
    }

    @Test fun spaceContentTracksFingerWhileFavoritesStayFixedAndCancelReturns() {
        val sidebar = compose.onNodeWithTag("sidebar")
        compose.waitForIdle()
        fun backgroundPixel(): Int = sidebar.captureToImage().asAndroidBitmap().let {
            it.getPixel(4, it.height / 2)
        }
        val colorBefore = backgroundPixel()
        val before = tab("Project notes").fetchSemanticsNode().boundsInRoot
        val favorites = compose.onNodeWithTag("favorites-grid").fetchSemanticsNode().boundsInRoot
        sidebar.performTouchInput {
            val start = Offset(width * 0.3f, height * 0.65f)
            down(start)
            moveTo(start + Offset(width * 0.3f, 0f), delayMillis = 100)
        }
        compose.waitForIdle()
        val during = tab("Project notes").fetchSemanticsNode().boundsInRoot
        assertTrue("Space tabs must follow the finger before release", during.left > before.left + 20f)
        assertEquals("Shared favorites must stay stationary", favorites,
            compose.onNodeWithTag("favorites-grid").fetchSemanticsNode().boundsInRoot)
        assertNotEquals("The gradient must blend toward the adjacent Space before release", colorBefore, backgroundPixel())
        assertEquals("Dragging does not persist a selection", spaceId, selectedSpace())
        saveScreen("sidebar-space-mid-swipe")
        sidebar.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(spaceId, selectedSpace())
        assertEquals(before.left, tab("Project notes").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals("Cancelled swipe restores the original gradient", colorBefore, backgroundPixel())
    }

    @Test fun horizontalSwipeSwitchesSpacesWithoutCreatingTabsAndPersists() {
        val previous = runBlocking { container.workspace.dao.spaces().let { it[it.indexOfFirst { space -> space.id == spaceId } - 1].id } }
        val count = runBlocking { container.workspace.dao.tabs(spaceId).size }
        swipeSpace(compose.onNodeWithTag("new-tab-button"), forward = false)
        compose.waitUntil { selectedSpace() == previous }
        compose.onNodeWithTag("new-tab-button").assertIsDisplayed()
        swipeSpace(compose.onNodeWithTag("new-tab-button"), forward = true)
        compose.waitUntil { selectedSpace() == spaceId }
        assertEquals(count, runBlocking { container.workspace.dao.tabs(spaceId).size })
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithContentDescription("Tab Project notes").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(spaceId, selectedSpace())
        saveScreen("sidebar-space-swipe")
    }
    @Test fun quickRowSwipeWorksButCancelVerticalMotionAndEndpointDoNotSwitch() {
        swipeSpace(tab("Project notes"), forward = false, canceled = true)
        compose.waitForIdle()
        assertEquals(spaceId, selectedSpace())
        tab("Reading list").performTouchInput {
            down(Offset(width * 0.45f, height / 2f))
            moveBy(Offset(0f, -80f), delayMillis = 100)
            up()
        }
        compose.waitForIdle()
        assertEquals(spaceId, selectedSpace())
        swipeSpace(tab("Project notes"), forward = true)
        compose.waitForIdle()
        assertEquals(spaceId, selectedSpace()) // Last Space has no next neighbor.
        swipeSpace(tab("Project notes"), forward = false)
        compose.waitUntil { selectedSpace() != spaceId }
        assertEquals(spaceId, runBlocking { container.workspace.dao.tab(gamma)!!.spaceId })
    }
    @Test fun collapsedSidebarAlsoSwitchesSpaces() {
        runBlocking { container.settings.update { it.copy(sidebarCollapsed = true) } }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Expand sidebar").fetchSemanticsNodes().isNotEmpty() }
        swipeSpace(compose.onNodeWithTag("sidebar"), forward = false)
        compose.waitUntil { selectedSpace() != spaceId }
        swipeSpace(compose.onNodeWithTag("sidebar"), forward = true)
        compose.waitUntil { selectedSpace() == spaceId }
    }

    @Test fun favoritesFillAvailableWidthAndReflowWithSidebarWidth() {
        fun favorite(name: String) = compose.onNodeWithContentDescription("Favorite $name")
        runBlocking { container.library.removeBookmark("drag-fixture-2") }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite GitHub").fetchSemanticsNodes().isEmpty() }
        val grid = compose.onNodeWithTag("favorites-grid").fetchSemanticsNode().boundsInRoot
        val first = favorite("Gmail").fetchSemanticsNode().boundsInRoot
        val second = favorite("ChatGPT").fetchSemanticsNode().boundsInRoot
        assertEquals(grid.left, first.left, 1f)
        assertEquals(grid.right, second.right, 1f)
        assertEquals(first.width, second.width, 1f)
        saveScreen("favorites-two-fill")
        runBlocking {
            for (index in 2..4) container.workspace.dao.putBookmark(Bookmark("drag-fixture-$index",
                "https://example.com/$index", "Grid $index", isFavorite = true, createdAt = index.toLong()).entity())
        }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite Grid 4").fetchSemanticsNodes().isNotEmpty() }
        for ((width, columns) in listOf(220f to 2, 264f to 3, 380f to 4)) {
            runBlocking { container.settings.update { it.copy(sidebarWidth = width) } }
            compose.waitForIdle()
            val rows = listOf("Gmail", "ChatGPT", "Grid 2", "Grid 3", "Grid 4").map {
                favorite(it).fetchSemanticsNode().boundsInRoot
            }
            for (index in 0 until columns) assertEquals(rows[0].top, rows[index].top, 1f)
            assertTrue(rows[columns].top > rows[0].bottom)
            for (row in rows) assertEquals(rows[0].width, row.width, 1f)
            saveScreen("favorites-${columns}-columns")
        }
    }

    @Test fun favoriteMouseDragPreviewsSlotsTracksGrabPointAndPersists() {
        fun favorite(name: String) = compose.onNodeWithContentDescription("Favorite $name")
        val sidebar = compose.onNodeWithTag("sidebar")
        val host = sidebar.fetchSemanticsNode().boundsInRoot
        val first = favorite("Gmail").fetchSemanticsNode().boundsInRoot
        val last = favorite("GitHub").fetchSemanticsNode().boundsInRoot
        val grab = first.topLeft + Offset(first.width * 0.3f, first.height * 0.4f)
        val end = last.center
        sidebar.performMouseInput {
            moveTo(grab - host.topLeft); press()
            moveTo(end - host.topLeft)
        }
        compose.waitForIdle()
        val preview = compose.onNodeWithTag("favorite-drag-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, preview.width, 1f)
        assertEquals(first.height, preview.height, 1f)
        assertEquals(end.x - (grab.x - first.left), preview.left, 1f)
        assertEquals(first.left, favorite("ChatGPT").fetchSemanticsNode().boundsInRoot.left, 1f)
        assertEquals(listOf("drag-fixture-0", "drag-fixture-1", "drag-fixture-2"),
            runBlocking { container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }.map { it.id } })
        saveScreen("favorites-live-reorder")
        sidebar.performMouseInput { release() }
        compose.waitUntil { runBlocking { container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }.map { it.id } } ==
            listOf("drag-fixture-1", "drag-fixture-2", "drag-fixture-0") }
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite Gmail").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue(favorite("ChatGPT").fetchSemanticsNode().boundsInRoot.left < favorite("Gmail").fetchSemanticsNode().boundsInRoot.left)
    }

    @Test fun favoriteTouchDragAcrossRowsCancelRestoresOriginalOrder() {
        runBlocking {
            for (index in 3..4) container.workspace.dao.putBookmark(Bookmark("drag-fixture-$index",
                "https://example.com/$index", "Grid $index", isFavorite = true, createdAt = index.toLong()).entity())
        }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite Grid 4").fetchSemanticsNodes().isNotEmpty() }
        val sidebar = compose.onNodeWithTag("sidebar")
        val host = sidebar.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithContentDescription("Favorite Gmail").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithContentDescription("Favorite Grid 4").fetchSemanticsNode().boundsInRoot
        sidebar.performTouchInput {
            down(first.center - host.topLeft); advanceEventTime(650)
            moveTo(last.center - host.topLeft, delayMillis = 100)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("favorite-drag-preview").assertIsDisplayed()
        assertEquals(first.left, compose.onNodeWithContentDescription("Favorite ChatGPT").fetchSemanticsNode().boundsInRoot.left, 1f)
        saveScreen("favorites-cross-row-preview")
        sidebar.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(first, compose.onNodeWithContentDescription("Favorite Gmail").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("favorite-drag-preview").assertDoesNotExist()
        assertEquals((0..4).map { "drag-fixture-$it" },
            runBlocking { container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }.map { it.id } })
    }

    @Test fun wholeRowDragReordersPinsUnpinsAndCancelDoesNotMove() {
        drag(tab("Design references"), tab("Project notes"), after = true)
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).last().id == alpha } }
        drag(tab("Project notes"), compose.onNodeWithText("Keep your everyday tabs here"))
        compose.waitUntil { runBlocking { container.workspace.dao.tab(gamma)!!.isPinned } }
        drag(tab("Reading list"), tab("Design references"), after = true, cancel = true)
        compose.waitForIdle()
        assertFalse(runBlocking { container.workspace.dao.tab(beta)!!.isPinned })
        assertEquals(listOf(beta, alpha), runBlocking { container.workspace.dao.tabs(spaceId).filter { !it.isPinned && it.id in listOf(alpha, beta) }.map { it.id } })
        drag(tab("Project notes"), tab("Reading list"), after = false)
        compose.waitUntil { runBlocking { !container.workspace.dao.tab(gamma)!!.isPinned } }
        assertEquals(listOf(gamma, beta, alpha), runBlocking { container.workspace.dao.tabs(spaceId).filter { it.id in listOf(alpha, beta, gamma) }.map { it.id } })
    }
    @Test fun dragToFavoritesBackToPinnedAndAnotherSpaceSurvivesRecreation() {
        val favorites = compose.onNodeWithContentDescription("Favorite Gmail")
        drag(tab("Design references"), favorites)
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite Design references").fetchSemanticsNodes().isNotEmpty() }
        drag(compose.onNodeWithContentDescription("Favorite Design references"), compose.onNodeWithText("Keep your everyday tabs here"))
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == "Design references" && it.isPinned && it.closedAt == null } } }
        drag(tab("Reading list"), compose.onNodeWithContentDescription("Switch to Work"))
        compose.waitUntil { runBlocking { container.workspace.dao.tab(beta)!!.spaceId == "work" } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithContentDescription("Tab Design references").fetchSemanticsNodes().isNotEmpty() }
        saveScreen("arc-sidebar")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Automatic search by region").assertExists()
        saveScreen("regional-search-settings")
    }
    @Test fun hoverActionsAndSecondaryClickDoNotSelectOrDrag() {
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId }
        val reading = tab("Reading list")
        reading.performMouseInput { moveTo(center) }
        compose.onNodeWithContentDescription("Actions for Reading list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Reading list").assertIsDisplayed()
        saveScreen("sidebar-hover-actions")
        reading.performMouseInput { exit() }
        compose.onNodeWithContentDescription("Actions for Reading list").assertDoesNotExist()
        reading.performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
        compose.onNodeWithText("Pin tab").assertDoesNotExist()
        compose.onNodeWithText("Add to favorites").assertDoesNotExist()
        compose.onNodeWithText("Save bookmark").assertDoesNotExist()
        compose.onNodeWithText("Open in right pane").assertIsDisplayed()
        assertEquals(active, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
        assertNull(runBlocking { container.workspace.dao.tab(beta)!!.closedAt })
        saveScreen("sidebar-context-menu")
        compose.onNodeWithText("Open in right pane").performClick()
        compose.onNodeWithTag("browser-pane-right-$beta").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite Gmail").performMouseInput {
            moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary)
        }
        compose.onNodeWithText("Remove favorite").assertIsDisplayed()
    }

    @Test fun customEmojiAndNewTabHomeSurviveRecreation() {
        compose.onNodeWithContentDescription("Space actions").performClick()
        compose.onNodeWithText("Edit Space").performClick()
        compose.onNodeWithText("Custom emoji").performTextReplacement("🧑‍💻")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon == "🧑‍💻" } }
        val previous = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId }
        compose.onNode(hasText("New tab") and hasContentDescription("Tab New tab").not()).performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId != previous } }
        val newId = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        compose.waitUntil { compose.onAllNodesWithText("Esc").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Esc").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("sidebar-tab-$newId"))
        val homeIcon = hasContentDescription("New tab home") and hasAnyAncestor(hasTestTag("sidebar-tab-$newId"))
        compose.onNode(homeIcon, useUnmergedTree = true).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithText("🧑‍💻").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("sidebar-tab-$newId"))
        compose.onNode(homeIcon, useUnmergedTree = true).assertIsDisplayed()
        saveScreen("sidebar-custom-emoji-home")
    }

    @Test fun mouseDragsWithoutLongPressAndLongListAutoScrolls() {
        val source = tab("Design references")
        val from = source.fetchSemanticsNode().boundsInRoot
        val target = tab("Project notes").fetchSemanticsNode().boundsInRoot
        source.performMouseInput {
            moveTo(Offset(from.width * 0.45f, from.height / 2f)); press()
            moveTo(Offset(from.width * 0.45f, target.bottom - 5f - from.top)); release()
        }
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).last().id == alpha } }
        runBlocking {
            repeat(25) { i ->
                val id = container.tabs.create(spaceId, "https://example.com/$i", false)
                container.tabs.updatePage(id, "https://example.com/$i", "Reference $i", null)
            }
        }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Tab Reference 0").fetchSemanticsNodes().isNotEmpty() }
        val src = tab("Reading list")
        val bounds = src.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithContentDescription("Sidebar tabs").fetchSemanticsNode().boundsInRoot
        src.performTouchInput {
            down(Offset(bounds.width * 0.45f, bounds.height / 2)); advanceEventTime(650)
            moveTo(Offset(bounds.width * 0.45f, viewport.bottom - 16f - bounds.top), delayMillis = 100)
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tab Reference 24").fetchSemanticsNodes().isNotEmpty() }
        saveScreen("arc-sidebar-drag")
        compose.onRoot().performTouchInput { cancel() }
        assertEquals(spaceId, runBlocking { container.workspace.dao.tab(beta)!!.spaceId })
    }
}
