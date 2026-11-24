package com.takeruf.nagi.ui.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeruf.nagi.R
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.NewTabBackground
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.components.ThemeColorEditor
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
fun NewTabAppearanceDialog(settings: BrowserSettings, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val strings = rememberNagiStrings()
    val dimensions = LocalNagiUiDimensions.current
    val importing by vm.importingNewTabPhoto.collectAsStateWithLifecycle()
    var editingColor by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importNewTabPhoto)
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val photoActionLabel = strings(R.string.ui_new_tab_choose_photo)
    val colorActionLabel = strings(R.string.ui_background_color)
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("new-tab-appearance-dialog"),
        title = { NagiSystemBars(); Text(strings(R.string.ui_new_tab_background)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(dimensions.sectionGap)) {
                val previewLabel = strings(R.string.ui_new_tab_home)
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("new-tab-preview").clearAndSetSemantics {
                    contentDescription = previewLabel
                }) {
                    Surface(shape = NagiShapes.Rounded, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().height((maxWidth * 0.48f).coerceIn(112.dp, if (dimensions.compact) 136.dp else 176.dp))) {
                        Box(contentAlignment = Alignment.Center) {
                            NewTabBackground(settings, Modifier.matchParentSize())
                            Surface(shape = NagiShapes.Pill, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 320.dp)) {
                                Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(NagiIcons.Search, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(strings(R.string.ui_search_or_enter_url), style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                val modes = NewTabBackground.entries.filter {
                    it != NewTabBackground.PHOTO || vm.canChooseNewTabPhoto || settings.newTabPhoto != null
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { type ->
                                val opensPicker = type == NewTabBackground.PHOTO && vm.canChooseNewTabPhoto &&
                                    (settings.newTabPhoto == null || settings.newTabBackground == NewTabBackground.PHOTO)
                                val selected = settings.newTabBackground == type
                                Surface(selected = selected, enabled = !importing,
                                    shape = NagiShapes.Rounded,
                                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.weight(1f).testTag("new-tab-option-${type.name}").semantics {
                                        if (opensPicker) onClick(label = photoActionLabel, action = null)
                                    },
                                    onClick = {
                                        if (opensPicker) pickPhoto()
                                        else vm.updateSettings { it.copy(newTabBackground = type) }
                                    }) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(shape = NagiShapes.Rounded,
                                            modifier = Modifier.fillMaxWidth().height(if (dimensions.compact) 28.dp else 36.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                NewTabBackground(settings.copy(newTabBackground = type), Modifier.matchParentSize())
                                                if (type == NewTabBackground.PHOTO && settings.newTabPhoto == null)
                                                    Icon(NagiIcons.Plus, null, Modifier.size(18.dp))
                                            }
                                        }
                                        Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(strings(when (type) {
                                                NewTabBackground.DEFAULT -> R.string.ui_new_tab_default
                                                NewTabBackground.SOLID -> R.string.ui_new_tab_solid
                                                NewTabBackground.GRADIENT -> R.string.ui_new_tab_gradient
                                                NewTabBackground.PHOTO -> R.string.ui_new_tab_photo
                                            }), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                            if (selected) Icon(NagiIcons.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (settings.newTabBackground == NewTabBackground.SOLID || settings.newTabBackground == NewTabBackground.GRADIENT) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings(R.string.ui_background_color), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(enabled = !importing, onClick = { editingColor = true }, shape = NagiShapes.Rounded,
                                modifier = Modifier.testTag("new-tab-custom-color").semantics {
                                    contentDescription = colorActionLabel
                                    stateDescription = "#%06X".format(settings.newTabColor and 0xFFFFFF)
                                }) {
                                Text("#%06X".format(settings.newTabColor and 0xFFFFFF))
                                Spacer(Modifier.width(6.dp))
                                Icon(NagiIcons.Pencil, null, Modifier.size(16.dp))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0xFF426B5A, 0xFF4676A0, 0xFF79659A, 0xFFAD715C, 0xFF313A40).forEach { color ->
                                val label = strings(R.string.ui_hex_color) + " #%06X".format(color and 0xFFFFFF)
                                val selected = settings.newTabColor == color
                                Surface(selected = selected, onClick = { vm.updateSettings { it.copy(newTabColor = color) } }, enabled = !importing,
                                    shape = CircleShape, color = Color.Transparent,
                                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.size(dimensions.controlSize).testTag("new-tab-color-%06X".format(color and 0xFFFFFF))
                                        .semantics { contentDescription = label }) {
                                    Surface(shape = CircleShape, color = Color(color), contentColor = Color.White,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.padding(5.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (selected) Icon(NagiIcons.Check, null, Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (vm.canChooseNewTabPhoto && (settings.newTabBackground == NewTabBackground.PHOTO || importing)) {
                    Text(strings(R.string.ui_new_tab_photo_description), style = MaterialTheme.typography.bodySmall)
                }
                if (importing) {
                    Text(strings(R.string.ui_new_tab_importing), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(strings(R.string.ui_new_tab_done)) } })
    if (editingColor) ThemeColorEditor(settings.newTabColor, onDismiss = { editingColor = false },
        title = strings(R.string.ui_background_color), onSave = { color ->
            vm.updateSettings { it.copy(newTabColor = color) }; editingColor = false
        })
}
