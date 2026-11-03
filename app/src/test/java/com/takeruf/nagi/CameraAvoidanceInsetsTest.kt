package com.takeruf.nagi

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.ui.cameraAvoidanceInsets
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraAvoidanceInsetsTest {
    private val density = Density(2f)
    private val enabled = BrowserSettings(avoidCameraOverlap = true, cameraSideMargin = 32)

    @Test fun offAndPortraitKeepExistingInsets() {
        assertEquals(0, cameraAvoidanceInsets(BrowserSettings(), true).getLeft(density, LayoutDirection.Ltr))
        assertEquals(0, cameraAvoidanceInsets(enabled, false).getLeft(density, LayoutDirection.Ltr))
    }

    @Test fun unreportedCameraGetsBothMarginsInEitherLayoutDirection() {
        val insets = cameraAvoidanceInsets(enabled, true)
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            assertEquals(64, insets.getLeft(density, direction))
            assertEquals(64, insets.getRight(density, direction))
        }
        assertEquals(0, insets.getTop(density))
        assertEquals(0, insets.getBottom(density))
    }

    @Test fun reportedCutoutAndSystemInsetsArePreservedWithoutDoublePadding() {
        val safe = WindowInsets(left = 90, top = 24, right = 20, bottom = 48)
        val insets = safe.union(cameraAvoidanceInsets(enabled, true))
        assertEquals(90, insets.getLeft(density, LayoutDirection.Ltr))
        assertEquals(64, insets.getRight(density, LayoutDirection.Ltr))
        assertEquals(24, insets.getTop(density))
        assertEquals(48, insets.getBottom(density))
    }
}
