package com.takeruf.nagi

import android.view.inputmethod.EditorInfo
import com.takeruf.nagi.browser.privacy.PrivateKeyboard
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = android.app.Application::class)
class PrivateKeyboardTest {
    @Test fun privateFlagPreservesEditorActionAndOtherOptions() {
        val original = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        val info = EditorInfo().apply { imeOptions = original }
        PrivateKeyboard.apply(info, true)
        assertEquals(original or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, info.imeOptions)
    }
    @Test fun normalModePreservesProviderOptions() {
        val info = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }
        PrivateKeyboard.apply(info, false)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, info.imeOptions)
    }
}
