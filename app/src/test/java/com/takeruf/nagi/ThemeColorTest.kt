package com.takeruf.nagi

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.takeruf.nagi.ui.theme.themeColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    @Test fun surfaceHueComesFromSelectedColorWithoutResidualGreen() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(0xFFCC3333, 0xFF3366CC, 0xFF888888)) {
                val colors = themeColorScheme(dark, seed)
                val surfaces = listOf(colors.background, colors.surface, colors.surfaceVariant,
                    colors.surfaceDim, colors.surfaceBright, colors.surfaceContainerLowest,
                    colors.surfaceContainerLow, colors.surfaceContainer, colors.surfaceContainerHigh,
                    colors.surfaceContainerHighest, colors.outline, colors.outlineVariant, colors.inverseSurface)
                for (surface in surfaces) {
                    when (seed) {
                        0xFFCC3333 -> {
                            assertTrue("Red theme retains green: $surface", surface.red > surface.green)
                            assertTrue("Red theme retains blue: $surface", surface.red > surface.blue)
                        }
                        0xFF3366CC -> assertTrue("Blue theme retains green: $surface", surface.blue > surface.green && surface.green > surface.red)
                        else -> assertTrue("Gray theme retains a color cast: $surface",
                            kotlin.math.abs(surface.red - surface.green) < 0.0001f &&
                                kotlin.math.abs(surface.green - surface.blue) < 0.0001f)
                    }
                }
            }
        }
    }

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
