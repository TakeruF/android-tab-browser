package com.takeruf.nagi

import android.app.Application
import android.view.KeyEvent
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class KeyboardShortcutsTest {
    private fun key(code: Int, meta: Int = KeyEvent.META_CTRL_ON, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0) =
        KeyEvent(0, 0, action, code, repeat, meta)

    @Test fun allNumbersSupportControlCommandAndNumpad() {
        for (number in 1..9) for (meta in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)) {
            assertEquals(number, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1 + number - 1, meta))?.tabNumber)
            assertEquals(number, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_NUMPAD_1 + number - 1, meta))?.tabNumber)
        }
    }
    @Test fun numbersDoNotInterceptTypingOtherModifiersReleaseOrRepeat() {
        for (meta in listOf(0, KeyEvent.META_ALT_ON, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)) assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, meta)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_0)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, action = KeyEvent.ACTION_UP)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, repeat = 1)))
        assertEquals(Shortcut.NEXT_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_TAB)))
        assertEquals(Shortcut.PREVIOUS_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)))
        assertEquals(Shortcut.RESTORE_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)))
    }
    @Test fun numberingMatchesSidebarSectionsAndExcludesUnavailableTabs() {
        val state = BrowserUiState(workspace = WorkspaceSnapshot(
            spaces = listOf(Space("personal", "Personal")),
            bookmarks = listOf(Bookmark("bookmark", "https://bookmark.test", "Bookmark"),
                Bookmark("favorite", "https://favorite.test", "Favorite", isFavorite = true)),
            tabs = listOf(BrowserTab("normal", "personal"), BrowserTab("pinned", "personal", isPinned = true),
                BrowserTab("closed", "personal", closedAt = 1), BrowserTab("archived", "personal", archivedAt = 1),
                BrowserTab("other", "other"))))
        assertEquals(NumberedTabTarget.Favorite("https://favorite.test"), state.numberedTabTarget(1))
        assertEquals(NumberedTabTarget.Tab("pinned"), state.numberedTabTarget(2))
        assertEquals(NumberedTabTarget.Tab("normal"), state.numberedTabTarget(3))
        for (number in listOf(0, 4, 9, 10)) assertNull(state.numberedTabTarget(number))
        assertNull(BrowserUiState().numberedTabTarget(1))
    }
    @Test fun nineSelectsNinthEvenWithMoreTabsAndReorderingChangesTarget() {
        val tabs = (1..12).map { BrowserTab("tab$it", "personal") }
        val state = BrowserUiState(workspace = WorkspaceSnapshot(spaces = listOf(Space("personal", "Personal")), tabs = tabs))
        assertEquals(NumberedTabTarget.Tab("tab9"), state.numberedTabTarget(9))
        assertEquals(NumberedTabTarget.Tab("tab12"), state.copy(workspace = state.workspace.copy(tabs = tabs.reversed())).numberedTabTarget(1))
    }
}
