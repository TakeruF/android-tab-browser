package com.orbit.browser

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.orbit.browser.ui.theme.themeColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    @Test fun customColorsKeepTextReadableAcrossThemes() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFFF00, 0xFF3568C0, 0xFF8059B1, 0xFFB4496B, 0xFFC76D26, 0xFF64748B)) {
                val colors = themeColorScheme(dark, seed)
                fun check(a: Color, b: Color) {
                    val ratio = (maxOf(a.luminance(), b.luminance()) + .05f) /
                        (minOf(a.luminance(), b.luminance()) + .05f)
                    assertTrue("$seed dark=$dark contrast=$ratio", ratio >= 4.5f)
                }
                check(colors.onSurface, colors.background)
                check(colors.onSurface, colors.surface)
                check(colors.onSurfaceVariant, colors.surfaceContainerHighest)
                check(colors.primary, colors.surface)
                check(colors.primary, colors.surfaceContainerLow)
                check(colors.onPrimary, colors.primary)
                check(colors.onPrimaryContainer, colors.primaryContainer)
                check(colors.inversePrimary, colors.inverseSurface)
            }
        }
    }
}
