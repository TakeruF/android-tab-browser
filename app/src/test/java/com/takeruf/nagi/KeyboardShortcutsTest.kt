package com.takeruf.nagi

import android.app.Application
import android.view.KeyEvent
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.browser.search.BrowserCommand
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

    @Test fun commandSettingsAdvertiseOnlySupportedControlAndCommandBindings() {
        val bindings = listOf(
            Triple(BrowserCommand.NEW_TAB, KeyEvent.KEYCODE_T, Shortcut.NEW_TAB),
            Triple(BrowserCommand.RESTORE_TAB, KeyEvent.KEYCODE_T, Shortcut.RESTORE_TAB),
            Triple(BrowserCommand.FIND, KeyEvent.KEYCODE_F, Shortcut.FIND),
            Triple(BrowserCommand.HISTORY, KeyEvent.KEYCODE_H, Shortcut.HISTORY),
            Triple(BrowserCommand.SETTINGS, KeyEvent.KEYCODE_COMMA, Shortcut.SETTINGS),
        )
        for ((command, code, action) in bindings) {
            for (primary in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)) {
                val modifiers = primary or if (command == BrowserCommand.RESTORE_TAB) KeyEvent.META_SHIFT_ON else 0
                assertEquals(action, KeyboardShortcuts.map(key(code, modifiers)))
            }
        }
        assertEquals(listOf("Ctrl / Command + T", "Ctrl / Command + Shift + T", "Ctrl / Command + F",
            "Ctrl / Command + H", "Ctrl / Command + ,"), bindings.map { it.first.shortcutLabel })
        BrowserCommand.entries.filter { it !in bindings.map { binding -> binding.first } }.forEach {
            assertNull(it.shortcutLabel)
        }
    }

    @Test fun allNumbersSupportControlCommandAndNumpad() {
        for (number in 1..9) for (meta in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)) {
            assertEquals(number, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1 + number - 1, meta))?.tabNumber)
            assertEquals(number, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_NUMPAD_1 + number - 1, meta))?.tabNumber)
        }
    }
    @Test fun numbersDoNotInterceptTypingOtherModifiersReleaseOrRepeat() {
        for (meta in listOf(0, KeyEvent.META_ALT_ON, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
            KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)) assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, meta)))
        assertEquals(Shortcut.RESET_ZOOM, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_0)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, action = KeyEvent.ACTION_UP)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_1, repeat = 1)))
        assertEquals(Shortcut.NEXT_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_TAB)))
        assertEquals(Shortcut.PREVIOUS_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_TAB, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)))
        assertEquals(Shortcut.RESTORE_TAB, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)))
    }
    @Test fun commonBrowserActionsSupportControlCommandAndKeyboardLayouts() {
        for (meta in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)) {
            for (code in listOf(KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD)) {
                assertEquals(Shortcut.ZOOM_IN, KeyboardShortcuts.map(key(code, meta)))
                assertEquals(Shortcut.ZOOM_IN, KeyboardShortcuts.map(key(code, meta or KeyEvent.META_SHIFT_ON)))
            }
            for (code in listOf(KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT)) {
                assertEquals(Shortcut.ZOOM_OUT, KeyboardShortcuts.map(key(code, meta)))
            }
            for (code in listOf(KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0)) {
                assertEquals(Shortcut.RESET_ZOOM, KeyboardShortcuts.map(key(code, meta)))
            }
            assertEquals(Shortcut.HISTORY, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_H, meta)))
            assertEquals(Shortcut.SETTINGS, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_COMMA, meta)))
            assertEquals(Shortcut.PRIVATE_BROWSING, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_N, meta or KeyEvent.META_SHIFT_ON)))
        }
        assertEquals(Shortcut.HISTORY, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_H, KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CAPS_LOCK_ON)))
        assertEquals(Shortcut.RESET_ZOOM, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.META_META_RIGHT_ON or KeyEvent.META_NUM_LOCK_ON)))
    }
    @Test fun unrelatedModifierCombinationsAndUnusedChordsReachThePageOrPlatform() {
        for (code in listOf(KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_COMMA)) {
            for (meta in listOf(0, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON,
                KeyEvent.META_META_ON or KeyEvent.META_ALT_ON, KeyEvent.META_CTRL_ON or KeyEvent.META_META_ON,
                KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON, KeyEvent.META_CTRL_ON or KeyEvent.META_SYM_ON)) {
                assertNull("code=$code, modifiers=$meta", KeyboardShortcuts.map(key(code, meta)))
            }
        }
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_N)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_D)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_H, action = KeyEvent.ACTION_UP)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_PLUS, repeat = 1)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON)))
        assertNull(KeyboardShortcuts.map(key(KeyEvent.KEYCODE_ESCAPE, KeyEvent.META_ALT_ON)))
        assertEquals(Shortcut.ESCAPE, KeyboardShortcuts.map(key(KeyEvent.KEYCODE_ESCAPE, 0)))
    }
    @Test fun zoomInUsesTheKeyboardLayoutWithoutRelaxingModifierGuards() {
        fun layoutKey(code: Int, modifiers: Int, character: Char) = object : KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers) {
            override fun getUnicodeChar(metaState: Int): Int {
                assertEquals(0, metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK))
                assertEquals(modifiers and KeyEvent.META_SHIFT_ON, metaState and KeyEvent.META_SHIFT_ON)
                return character.code
            }
        }
        for (primary in listOf(KeyEvent.META_CTRL_ON, KeyEvent.META_META_ON)) {
            assertEquals(Shortcut.ZOOM_IN, KeyboardShortcuts.map(layoutKey(KeyEvent.KEYCODE_SEMICOLON, primary or KeyEvent.META_SHIFT_ON, '+')))
            assertEquals(Shortcut.ZOOM_IN, KeyboardShortcuts.map(layoutKey(KeyEvent.KEYCODE_UNKNOWN, primary, '=')))
            for (extra in listOf(KeyEvent.META_ALT_ON, KeyEvent.META_SYM_ON, KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON)) {
                assertNull(KeyboardShortcuts.map(layoutKey(KeyEvent.KEYCODE_SEMICOLON, primary or extra, '+')))
            }
            assertNull(KeyboardShortcuts.map(layoutKey(KeyEvent.KEYCODE_MINUS, primary or KeyEvent.META_SHIFT_ON, '-')))
            assertNull(KeyboardShortcuts.map(layoutKey(KeyEvent.KEYCODE_0, primary or KeyEvent.META_SHIFT_ON, '0')))
        }
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
