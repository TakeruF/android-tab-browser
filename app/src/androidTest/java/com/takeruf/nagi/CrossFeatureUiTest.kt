package com.takeruf.nagi

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.ArchivePeriod
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Cross-feature contracts: selection, split focus, shortcuts and archival. */
class CrossFeatureUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String

    private fun shortcut(code: Int) = compose.runOnIdle {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            code, 0, KeyEvent.META_CTRL_ON))
    }

    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Cross QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!!
            rightId = container.tabs.create(spaceId, select = false)
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$leftId").fetchSemanticsNodes().isNotEmpty()
        }
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement(">split")
        compose.onNodeWithText("New split view").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-right-$rightId").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() = runBlocking { container.spaces.delete(spaceId) }

    @Test fun numberedRightTabSelectionMakesCloseShortcutCloseRightTab() {
        // Shared Favorites precede the two tabs in the documented numbered order.
        val favoriteCount = runBlocking { container.workspace.dao.observeBookmarks().first().count { it.isFavorite } }
        val number = favoriteCount + 2
        assertTrue("Fixture must fit numbered shortcut range", number <= 9)
        shortcut(KeyEvent.KEYCODE_1 + number - 1)
        compose.waitUntil(5_000) {
            runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == rightId }
        }
        shortcut(KeyEvent.KEYCODE_W)
        compose.waitUntil(5_000) {
            runBlocking { listOf(leftId, rightId).any { container.workspace.dao.tab(it)?.closedAt != null } }
        }
        assertNull("Selecting the right tab must keep the left tab open", runBlocking { container.workspace.dao.tab(leftId)!!.closedAt })
        assertNotNull("Ctrl W must close the selected right tab", runBlocking { container.workspace.dao.tab(rightId)!!.closedAt })
    }

    @Test fun nextTabSelectionMakesCloseShortcutCloseRightTab() {
        shortcut(KeyEvent.KEYCODE_TAB)
        compose.waitUntil(5_000) {
            runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == rightId }
        }
        shortcut(KeyEvent.KEYCODE_W)
        compose.waitUntil(5_000) {
            runBlocking { listOf(leftId, rightId).any { container.workspace.dao.tab(it)?.closedAt != null } }
        }
        assertNull("Ctrl Tab to the right tab must preserve the left tab", runBlocking { container.workspace.dao.tab(leftId)!!.closedAt })
        assertNotNull(runBlocking { container.workspace.dao.tab(rightId)!!.closedAt })
    }

    @Test fun archiveDoesNotHideEitherCurrentlyDisplayedSplitPage() {
        runBlocking {
            val dao = container.workspace.dao
            dao.putTab(dao.tab(rightId)!!.copy(lastAccessedAt = 1))
            container.settings.update { it.copy(archivePeriod = ArchivePeriod.DAY) }
            container.tabs.archiveNow()
        }
        compose.waitForIdle()
        assertNull("A visible right pane must be protected from archival", runBlocking { container.workspace.dao.tab(rightId)!!.archivedAt })
        compose.onNodeWithTag("browser-pane-right-$rightId").assertExists()
    }
}
