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

open class MainActivity : ComponentActivity() {
    open val isPrivateBrowsing: Boolean = false
    var disposeSessions: (() -> Unit)? = null
    var shortcutHandler: ((Shortcut) -> Boolean)? = null
    private var ownsTaskRoot = false
    private lateinit var host: NativeBrowserHost
    lateinit var updateInstaller: com.takeruf.nagi.updates.UpdateInstaller
        private set
    private val incomingUrls = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ownsTaskRoot = isTaskRoot
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
                        return com.takeruf.nagi.ui.browser.TouchpadScrollDispatch.dispatch {
                            originalCallback.dispatchGenericMotionEvent(pointerEvent)
                        }
                    } finally { pointerEvent.recycle() }
                }
                return originalCallback.dispatchGenericMotionEvent(event)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        val container = if (isPrivateBrowsing)
            (application as NagiApplication).privateWorkspaces.acquire(taskId)
        else (application as NagiApplication).container
        if (isPrivateBrowsing) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        updateInstaller = com.takeruf.nagi.updates.UpdateInstaller(this, container.updates)
        setContent {
            com.takeruf.nagi.browser.privacy.PrivateKeyboardInput(isPrivateBrowsing) {
                NagiApp(this, host, container, incomingUrls)
            }
        }
    }
    fun openPrivateBrowsing() {
        if (isPrivateBrowsing) return
        if (!com.takeruf.nagi.browser.privacy.PrivateProfiles.supported()) {
            host.showMessage(getString(R.string.ui_private_unavailable)); return
        }
        startActivity(Intent(this, PrivateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isPrivateBrowsing) outState.clear()
    }
    private fun handleIntent(intent: Intent) {
        intent.dataString?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { incomingUrls.tryEmit(it) }
    }
    private fun handleShortcut(event: KeyEvent): Boolean {
        val shortcut = KeyboardShortcuts.map(event)
        if (shortcut != null && shortcutHandler?.invoke(shortcut) == true) return true
        return false
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); host.video.onUserLeave() }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        host.video.onPipChanged(isInPictureInPictureMode)
    }
    @androidx.annotation.RequiresApi(35)
    override fun onPictureInPictureUiStateChanged(pipState: android.app.PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (pipState.isTransitioningToPip) host.video.onPipTransition()
    }
    override fun onStop() { host.video.onStopped(); super.onStop() }
    override fun onDestroy() {
        disposeSessions?.invoke()
        host.dispose()
        super.onDestroy()
        // A task's root can also report removal if Android has already stopped the observer service.
        if (!isPrivateBrowsing && isFinishing && ownsTaskRoot) {
            (application as NagiApplication).privateWorkspaces.close()
        }
    }
}

class PrivateActivity : MainActivity() {
    override val isPrivateBrowsing = true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as NagiApplication).privateWorkspaces.attach(this)
    }
    override fun onDestroy() {
        super.onDestroy()
        (application as NagiApplication).privateWorkspaces.detach(this)
    }

    fun closePrivateBrowsing() {
        (application as NagiApplication).privateWorkspaces.close()
    }
}
