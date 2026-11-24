package com.takeruf.nagi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import android.app.Activity
import com.takeruf.nagi.R
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.domain.model.CompactUiMode
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.components.NagiUiDimensions
import com.takeruf.nagi.ui.components.hardwareKeyboardConnected

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

/** Effective app theme, including system changes, independent of animated palette colors. */
val LocalNagiDarkTheme = staticCompositionLocalOf { false }
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
internal fun NagiSystemBars(dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f) {
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
fun NagiTheme(mode: ThemeMode, themeColor: Long = 0xFF426B5A,
    compactUiMode: CompactUiMode = CompactUiMode.AUTO, content: @Composable () -> Unit) {
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM && isSystemInDarkTheme()
    NagiSystemBars(dark)
    // Retarget from the current color on rapid switches. Compose respects animator duration scale.
    val seed by animateColorAsState(Color(themeColor or 0xFF000000),
        animationSpec = tween(250, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)), label = "space-theme")
    val dimensions = NagiUiDimensions(compactUiMode.enabled(hardwareKeyboardConnected()))
    CompositionLocalProvider(LocalNagiDarkTheme provides dark,
        LocalNagiUiDimensions provides dimensions,
        LocalMinimumInteractiveComponentSize provides dimensions.controlSize) {
        MaterialTheme(colorScheme = themeColorScheme(dark, seed.toArgb().toLong()), shapes = NagiShapes.Material, content = content)
    }
}

/**
 * The color the app is themed with: the current Space's, whether or not Spaces are shown.
 * Turning Spaces off keeps using that Space, so it must not switch to another color.
 * The app-wide [fallback] applies only before any Space exists.
 */
fun themeSeedColor(currentSpaceColor: Long?, fallback: Long): Long = currentSpaceColor ?: fallback

// Keep user colors intact in storage; derive displayed roles from them.
// Follows the Material 3 tonal-spot recipe used by Android dynamic color and Chrome:
// each role is a fixed tone (perceptual lightness, CIE L*) of the Space hue at a fixed
// chroma, so contrast between paired roles is guaranteed by their tone distance.
// Selection uses the low-chroma secondary container rather than a saturated accent.
internal fun themeColorScheme(dark: Boolean, themeColor: Long): ColorScheme {
    val original = if (dark) DarkColors else LightColors
    val seed = Lch.from(Color(themeColor or 0xFF000000))
    // Near-gray seeds produce an exactly neutral scheme.
    val chromatic = seed.chroma >= 6f
    val hue = seed.hue
    fun palette(chroma: Float, hueShift: Float = 0f): (Float) -> Color =
        { tone -> Lch.toColor(tone, if (chromatic) chroma else 0f, hue + hueShift) }
    // The seed's own chroma picks the Material variant, like Chrome's theme presets:
    // muted seeds stay muted (neutral), mid seeds use tonal spot, vivid seeds keep a vivid accent.
    // Selection (secondary) stays low-chroma in every variant.
    val variant = ThemeVariant.of(seed.chroma)
    val primary = palette(variant.primary(seed.chroma))
    val secondary = palette(variant.secondary)
    val tertiary = palette(variant.tertiary, 60f)
    val neutral = palette(variant.neutral)
    val neutralVariant = palette(variant.neutralVariant)
    val error = { tone: Float -> Lch.toColor(tone, 60f, 30f) }
    fun t(light: Float, darkTone: Float) = if (dark) darkTone else light
    val scheme = original.copy(
        primary = primary(t(40f, 80f)), onPrimary = primary(t(100f, 20f)),
        primaryContainer = primary(t(90f, 30f)), onPrimaryContainer = primary(t(10f, 90f)),
        inversePrimary = primary(t(80f, 40f)), surfaceTint = primary(t(40f, 80f)),
        secondary = secondary(t(40f, 80f)), onSecondary = secondary(t(100f, 20f)),
        secondaryContainer = secondary(t(90f, 30f)), onSecondaryContainer = secondary(t(10f, 90f)),
        tertiary = tertiary(t(40f, 80f)), onTertiary = tertiary(t(100f, 20f)),
        tertiaryContainer = tertiary(t(90f, 30f)), onTertiaryContainer = tertiary(t(10f, 90f)),
        error = error(t(40f, 80f)), onError = error(t(100f, 20f)),
        errorContainer = error(t(90f, 30f)), onErrorContainer = error(t(10f, 90f)),
        // The app frame (sidebar and margins) sits one container step away from page surfaces.
        background = neutral(t(94f, 12f)), onBackground = neutral(t(10f, 90f)),
        surface = neutral(t(98f, 6f)), onSurface = neutral(t(10f, 90f)),
        surfaceVariant = neutralVariant(t(90f, 30f)), onSurfaceVariant = neutralVariant(t(30f, 80f)),
        surfaceDim = neutral(t(87f, 6f)), surfaceBright = neutral(t(98f, 24f)),
        surfaceContainerLowest = neutral(t(99f, 4f)), surfaceContainerLow = neutral(t(96f, 10f)),
        surfaceContainer = neutral(t(94f, 12f)), surfaceContainerHigh = neutral(t(92f, 17f)),
        surfaceContainerHighest = neutral(t(90f, 22f)),
        inverseSurface = neutral(t(20f, 90f)), inverseOnSurface = neutral(t(95f, 20f)),
        outline = neutralVariant(t(50f, 60f)), outlineVariant = neutralVariant(t(80f, 30f)),
        scrim = Color.Black,
    )
    return scheme
}

