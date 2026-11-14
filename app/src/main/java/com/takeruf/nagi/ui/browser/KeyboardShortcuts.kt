package com.takeruf.nagi.ui.browser

import android.view.KeyEvent

enum class Shortcut(val tabNumber: Int? = null) {
    OMNIBOX, NEW_TAB, CLOSE_TAB, RESTORE_TAB, NEXT_TAB, PREVIOUS_TAB,
    RELOAD, BACK, FORWARD, FIND, ESCAPE,
    ZOOM_IN, ZOOM_OUT, RESET_ZOOM, HISTORY, SETTINGS, PRIVATE_BROWSING,
    TAB_1(1), TAB_2(2), TAB_3(3), TAB_4(4), TAB_5(5),
    TAB_6(6), TAB_7(7), TAB_8(8), TAB_9(9),
}
object KeyboardShortcuts {
    fun map(event: KeyEvent): Shortcut? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null
        val modifiers = KeyEvent.normalizeMetaState(event.metaState)
        val control = modifiers and KeyEvent.META_CTRL_ON != 0
        val command = modifiers and KeyEvent.META_META_ON != 0
        val primary = when {
            control && !command -> KeyEvent.META_CTRL_ON
            command && !control -> KeyEvent.META_META_ON
            else -> 0
        }
        if (primary != 0 && KeyEvent.metaStateHasModifiers(modifiers, primary)) {
            val number = when (event.keyCode) {
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> event.keyCode - KeyEvent.KEYCODE_1 + 1
                in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> event.keyCode - KeyEvent.KEYCODE_NUMPAD_1 + 1
                else -> null
            }
            if (number != null) return Shortcut.entries.first { it.tabNumber == number }
            if (isZoomInSymbol(event, modifiers)) return Shortcut.ZOOM_IN
            return when (event.keyCode) {
                KeyEvent.KEYCODE_L -> Shortcut.OMNIBOX
                KeyEvent.KEYCODE_T -> Shortcut.NEW_TAB
                KeyEvent.KEYCODE_W -> Shortcut.CLOSE_TAB
                KeyEvent.KEYCODE_TAB -> Shortcut.NEXT_TAB
                KeyEvent.KEYCODE_R -> Shortcut.RELOAD
                KeyEvent.KEYCODE_F -> Shortcut.FIND
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> Shortcut.ZOOM_IN
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> Shortcut.ZOOM_OUT
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> Shortcut.RESET_ZOOM
                KeyEvent.KEYCODE_H -> Shortcut.HISTORY
                KeyEvent.KEYCODE_COMMA -> Shortcut.SETTINGS
                else -> null
            }
        }
        if (primary != 0 && KeyEvent.metaStateHasModifiers(modifiers, primary or KeyEvent.META_SHIFT_ON)) {
            if (isZoomInSymbol(event, modifiers)) return Shortcut.ZOOM_IN
            return when (event.keyCode) {
                KeyEvent.KEYCODE_T -> Shortcut.RESTORE_TAB
                KeyEvent.KEYCODE_TAB -> Shortcut.PREVIOUS_TAB
                KeyEvent.KEYCODE_N -> Shortcut.PRIVATE_BROWSING
                KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_NUMPAD_ADD -> Shortcut.ZOOM_IN
                else -> null
            }
        }
        if (KeyEvent.metaStateHasModifiers(modifiers, KeyEvent.META_ALT_ON)) return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Shortcut.BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> Shortcut.FORWARD
            else -> null
        }
        return if (KeyEvent.metaStateHasNoModifiers(modifiers) && event.keyCode == KeyEvent.KEYCODE_ESCAPE) Shortcut.ESCAPE else null
    }

    private fun isZoomInSymbol(event: KeyEvent, modifiers: Int): Boolean {
        // '+' is Shift+= on a US keyboard, but can come from another keycode
        // on JIS and other layouts. Keep Shift while removing shortcut modifiers.
        val character = event.getUnicodeChar(modifiers and (KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK).inv())
        return character == '+'.code || character == '='.code
    }
}
