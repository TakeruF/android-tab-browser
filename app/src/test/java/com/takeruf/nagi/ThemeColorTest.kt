package com.takeruf.nagi

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.takeruf.nagi.ui.theme.Lch
import com.takeruf.nagi.ui.theme.ThemePreset
import com.takeruf.nagi.ui.theme.ThemeVariant
import com.takeruf.nagi.ui.theme.themeColorScheme
import com.takeruf.nagi.ui.theme.themeSeedColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {
    @Test fun surfaceHueComesFromSelectedColorWithoutResidualGreen() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(0xFFCC3333, 0xFF3366CC, 0xFF888888)) {
                val colors = themeColorScheme(dark, seed)
                val seedHue = Lch.from(Color(seed)).hue
                val surfaces = listOf(colors.background, colors.surface, colors.surfaceVariant,
                    colors.surfaceDim, colors.surfaceBright, colors.surfaceContainerLowest,
                    colors.surfaceContainerLow, colors.surfaceContainer, colors.surfaceContainerHigh,
                    colors.surfaceContainerHighest, colors.outline, colors.outlineVariant, colors.inverseSurface)
                for (surface in surfaces) {
                    if (seed == 0xFF888888) {
                        assertTrue("Gray theme retains a color cast: $surface",
                            kotlin.math.abs(surface.red - surface.green) < 0.0001f &&
                                kotlin.math.abs(surface.green - surface.blue) < 0.0001f)
                    } else {
                        val lch = Lch.from(surface)
                        // 8-bit rounding leaves near-white tones without a measurable hue.
                        if (lch.chroma < 1.5f) continue
                        val distance = kotlin.math.abs(((lch.hue - seedHue + 540f) % 360f) - 180f)
                        assertTrue("$seed dark=$dark surface $surface hue ${lch.hue} vs ${seedHue}", distance < 25f)
                    }
                }
            }
        }
    }

    @Test fun selectionAndAccentFollowMaterialToneAndChroma() {
        for (dark in listOf(false, true)) {
            for (seed in listOf(0xFF426B5A, 0xFF3568C0, 0xFFFFFF00, 0xFF00FF66, 0xFFB4496B, 0xFFC76D26)) {
                val colors = themeColorScheme(dark, seed)
                val selection = Lch.from(colors.secondaryContainer)
                // Tonal-spot secondary: muted (chroma 16) at tone 90 / 30, never a fluorescent fill.
                assertTrue("$seed dark=$dark selection chroma ${selection.chroma}", selection.chroma <= 17f)
                assertTrue("$seed dark=$dark selection tone ${selection.lightness}",
                    kotlin.math.abs(selection.lightness - if (dark) 30f else 90f) < 1.5f)
                assertTrue("$seed dark=$dark primary chroma", Lch.from(colors.primary).chroma <= 65f)
                assertTrue("$seed dark=$dark surface chroma", Lch.from(colors.surfaceContainer).chroma <= 7f)
                fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) /
                    (minOf(a.luminance(), b.luminance()) + .05f)
                // Container text sits 80 tones away: WCAG AAA (7:1) for selected labels.
                assertTrue("$seed dark=$dark selected label", ratio(colors.onSecondaryContainer, colors.secondaryContainer) >= 7f)
                assertTrue("$seed dark=$dark body on selection", ratio(colors.onSurface, colors.secondaryContainer) >= 4.5f)
                assertTrue("$seed dark=$dark on primary", ratio(colors.onPrimary, colors.primary) >= 4.5f)
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

    @Test fun presetsCoverHuesAndMutedVariantsWithReadableRoles() {
        // Earlier presets stay selectable so saved Space colors keep their check mark.
        for (legacy in listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96, 0xFF995C77, 0xFF687081)) {
            assertTrue("missing $legacy", ThemePreset.of(legacy) != null)
        }
        assertEquals(15, ThemePreset.entries.size)
        assertEquals(15, ThemePreset.entries.map { it.color }.toSet().size)
        val muted = setOf(ThemePreset.SAGE, ThemePreset.SLATE, ThemePreset.CLAY, ThemePreset.GREY)
        val vivid = setOf(ThemePreset.BLUE, ThemePreset.RED, ThemePreset.CITRON)
        for (preset in ThemePreset.entries) {
            val variant = ThemeVariant.of(Lch.from(Color(preset.color)).chroma)
            when (preset) {
                in muted -> assertEquals(preset.name, ThemeVariant.NEUTRAL, variant)
                in vivid -> assertEquals(preset.name, ThemeVariant.VIBRANT, variant)
                else -> assertEquals(preset.name, ThemeVariant.TONAL_SPOT, variant)
            }
            for (dark in listOf(false, true)) {
                val colors = themeColorScheme(dark, preset.color)
                fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) /
                    (minOf(a.luminance(), b.luminance()) + .05f)
                assertTrue("${preset.name} dark=$dark selected label", ratio(colors.onSecondaryContainer, colors.secondaryContainer) >= 7f)
                assertTrue("${preset.name} dark=$dark on primary", ratio(colors.onPrimary, colors.primary) >= 4.5f)
                assertTrue("${preset.name} dark=$dark primary on surface", ratio(colors.primary, colors.surface) >= 4.5f)
                assertTrue("${preset.name} dark=$dark selection chroma", Lch.from(colors.secondaryContainer).chroma <= 17f)
            }
        }
        // Muted presets keep surfaces close to gray; vivid ones keep a stronger accent than tonal spot.
        assertTrue(Lch.from(themeColorScheme(false, ThemePreset.SLATE.color).surfaceContainer).chroma <= 3f)
        assertTrue(Lch.from(themeColorScheme(false, ThemePreset.BLUE.color).primary).chroma >
            Lch.from(themeColorScheme(false, ThemePreset.FOREST.color).primary).chroma)
    }

    @Test fun turningSpacesOffKeepsTheCurrentSpaceColor() {
        // Spaces off still uses the current Space, so its color stays; the app-wide color
        // applies only when no Space exists yet.
        assertEquals(0xFF6C6193, themeSeedColor(0xFF6C6193, fallback = 0xFF426B5A))
        assertEquals(0xFF426B5A, themeSeedColor(null, fallback = 0xFF426B5A))
    }
}
