package com.orbit.browser.testing

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.TextView

/** Debug-only IME: the real system InputConnection without another IME modifying the test preedit. */
class InputConnectionHarnessIme : InputMethodService() {
    companion object {
        @Volatile var active: InputConnection? = null
            private set
        @Volatile var editor: EditorInfo? = null
            private set
        @Volatile var bound = false
            private set
    }
    override fun onBindInput() { super.onBindInput(); bound = true }
    override fun onUnbindInput() { bound = false; active = null; editor = null; super.onUnbindInput() }
    override fun onDestroy() { bound = false; active = null; editor = null; super.onDestroy() }
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting); editor = attribute; active = currentInputConnection
    }
    override fun onFinishInput() { active = null; editor = null; super.onFinishInput() }
    override fun onCreateInputView(): View = TextView(this).apply {
        text = "Orbit · IME composition test"; setPadding(24, 16, 24, 16)
    }
}
