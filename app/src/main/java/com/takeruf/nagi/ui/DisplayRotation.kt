package com.takeruf.nagi.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
internal fun rememberDisplayRotation(): Int {
    val view = LocalView.current
    val manager = LocalContext.current.getSystemService(DisplayManager::class.java)
    var rotation by remember(view) { mutableIntStateOf(view.display?.rotation ?: 0) }
    DisposableEffect(view, manager) {
        // A 180-degree turn keeps Configuration.orientation unchanged and may not
        // recreate the Activity. Listen for display changes explicitly.
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == view.display?.displayId) rotation = view.display?.rotation ?: rotation
            }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        rotation = view.display?.rotation ?: rotation
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    return rotation
}
