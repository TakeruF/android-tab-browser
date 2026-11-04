package com.takeruf.nagi.browser.privacy

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

object PrivateKeyboard {
    fun apply(info: EditorInfo, privateBrowsing: Boolean) {
        if (privateBrowsing) info.imeOptions = info.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
    }
}

/** Covers every Compose editor, including dialogs, without changing CJK composition. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateKeyboardInput(privateBrowsing: Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput(interceptor = { request, next ->
        next.startInputMethod(object : PlatformTextInputMethodRequest {
            override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                val connection = request.createInputConnection(outAttributes)
                PrivateKeyboard.apply(outAttributes, privateBrowsing)
                return connection
            }
        })
    }, content = content)
}
