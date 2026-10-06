package com.takeruf.nagi

import android.os.Bundle
import android.content.Intent
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.takeruf.nagi.browser.engine.NativeBrowserHost
import com.takeruf.nagi.ui.NagiApp
import com.takeruf.nagi.ui.browser.KeyboardShortcuts
import com.takeruf.nagi.ui.browser.Shortcut
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    var shortcutHandler: ((Shortcut) -> Boolean)? = null
    private lateinit var host: NativeBrowserHost
    private val incomingUrls = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        host = NativeBrowserHost(this)
        val originalCallback = window.callback
        window.callback = object : Window.Callback by originalCallback {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                handleShortcut(event) || originalCallback.dispatchKeyEvent(event)

            override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
                // Normalize before View/Compose hit testing: SOURCE_TOUCHPAD uses focused-view
                // routing, but a wheel event should reach the content under the pointer.
                if (event.actionMasked == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_TOUCHPAD)) {
                    val pointerEvent = MotionEvent.obtain(event)
                    try {
                        pointerEvent.source = InputDevice.SOURCE_MOUSE
                        return originalCallback.dispatchGenericMotionEvent(pointerEvent)
                    } finally { pointerEvent.recycle() }
                }
                return originalCallback.dispatchGenericMotionEvent(event)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        val container = (application as NagiApplication).container
        setContent { NagiApp(this, host, container, incomingUrls) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    private fun handleIntent(intent: Intent) {
        intent.dataString?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { incomingUrls.tryEmit(it) }
    }
    private fun handleShortcut(event: KeyEvent): Boolean {
        val shortcut = KeyboardShortcuts.map(event)
        if (shortcut != null && shortcutHandler?.invoke(shortcut) == true) return true
        return false
    }
    override fun onDestroy() { host.dispose(); super.onDestroy() }
}
