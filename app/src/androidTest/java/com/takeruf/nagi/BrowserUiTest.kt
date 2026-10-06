package com.takeruf.nagi

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class BrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.workspace.dao.engines().filter { it.keyword == "fixture" }.forEach { container.engines.delete(it.id) }
            container.settings.update { BrowserSettings(theme = com.takeruf.nagi.domain.model.ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create("QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Search or enter URL").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { server.close() }
    private fun shortcut(code: Int, shift: Boolean = false) {
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
            code, 0, AndroidKeyEvent.META_CTRL_ON or if (shift) AndroidKeyEvent.META_SHIFT_ON else 0)) }
    }
    private fun omnibox(input: String) {
        shortcut(AndroidKeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement(input)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
    }
    private fun awaitTitle(title: String) {
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == title && it.closedAt == null } } }
    }
    @Test fun browsingHistoryFavoritesAndClosedTabRestore() {
        omnibox("${server.origin}/one")
        awaitTitle("Fixture One")
        val source = compose.onNodeWithContentDescription("Tab Fixture One")
        val bounds = source.fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("favorites-grid").fetchSemanticsNode().boundsInRoot
        source.performTouchInput {
            val start = center
            val end = target.center - bounds.topLeft
            down(start); advanceEventTime(650)
            for (i in 1..16) moveTo(start + (end - start) * (i / 16f), delayMillis = 20)
            up()
        }
        compose.waitUntil { runBlocking { container.workspace.dao.observeBookmarks().first().any { it.isFavorite && it.title == "Fixture One" } } }
        compose.onNodeWithContentDescription("Bookmarks").assertExists()
        // Moving a tab to Favorites closes its ordinary-tab entry. Reopen the shortcut
        // before exercising the page menu and the close/restore keyboard flow.
        compose.onNodeWithContentDescription("Favorite Fixture One").performClick()
        awaitTitle("Fixture One")
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Add to favorites").assertExists()
        compose.onNodeWithText("Save bookmark").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.observeBookmarks().first().any { !it.isFavorite && it.title == "Fixture One" } } }
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Find in page").performClick()
        compose.onNodeWithContentDescription("Close find").performClick()
        shortcut(AndroidKeyEvent.KEYCODE_W)
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == "Fixture One" && it.closedAt != null } } }
        shortcut(AndroidKeyEvent.KEYCODE_T, shift = true)
        awaitTitle("Fixture One")
        compose.onNodeWithContentDescription("History").performClick()
        compose.onAllNodesWithText("Fixture One").assertAny(hasText("Fixture One"))
    }
    @Test fun commandPaletteCreatesTwoPanesAndResizesDivider() {
        omnibox("${server.origin}/one"); awaitTitle("Fixture One")
        shortcut(AndroidKeyEvent.KEYCODE_T)
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).count { it.closedAt == null } == 2 } }
        omnibox("${server.origin}/two"); awaitTitle("Fixture Two")
        shortcut(AndroidKeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement(">split")
        compose.onNodeWithText("New split view").performClick()
        compose.onNodeWithContentDescription("Resize split view").assertExists().performTouchInput { swipeLeft() }
        compose.onNodeWithText("Choose tab").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Page menu")[0].performClick()
        compose.onNodeWithText("Swap panes").performClick()
        compose.onNodeWithContentDescription("Resize split view").assertExists()
        compose.onAllNodesWithContentDescription("Page menu")[0].performClick()
        compose.onNodeWithText("Close split view").performClick()
        compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
    }
    @Test fun settingsAllowCustomKeywordSearchAndSurviveActivityRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("customize-search-engines").performScrollTo().performClick()
        compose.onNodeWithText("Add search engine").performScrollTo().performClick()
        compose.onNodeWithText("Name").performTextInput("Fixture Search")
        compose.onNodeWithText("Keyword").performTextInput("fixture")
        compose.onNodeWithText("Search URL Template").performTextReplacement("${server.origin}/one?q={query}")
        compose.onNodeWithText("Save").performClick()
        // HTTPS validation deliberately prevents a local HTTP search provider.
        compose.onNodeWithText("Use a valid https search URL").assertExists()
        compose.onNodeWithText("Search URL Template").performTextReplacement("https://example.com/?q={query}")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { runBlocking { container.workspace.dao.engines().any { it.keyword == "fixture" } } }
        val customId = runBlocking { container.workspace.dao.engines().first { it.keyword == "fixture" }.id }
        compose.onNodeWithTag("search-engines-list").performScrollToNode(hasTestTag("common-engine:$customId"))
        compose.onNodeWithTag("common-engine:$customId").performClick()
        compose.waitUntil(5_000) { runBlocking { customId in container.settings.settings.first().commonSearchEngineIds.orEmpty() } }
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithContentDescription("Back to browser").performClick()
        shortcut(AndroidKeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement("fixture tablet")
        compose.onNodeWithText("Search Fixture Search for “tablet”").assertExists()
        compose.onNodeWithText("Esc").performClick()
        omnibox("${server.origin}/one"); awaitTitle("Fixture One")
        compose.activityRule.scenario.recreate()
        awaitTitle("Fixture One")
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Drag Fixture One").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { container.workspace.dao.engines().any { it.keyword == "fixture" } })
    }
}
