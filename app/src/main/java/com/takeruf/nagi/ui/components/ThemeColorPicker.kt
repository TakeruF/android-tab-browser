package com.takeruf.nagi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.ThemePreset
import com.takeruf.nagi.ui.theme.themeColorScheme

/**
 * Chrome-style theme grid: each tile previews the tonal roles a seed produces
 * (container on top, accent bottom-left, light accent bottom-right). The last tile opens the HEX editor.
 */
@Composable
fun ThemeColorPicker(
    selected: Long,
    onSelect: (Long) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "space-color-",
) {
    val strings = rememberNagiStrings()
    val preset = ThemePreset.of(selected)
    val tiles: List<ThemePreset?> = ThemePreset.entries + listOf(null)
    // Fill the available width with 4, 8 or 16 columns (each divides the 16 tiles evenly, so no row
    // is left partly empty). Prefer the fewest columns whose tiles stay within 48-96dp; on very wide
    // panes fall back to the most columns that keep a 48dp touch target.
    BoxWithConstraints(modifier.fillMaxWidth()) {
    val gap = 8.dp
    fun tile(columns: Int) = (maxWidth - gap * (columns - 1)) / columns
    val columns = listOf(4, 8, 16).firstOrNull { tile(it) in 48.dp..96.dp }
        ?: listOf(16, 8).firstOrNull { tile(it) >= 48.dp } ?: 4
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { item ->
                    val isSelected = if (item == null) preset == null else item == preset
                    val description = if (item == null) strings(R.string.ui_custom_color_1_s, "%06X".format(selected and 0xFFFFFF))
                        else strings(item.label)
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(NagiShapes.Rounded)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { if (item == null) onCustom() else onSelect(item.color) }
                        .testTag(tagPrefix + (item?.color?.toString() ?: "custom"))
                        .semantics { this.selected = isSelected; contentDescription = description }
                        .padding(8.dp), contentAlignment = Alignment.Center) {
                        if (item != null) ThemeSwatch(item.color, Modifier.fillMaxSize())
                        else Box(Modifier.fillMaxSize().clip(CircleShape)
                            .background(if (preset == null) Color(selected or 0xFF000000) else MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center) {
                            val tint = if (preset == null) themeColorScheme(false, selected).onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            Icon(NagiIcons.Palette, null, Modifier.size(20.dp), tint = tint)
                        }
                        if (isSelected) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(NagiIcons.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    }
}

/** Three tonal roles of [seed] in one circle, always shown from the light scheme so swatches look alike in both themes. */
@Composable
private fun ThemeSwatch(seed: Long, modifier: Modifier) {
    val scheme = remember(seed) { themeColorScheme(false, seed) }
    Canvas(modifier.clip(CircleShape)) {
        val diameter = minOf(size.width, size.height)
        val arcSize = Size(diameter, diameter)
        drawArc(scheme.primaryContainer, 180f, 180f, useCenter = true, size = arcSize)
        drawArc(scheme.primary, 90f, 90f, useCenter = true, size = arcSize)
        drawArc(scheme.inversePrimary, 0f, 90f, useCenter = true, size = arcSize)
    }
}
