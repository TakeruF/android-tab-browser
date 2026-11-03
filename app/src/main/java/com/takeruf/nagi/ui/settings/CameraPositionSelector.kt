package com.takeruf.nagi.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes

@Composable
internal fun CameraPositionSelector(leftRotation: Int?, rotation: Int, landscape: Boolean, onSelect: (Int) -> Unit) {
    val strings = rememberNagiStrings()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("settings-camera-position"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(strings(R.string.ui_camera_position), style = MaterialTheme.typography.titleSmall)
        Text(strings(if (landscape) R.string.ui_camera_position_hint else R.string.ui_camera_position_rotate),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Row(Modifier.widthIn(max = 480.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(true, false).forEach { left ->
                val registeredLeftRotation = if (left) rotation else (rotation + 2) % 4
                val selected = landscape && leftRotation == registeredLeftRotation
                Surface(shape = NagiShapes.Rounded,
                    color = if (selected) colors.secondaryContainer else colors.surface,
                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) colors.primary else colors.outlineVariant),
                    modifier = Modifier.weight(1f).testTag(if (left) "camera-position-left" else "camera-position-right")
                        .selectable(selected = selected, enabled = landscape, role = Role.RadioButton,
                            onClick = { onSelect(registeredLeftRotation) })) {
                    Column(Modifier.padding(12.dp).alpha(if (landscape) 1f else 0.45f),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp, 52.dp).border(1.dp, colors.onSurfaceVariant, NagiShapes.Rounded)
                            .padding(horizontal = 6.dp)) {
                            Box(Modifier.size(7.dp).align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                                .background(colors.onSurface, NagiShapes.Rounded))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, enabled = landscape, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings(if (left) R.string.ui_camera_position_left else R.string.ui_camera_position_right))
                        }
                    }
                }
            }
        }
        Text(strings(R.string.ui_camera_position_behavior), style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
    }
}
