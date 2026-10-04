package com.orbit.browser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.app.Activity
import com.orbit.browser.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF426B5A), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE9DF), onPrimaryContainer = Color(0xFF234334),
    secondary = Color(0xFF6C6193), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E2F2), onSecondaryContainer = Color(0xFF302747),
    tertiary = Color(0xFF805B32), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DFC6), onTertiaryContainer = Color(0xFF392710),
    background = Color(0xFFEDEFEA), onBackground = Color(0xFF26332E),
    surface = Color(0xFFFAFBF8), onSurface = Color(0xFF26332E),
    surfaceVariant = Color(0xFFE5E9E2), onSurfaceVariant = Color(0xFF4E5E54),
    surfaceDim = Color(0xFFD8DDD6), surfaceBright = Color(0xFFFAFBF8),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F5EF),
    surfaceContainer = Color(0xFFECF0E9), surfaceContainerHigh = Color(0xFFE5E9E2),
    surfaceContainerHighest = Color(0xFFDDE3DA), surfaceTint = Color(0xFF426B5A),
    inverseSurface = Color(0xFF2C3630), inverseOnSurface = Color(0xFFF0F5EE), inversePrimary = Color(0xFFADCDB9),
    outline = Color(0xFF6D7D72), outlineVariant = Color(0xFFBCC8BD),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFADCDB9), onPrimary = Color(0xFF153C2B),
    primaryContainer = Color(0xFF2D4D3C), onPrimaryContainer = Color(0xFFD1EAD9),
    secondary = Color(0xFFC8B8E5), onSecondary = Color(0xFF302747),
    secondaryContainer = Color(0xFF473D5A), onSecondaryContainer = Color(0xFFE8E2F2),
    tertiary = Color(0xFFE1BD91), onTertiary = Color(0xFF432D14),
    tertiaryContainer = Color(0xFF5D4224), onTertiaryContainer = Color(0xFFF3DFC6),
    background = Color(0xFF171E1B), onBackground = Color(0xFFE1E9E1),
    surface = Color(0xFF202925), onSurface = Color(0xFFE1E9E1),
    surfaceVariant = Color(0xFF303E35), onSurfaceVariant = Color(0xFFC0CDC3),
    surfaceDim = Color(0xFF171E1B), surfaceBright = Color(0xFF37433C),
    surfaceContainerLowest = Color(0xFF111713), surfaceContainerLow = Color(0xFF202925),
    surfaceContainer = Color(0xFF26322B), surfaceContainerHigh = Color(0xFF303E35),
    surfaceContainerHighest = Color(0xFF3B4A40), surfaceTint = Color(0xFFADCDB9),
    inverseSurface = Color(0xFFE1E9E1), inverseOnSurface = Color(0xFF26332E), inversePrimary = Color(0xFF426B5A),
    outline = Color(0xFF91A596), outlineVariant = Color(0xFF4B5D50),
)

// Space colors are persisted user choices. Adjust only their displayed foreground,
// keeping the original hue for the sidebar background and color picker.
internal fun readableSpaceColor(color: Color, foreground: Color, backgrounds: List<Color>, minimumContrast: Float = 4.5f): Color {
    fun readable(candidate: Color) = backgrounds.all { background ->
        val a = candidate.luminance()
        val b = background.luminance()
        (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= minimumContrast
    }
    if (readable(color)) return color
    var low = 0f
    var high = 1f
    repeat(16) {
        val fraction = (low + high) / 2f
        if (readable(lerp(color, foreground, fraction))) high = fraction else low = fraction
    }
    return lerp(color, foreground, high)
}

@Composable
internal fun OrbitSystemBars(dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: (view.context as? Activity)?.window
        window?.let {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

@Composable
fun OrbitTheme(mode: ThemeMode, themeColor: Long = 0xFF426B5A, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM && isSystemInDarkTheme()
    OrbitSystemBars(dark)
    MaterialTheme(colorScheme = themeColorScheme(dark, themeColor), content = content)
}

// Keep user colors intact in storage; adapt their displayed roles for contrast.
internal fun themeColorScheme(dark: Boolean, themeColor: Long): ColorScheme {
    val original = if (dark) DarkColors else LightColors
    val seed = Color(themeColor or 0xFF000000)
    // Tint all browser chrome with the chosen hue, keeping neutral text readable.
    fun tint(color: Color, amount: Float = 0.09f) = lerp(color, seed, amount)
    val base = original.copy(
        background = tint(original.background), surface = tint(original.surface, 0.05f),
        surfaceVariant = tint(original.surfaceVariant), surfaceDim = tint(original.surfaceDim),
        surfaceBright = tint(original.surfaceBright, 0.05f),
        surfaceContainerLowest = tint(original.surfaceContainerLowest, 0.03f),
        surfaceContainerLow = tint(original.surfaceContainerLow, 0.06f),
        surfaceContainer = tint(original.surfaceContainer), surfaceContainerHigh = tint(original.surfaceContainerHigh),
        surfaceContainerHighest = tint(original.surfaceContainerHighest),
        outline = tint(original.outline), outlineVariant = tint(original.outlineVariant),
    )
    val primary = readableSpaceColor(seed, base.onSurface,
        listOf(base.surface, base.surfaceContainerLow, base.background), minimumContrast = 5f)
    val container = lerp(base.surface, seed, if (dark) 0.25f else 0.16f)
    val onContainer = readableSpaceColor(base.onSurface, if (dark) Color.White else Color.Black, listOf(container), minimumContrast = 5f)
    val onPrimary = if (primary.luminance() > 0.179f) Color.Black else Color.White
    val surfaces = listOf(base.background, base.surface, base.surfaceVariant, base.surfaceContainerLowest,
        base.surfaceContainerLow, base.surfaceContainer, base.surfaceContainerHigh, base.surfaceContainerHighest)
    val foreground = if (dark) Color.White else Color.Black
    return base.copy(onSurface = readableSpaceColor(base.onSurface, foreground, surfaces, 5f),
        onBackground = readableSpaceColor(base.onBackground, foreground, surfaces, 5f),
        onSurfaceVariant = readableSpaceColor(base.onSurfaceVariant, foreground, surfaces, 5f),
        primary = primary, onPrimary = onPrimary, primaryContainer = container,
        onPrimaryContainer = onContainer, surfaceTint = primary,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onContainer,
        inversePrimary = readableSpaceColor(seed, base.inverseOnSurface, listOf(base.inverseSurface)))
}
