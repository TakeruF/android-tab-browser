package com.takeruf.nagi

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

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

    @Test fun keyboardNewTabPreservesPairThroughRecreation() {
        shortcut(KeyEvent.KEYCODE_T)
        assertStandaloneAndReturn(recreate = true)
    }

    @Test fun sidebarNewTabPreservesPairWithRightPaneFocused() {
        compose.onNodeWithContentDescription("Tab Fixture Two").performClick()
        compose.onNodeWithTag("new-tab-button").performScrollTo().performClick()
        assertStandaloneAndReturn()
    }
}
