package com.takeruf.nagi.ui.browser

import android.view.KeyEvent

enum class Shortcut(val tabNumber: Int? = null) {
    OMNIBOX, NEW_TAB, CLOSE_TAB, RESTORE_TAB, NEXT_TAB, PREVIOUS_TAB,
    RELOAD, BACK, FORWARD, FIND, ESCAPE,
    TAB_1(1), TAB_2(2), TAB_3(3), TAB_4(4), TAB_5(5),
    TAB_6(6), TAB_7(7), TAB_8(8), TAB_9(9),
}
object KeyboardShortcuts {
    fun map(event: KeyEvent): Shortcut? {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return null
        val primary = event.isCtrlPressed || event.isMetaPressed
        if (primary && !event.isShiftPressed && !event.isAltPressed) {
            val number = when (event.keyCode) {
                in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> event.keyCode - KeyEvent.KEYCODE_1 + 1
                in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> event.keyCode - KeyEvent.KEYCODE_NUMPAD_1 + 1
                else -> null
            }
            if (number != null) return Shortcut.entries.first { it.tabNumber == number }
        }
        if (primary) return when (event.keyCode) {
            KeyEvent.KEYCODE_L -> Shortcut.OMNIBOX
            KeyEvent.KEYCODE_T -> if (event.isShiftPressed) Shortcut.RESTORE_TAB else Shortcut.NEW_TAB
            KeyEvent.KEYCODE_W -> Shortcut.CLOSE_TAB
            KeyEvent.KEYCODE_TAB -> if (event.isShiftPressed) Shortcut.PREVIOUS_TAB else Shortcut.NEXT_TAB
            KeyEvent.KEYCODE_R -> Shortcut.RELOAD
            KeyEvent.KEYCODE_F -> Shortcut.FIND
            else -> null
        }
        if (event.isAltPressed) return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> Shortcut.BACK
            KeyEvent.KEYCODE_DPAD_RIGHT -> Shortcut.FORWARD
            else -> null
        }
        return if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) Shortcut.ESCAPE else null
    }
}
