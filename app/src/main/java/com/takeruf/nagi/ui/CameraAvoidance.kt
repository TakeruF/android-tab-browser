package com.takeruf.nagi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.domain.model.BrowserSettings

internal fun cameraIsOnLeft(settings: BrowserSettings, landscape: Boolean, rotation: Int): Boolean =
    settings.avoidCameraOverlap && landscape && settings.cameraLeftRotation == rotation

internal fun cameraAvoidanceInsets(settings: BrowserSettings, landscape: Boolean, rotation: Int): WindowInsets {
    val margin = if (cameraIsOnLeft(settings, landscape, rotation))
        settings.cameraSideMargin.coerceIn(0, 64) else 0
    // Physical left, independent of the interface language's reading direction.
    return WindowInsets(left = margin.dp)
}

internal fun cameraContentInsets(
    settings: BrowserSettings, landscape: Boolean, rotation: Int,
    safeDrawing: WindowInsets, systemBars: WindowInsets, ime: WindowInsets,
): WindowInsets {
    if (!settings.avoidCameraOverlap || !landscape || settings.cameraLeftRotation == null) return safeDrawing
    // Once calibrated, the user controls horizontal camera spacing. Do not leave
    // a cutout inset on the right; keep actual system bars, IME and vertical safety.
    return safeDrawing.only(WindowInsetsSides.Vertical)
        .union(systemBars.only(WindowInsetsSides.Horizontal))
        .union(ime.only(WindowInsetsSides.Horizontal))
        .union(cameraAvoidanceInsets(settings, landscape, rotation))
}
