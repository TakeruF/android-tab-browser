package com.takeruf.nagi.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.BrowserSettings

// Union with safeDrawing at the caller: use the larger inset, never add the same
// cutout/system-bar spacing twice. Both physical sides also cover reverse landscape
// and devices that do not report their camera cutout to Android.
internal fun cameraAvoidanceInsets(settings: BrowserSettings, landscape: Boolean): WindowInsets {
    val margin = if (settings.avoidCameraOverlap && landscape)
        settings.cameraSideMargin.coerceIn(0, 64) else 0
    return WindowInsets(left = margin.dp, right = margin.dp)
}
