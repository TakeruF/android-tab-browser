package com.takeruf.nagi

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.ui.cameraAvoidanceInsets
import com.takeruf.nagi.ui.cameraContentInsets
import org.junit.Assert.*
import org.junit.Test

class CameraAvoidanceInsetsTest {
    private val density = Density(2f)
    private val enabled = BrowserSettings(avoidCameraOverlap = true, cameraSideMargin = 32, cameraLeftRotation = 0)

    @Test fun offPortraitAndUnregisteredDoNotAddCameraMargins() {
        for ((settings, landscape) in listOf(BrowserSettings() to true, enabled to false,
            enabled.copy(cameraLeftRotation = null) to true)) {
            val inset = cameraAvoidanceInsets(settings, landscape, 0)
            assertEquals(0, inset.getLeft(density, LayoutDirection.Ltr))
            assertEquals(0, inset.getRight(density, LayoutDirection.Ltr))
        }
    }

    @Test fun onlyRegisteredLandscapeGetsPhysicalLeftMarginAcrossAllRotationPairs() {
        for (rotation in 0..3) for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val settings = enabled.copy(cameraLeftRotation = rotation)
            val left = cameraAvoidanceInsets(settings, true, rotation)
            assertEquals(64, left.getLeft(density, direction))
            assertEquals(0, left.getRight(density, direction))
            val right = cameraAvoidanceInsets(settings, true, (rotation + 2) % 4)
            assertEquals(0, right.getLeft(density, direction))
            assertEquals(0, right.getRight(density, direction))
        }
    }

    @Test fun rightCameraDoesNotRetainCutoutGutterButKeepsSystemBarsAndIme() {
        val safe = WindowInsets(left = 90, top = 24, right = 100, bottom = 400)
        val bars = WindowInsets(left = 0, top = 24, right = 20, bottom = 48)
        val ime = WindowInsets(left = 0, top = 0, right = 0, bottom = 400)
        val right = cameraContentInsets(enabled, true, 2, safe, bars, ime)
        assertEquals(0, right.getLeft(density, LayoutDirection.Ltr))
        assertEquals(20, right.getRight(density, LayoutDirection.Ltr))
        assertEquals(24, right.getTop(density))
        assertEquals(400, right.getBottom(density))
        val left = cameraContentInsets(enabled, true, 0, safe, bars, ime)
        assertEquals(64, left.getLeft(density, LayoutDirection.Ltr))
        assertEquals(20, left.getRight(density, LayoutDirection.Ltr))
        assertSame(safe, cameraContentInsets(enabled, false, 1, safe, bars, ime))
        assertSame(safe, cameraContentInsets(enabled.copy(avoidCameraOverlap = false), true, 0, safe, bars, ime))
    }
}
