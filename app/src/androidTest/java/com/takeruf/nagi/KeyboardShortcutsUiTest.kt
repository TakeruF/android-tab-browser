package com.takeruf.nagi

import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class KeyboardShortcutsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun numberedShortcutsSwitchTabsFromBrowserSettingsAndOmnibox() {
        val container = (compose.activity.application as NagiApplication).container
        lateinit var spaceId: String
        val ids = runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Keyboard QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            // Use no shared Favorites so the test isolates numbered tab selection.
            container.workspace.snapshot.first().bookmarks.filter { it.isFavorite }.forEach { container.library.removeBookmark(it.id) }
            val first = container.workspace.dao.tabs(spaceId).single().id
            val second = container.tabs.create(spaceId, "about:blank")
            container.tabs.togglePin(second)
            val rest = (3..10).map { container.tabs.create(spaceId, "about:blank") }
            listOf(second, first) + rest
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        fun shortcut(code: Int, meta: Int = KeyEvent.META_CTRL_ON) {
            compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)) }
        }
        fun awaitActive(id: String) {
            compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == id } }
            compose.waitForIdle()
        }
        shortcut(KeyEvent.KEYCODE_1); awaitActive(ids[0])
        shortcut(KeyEvent.KEYCODE_9); awaitActive(ids[8])
        compose.onNodeWithContentDescription("Settings").performClick()
        shortcut(KeyEvent.KEYCODE_2, KeyEvent.META_META_ON); awaitActive(ids[1])
        compose.onNodeWithContentDescription("Back to browser").assertDoesNotExist()
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).assertExists()
        shortcut(KeyEvent.KEYCODE_NUMPAD_1); awaitActive(ids[0])
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        runBlocking { ids.drop(2).forEach { container.tabs.close(it) } }
        compose.waitForIdle()
        shortcut(KeyEvent.KEYCODE_8)
        assertEquals(ids[0], runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
    }
}