internal enum class ThemeVariant(val secondary: Float, val tertiary: Float, val neutral: Float, val neutralVariant: Float) {
    NEUTRAL(8f, 12f, 2f, 3f), TONAL_SPOT(16f, 24f, 6f, 8f), VIBRANT(16f, 24f, 6f, 8f);

    fun primary(seedChroma: Float) = when (this) {
        NEUTRAL -> 12f
        TONAL_SPOT -> 36f
        VIBRANT -> seedChroma.coerceAtMost(64f)
    }

    companion object {
        fun of(seedChroma: Float) = when {
            seedChroma < 14f -> NEUTRAL
            seedChroma < 48f -> TONAL_SPOT
            else -> VIBRANT
        }
    }
}

/** Curated seeds for the theme color grid, ordered by hue with muted variants beside vivid ones. */
internal enum class ThemePreset(val color: Long, val label: Int) {
    FOREST(0xFF426B5A, R.string.ui_theme_preset_forest), SAGE(0xFF6C7B6D, R.string.ui_theme_preset_sage),
    LEAF(0xFF577C37, R.string.ui_theme_preset_leaf), TEAL(0xFF157E81, R.string.ui_theme_preset_teal),
    OCEAN(0xFF477F96, R.string.ui_theme_preset_ocean), BLUE(0xFF1761C0, R.string.ui_theme_preset_blue),
    SLATE(0xFF687081, R.string.ui_theme_preset_slate), VIOLET(0xFF6C6193, R.string.ui_theme_preset_violet),
    ORCHID(0xFF93548C, R.string.ui_theme_preset_orchid), ROSE(0xFF995C77, R.string.ui_theme_preset_rose),
    RED(0xFFB34746, R.string.ui_theme_preset_red), AMBER(0xFFB07D47, R.string.ui_theme_preset_amber),
    CITRON(0xFF8E862C, R.string.ui_theme_preset_citron), CLAY(0xFF867369, R.string.ui_theme_preset_clay),
    GREY(0xFF777777, R.string.ui_theme_preset_grey);

    companion object {
        fun of(color: Long) = entries.firstOrNull { it.color == (color or 0xFF000000) }
    }
}

/** CIE L*C*h(ab) under D65; L* is the Material 3 "tone". Pure Kotlin so JVM tests can run it. */
internal object Lch {
    data class Value(val lightness: Float, val chroma: Float, val hue: Float)

    private const val Xn = 0.95047f
    private const val Yn = 1f
    private const val Zn = 1.08883f

    private fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    private fun gamma(c: Float) = if (c <= 0.0031308f) c * 12.92f else (1.055f * Math.pow(c.toDouble(), 1 / 2.4).toFloat() - 0.055f)
    private fun f(t: Float) = if (t > 216f / 24389f) Math.cbrt(t.toDouble()).toFloat() else (24389f / 27f * t + 16f) / 116f
    private fun fInv(t: Float) = if (t * t * t > 216f / 24389f) t * t * t else (116f * t - 16f) / (24389f / 27f)

    fun from(color: Color): Value {
        val r = linear(color.red); val g = linear(color.green); val b = linear(color.blue)
        val x = 0.4124f * r + 0.3576f * g + 0.1805f * b
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = 0.0193f * r + 0.1192f * g + 0.9505f * b
        val fx = f(x / Xn); val fy = f(y / Yn); val fz = f(z / Zn)
        val a = 500f * (fx - fy); val bb = 200f * (fy - fz)
        val hue = Math.toDegrees(kotlin.math.atan2(bb, a).toDouble()).toFloat().let { if (it < 0) it + 360f else it }
        return Value(116f * fy - 16f, kotlin.math.hypot(a, bb), hue)
    }

    /** Neutral gray at [tone], exactly r = g = b. */
    private fun gray(tone: Float): Color {
        val v = gamma(fInv((tone + 16f) / 116f)).coerceIn(0f, 1f)
        return Color(v, v, v)
    }

    private fun rgb(tone: Float, chroma: Float, hue: Float): FloatArray {
        val rad = Math.toRadians(hue.toDouble())
        val a = chroma * kotlin.math.cos(rad).toFloat(); val b = chroma * kotlin.math.sin(rad).toFloat()
        val fy = (tone + 16f) / 116f
        val x = Xn * fInv(fy + a / 500f); val y = Yn * fInv(fy); val z = Zn * fInv(fy - b / 200f)
        return floatArrayOf(
            gamma(3.2406f * x - 1.5372f * y - 0.4986f * z),
            gamma(-0.9689f * x + 1.8758f * y + 0.0415f * z),
            gamma(0.0557f * x - 0.2040f * y + 1.0570f * z))
    }

    /** The color at [tone] and [hue], reducing chroma until it fits in sRGB. */
    fun toColor(tone: Float, chroma: Float, hue: Float): Color {
        if (chroma <= 0f) return gray(tone)
        fun inGamut(c: FloatArray) = c.all { it in -0.0005f..1.0005f }
        val full = rgb(tone, chroma, hue)
        val fitted = if (inGamut(full)) full else {
            var low = 0f; var high = chroma
            repeat(18) { val mid = (low + high) / 2f; if (inGamut(rgb(tone, mid, hue))) low = mid else high = mid }
            if (low < 0.01f) return gray(tone)
            rgb(tone, low, hue)
        }
        return Color(fitted[0].coerceIn(0f, 1f), fitted[1].coerceIn(0f, 1f), fitted[2].coerceIn(0f, 1f))
    }
}
