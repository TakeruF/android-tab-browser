package com.takeruf.nagi

import android.view.KeyEvent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Creating standalone tabs must not change the saved split pair. */
class SplitNewTabUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String

    private fun shortcut(code: Int) = compose.runOnIdle {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            code, 0, KeyEvent.META_CTRL_ON))
    }

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Split QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.workspace.dao.tabs(spaceId).single { it.closedAt == null }.id
            rightId = container.tabs.create(spaceId, "${server.origin}/two", select = false)
            val dao = container.workspace.dao
            dao.putTab(dao.tab(leftId)!!.copy(url = "${server.origin}/one", title = "Fixture One"))
            dao.putTab(dao.tab(rightId)!!.copy(title = "Fixture Two"))
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$leftId").fetchSemanticsNodes().isNotEmpty()
        }
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement(">split")
        compose.onNodeWithText("New split view").performClick()
        assertPair()
    }

    @After fun cleanup() { server.close() }

    private fun assertPair() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-right-$rightId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("browser-pane-left-$leftId").assertExists()
        compose.onNodeWithTag("browser-pane-right-$rightId").assertExists()
        compose.onNodeWithTag("sidebar-split-group").assertExists()
        compose.onNodeWithContentDescription("Resize split view").assertExists()
    }

    private fun assertStandaloneAndReturn(recreate: Boolean = false) {
        compose.waitUntil(10_000) {
            runBlocking { container.workspace.dao.tabs(spaceId).count { it.closedAt == null } == 3 }
        }
        val newId = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        assertNotEquals(leftId, newId)
        assertNotEquals(rightId, newId)
        if (recreate) compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$newId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
        if (compose.onAllNodesWithText("Esc").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Esc").performClick()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("sidebar-split-group"))
        compose.onNodeWithTag("sidebar-split-group").assertExists()
        // Either member of the saved group restores both original panes.
        compose.onNodeWithContentDescription("Tab Fixture Two").performClick()
        assertPair()
        compose.onNodeWithContentDescription("Tab Fixture One").performClick()
        assertPair()
        compose.activityRule.scenario.recreate()
        assertPair()
    }

    private fun dragMemberOut(toRight: Boolean, pinned: Boolean, mouse: Boolean, above: Boolean = false) {
        val draggedId = if (toRight) rightId else leftId
        val remainingId = if (toRight) leftId else rightId
        val source = compose.onNodeWithTag("sidebar-tab-$draggedId")
        val from = source.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(if (pinned) "sidebar-pinned-drop" else if (above) "sidebar-today-top-drop" else "sidebar-today-drop")
        val end = target.fetchSemanticsNode().boundsInRoot.center - from.topLeft
        // Cancelling an extraction leaves both panes and their group intact.
        source.performTouchInput { down(center); advanceEventTime(650); moveTo(end, delayMillis = 100) }
        compose.onNodeWithText("Drop as an unpinned tab").assertDoesNotExist()
        compose.onNodeWithText("Release to separate tabs").assertDoesNotExist()
        if (!pinned) {
            val indicator = compose.onNodeWithTag(if (above) "sidebar-today-top-indicator" else "sidebar-today-drop-indicator")
            indicator.assertIsDisplayed()
            val group = compose.onNodeWithTag("sidebar-split-group").fetchSemanticsNode().boundsInRoot
            val line = indicator.fetchSemanticsNode().boundsInRoot
            if (above) assertTrue(line.bottom <= group.top) else assertTrue(line.top >= group.bottom)
        }
        source.performTouchInput { cancel() }
        assertPair()
        if (mouse) source.performMouseInput {
            moveTo(center); press(); moveTo(end, delayMillis = 100); release()
        } else {
            source.performTouchInput { down(center); advanceEventTime(650); moveTo(end, delayMillis = 100) }
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), if (above) "split-detach-above.png" else "split-detach-hover.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            source.performTouchInput { up() }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("sidebar-split-group").fetchSemanticsNodes().isEmpty() &&
                runBlocking { container.workspace.dao.tab(draggedId)?.isPinned == pinned }
        }
        if (!pinned) {
            val order = runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }.map { it.id } }
            if (above) assertTrue(order.indexOf(draggedId) < order.indexOf(remainingId))
            else assertTrue(order.indexOf(draggedId) > order.indexOf(remainingId))
        }
        compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
        compose.onNodeWithTag("browser-pane-left-$draggedId").assertExists()
        for (id in listOf(draggedId, remainingId)) {
            assertNull(runBlocking { container.workspace.dao.tab(id)!!.closedAt })
            compose.onNodeWithTag("sidebar-tab-$id").assertExists().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("browser-pane-left-$id").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$remainingId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("sidebar-split-group").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-tab-$draggedId").assertExists()
    }

    @Test fun touchDragRightMemberOutSeparatesBothTabs() = dragMemberOut(toRight = true, pinned = false, mouse = false)
    @Test fun mouseDragLeftMemberToPinnedSeparatesBothTabs() = dragMemberOut(toRight = false, pinned = true, mouse = true)

    @Test fun dragRightMemberAboveGroupInsertsBeforeLeft() = dragMemberOut(toRight = true, pinned = false, mouse = false, above = true)
    @Test fun dragLeftMemberAboveGroupInsertsBeforeRight() = dragMemberOut(toRight = false, pinned = false, mouse = true, above = true)

    @Test fun keyboardNewTabPreservesPairThroughRecreation() {
        shortcut(KeyEvent.KEYCODE_T)
        assertStandaloneAndReturn(recreate = true)
    }

    @Test fun sidebarNewTabPreservesPairWithRightPaneFocused() {
        compose.onNodeWithContentDescription("Tab Fixture Two").performClick()
        compose.onNodeWithTag("new-tab-button").performScrollTo().performClick()
        assertStandaloneAndReturn()
    }

    private fun closePaneAndAssertSurvivor(closeLeft: Boolean, focusRight: Boolean) {
        // A third tab must not replace the surviving split member when the focused pane closes.
        runBlocking { container.tabs.create(spaceId, "${server.origin}/three", select = false) }
        compose.onNodeWithContentDescription(if (focusRight) "Tab Fixture Two" else "Tab Fixture One").performClick()
        val closedId = if (closeLeft) leftId else rightId
        val remainingId = if (closeLeft) rightId else leftId
        compose.onNodeWithTag("close-split-tab:$closedId").performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                container.workspace.dao.tab(closedId)?.closedAt != null &&
                    container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == remainingId
            } && compose.onAllNodesWithTag("browser-pane-left-$remainingId").fetchSemanticsNodes().isNotEmpty()
        }
        assertNull(runBlocking { container.workspace.dao.tab(remainingId)!!.closedAt })
        compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-split-group").assertDoesNotExist()
        compose.onNodeWithTag("close-split-tab:$remainingId").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$remainingId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
    }

    @Test fun closeFocusedLeftPaneKeepsRightTab() = closePaneAndAssertSurvivor(closeLeft = true, focusRight = false)
    @Test fun closeUnfocusedLeftPaneKeepsRightTab() = closePaneAndAssertSurvivor(closeLeft = true, focusRight = true)
    @Test fun closeFocusedRightPaneKeepsLeftTab() = closePaneAndAssertSurvivor(closeLeft = false, focusRight = true)
    @Test fun closeUnfocusedRightPaneKeepsLeftTab() = closePaneAndAssertSurvivor(closeLeft = false, focusRight = false)

    @Test fun collapsedSidebarCombinesPairAndRestoresBothPanes() {
        compose.onNodeWithContentDescription("Tab Fixture Two").performClick()
        compose.onNodeWithContentDescription("Collapse sidebar").performClick()
        val group = compose.onNodeWithTag("sidebar-split-group")
        group.assertIsSelected().assertWidthIsEqualTo(48.dp)
        compose.onNodeWithContentDescription("Tab Fixture One / Fixture Two").assertExists()
        compose.onNodeWithContentDescription("Tab Fixture One").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tab Fixture Two").assertDoesNotExist()
        group.performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == rightId }
        }

        shortcut(KeyEvent.KEYCODE_T)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Resize split view").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Esc").performClick()
        group.assertIsNotSelected().performClick()
        assertPair()
        compose.waitUntil(10_000) {
            runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == leftId }
        }
        compose.activityRule.scenario.recreate()
        assertPair()
        group.assertIsSelected()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "collapsed-split-sidebar.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()

        // Removing either member exposes the surviving tab as a normal icon again.
        runBlocking { container.tabs.close(rightId) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("sidebar-split-group").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithContentDescription("Tab Fixture One").assertExists()
    }
}
