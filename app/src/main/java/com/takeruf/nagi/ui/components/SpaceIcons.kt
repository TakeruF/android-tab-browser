package com.takeruf.nagi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.R as LucideR
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

// Stable IDs share the existing Space icon column with emoji; no database migration is needed.
private val minimalistIcons = linkedMapOf(
    "house" to LucideR.drawable.lucide_ic_house,
    "briefcase" to LucideR.drawable.lucide_ic_briefcase,
    "book-open" to LucideR.drawable.lucide_ic_book_open,
    "graduation-cap" to LucideR.drawable.lucide_ic_graduation_cap,
    "rocket" to LucideR.drawable.lucide_ic_rocket,
    "palette" to LucideR.drawable.lucide_ic_palette,
    "globe" to LucideR.drawable.lucide_ic_globe,
    "code" to LucideR.drawable.lucide_ic_code,
    "terminal" to LucideR.drawable.lucide_ic_terminal,
    "folder" to LucideR.drawable.lucide_ic_folder,
    "music" to LucideR.drawable.lucide_ic_music,
    "camera" to LucideR.drawable.lucide_ic_camera,
    "heart" to LucideR.drawable.lucide_ic_heart,
    "star" to LucideR.drawable.lucide_ic_star,
    "cloud" to LucideR.drawable.lucide_ic_cloud,
    "sparkles" to LucideR.drawable.lucide_ic_sparkles,
    "coffee" to LucideR.drawable.lucide_ic_coffee,
    "leaf" to LucideR.drawable.lucide_ic_leaf,
    "mountain" to LucideR.drawable.lucide_ic_mountain,
    "plane" to LucideR.drawable.lucide_ic_plane,
    "compass" to LucideR.drawable.lucide_ic_compass,
    "lightbulb" to LucideR.drawable.lucide_ic_lightbulb,
    "zap" to LucideR.drawable.lucide_ic_zap,
    "gamepad-2" to LucideR.drawable.lucide_ic_gamepad_2,
)
private val emojiIcons = listOf("🏠", "💼", "📚", "🎓", "🚀", "🎨", "🌏", "💻", "🧪", "📁", "🎵", "📷",
    "❤️", "⭐", "☁️", "✨", "☕", "🌿", "🏔️", "✈️", "🧭", "💡", "⚡", "🎮")

internal fun defaultSpaceEmoji(value: String): String = when (value) {
    "◉" -> "🏠"
    "▣" -> "💼"
    else -> value
}

@Composable
fun SpaceIcon(value: String, color: Color, modifier: Modifier = Modifier, description: String? = null) {
    val resource = minimalistIcons[value.removePrefix("lucide:")].takeIf { value.startsWith("lucide:") }
    if (resource != null) Icon(ImageVector.vectorResource(resource), description, modifier.size(22.dp), tint = color)
    else {
        val emoji = defaultSpaceEmoji(value)
        val display = if (emoji in listOf("✦", "☁", "♥", "△")) emoji + "\uFE0E" else emoji
        Text(display, modifier, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun SpaceIconPicker(value: String, onSelect: (String) -> Unit) {
    val strings = rememberNagiStrings()
    val palette = MaterialTheme.colorScheme
    var minimalist by rememberSaveable { mutableStateOf(value.startsWith("lucide:")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = palette.primaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { SpaceIcon(value, palette.onPrimaryContainer) }
            }
            Text(strings(R.string.ui_icon), style = MaterialTheme.typography.titleSmall)
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!minimalist, { minimalist = false }, label = { Text(strings(R.string.ui_emoji)) })
            FilterChip(minimalist, { minimalist = true }, label = { Text("Lucide") })
        }
        FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val choices = if (minimalist) minimalistIcons.keys.map { "lucide:$it" } else emojiIcons
            choices.forEach { choice ->
                val selected = value == choice
                val label = strings(R.string.ui_space_icon_1_s, choice.removePrefix("lucide:").replace('-', ' '))
                Surface(shape = RoundedCornerShape(10.dp), color = if (selected) palette.primaryContainer else palette.surfaceContainer,
                    border = BorderStroke(1.dp, if (selected) palette.primary else palette.outlineVariant)) {
                    Box(Modifier.size(48.dp).semantics { contentDescription = label }.selectable(selected, role = Role.RadioButton, onClick = { onSelect(choice) }),
                        contentAlignment = Alignment.Center) {
                        if (minimalist) SpaceIcon(choice, if (selected) palette.onPrimaryContainer else palette.onSurface)
                        else Text(choice, Modifier, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if (!minimalist) OutlinedTextField(if (value.startsWith("lucide:")) "" else value,
            { if (!it.startsWith("lucide:")) onSelect(it) }, label = { Text(strings(R.string.ui_custom_emoji)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
    }
}
