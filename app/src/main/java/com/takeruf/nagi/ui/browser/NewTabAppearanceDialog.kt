package com.takeruf.nagi.ui.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeruf.nagi.R
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.NewTabBackground
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.ThemeColorEditor
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
fun NewTabAppearanceDialog(settings: BrowserSettings, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val strings = rememberNagiStrings()
    val importing by vm.importingNewTabPhoto.collectAsStateWithLifecycle()
    var editingColor by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importNewTabPhoto)
    }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { NagiSystemBars(); Text(strings(R.string.ui_new_tab_background)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = NagiShapes.Rounded, modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    NewTabBackground(settings, Modifier.fillMaxSize())
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewTabBackground.entries.forEach { type ->
                        if (type != NewTabBackground.PHOTO || vm.canChooseNewTabPhoto || settings.newTabPhoto != null) {
                            FilterChip(selected = settings.newTabBackground == type, enabled = !importing,
                                modifier = Modifier.testTag("new-tab-option-${type.name}"),
                                onClick = {
                                    if (type == NewTabBackground.PHOTO && settings.newTabPhoto == null) pickPhoto()
                                    else vm.updateSettings { it.copy(newTabBackground = type) }
                                }, label = { Text(strings(when (type) {
                                    NewTabBackground.DEFAULT -> R.string.ui_new_tab_default
                                    NewTabBackground.SOLID -> R.string.ui_new_tab_solid
                                    NewTabBackground.GRADIENT -> R.string.ui_new_tab_gradient
                                    NewTabBackground.PHOTO -> R.string.ui_new_tab_photo
                                })) })
                        }
                    }
                }
                if (settings.newTabBackground == NewTabBackground.SOLID || settings.newTabBackground == NewTabBackground.GRADIENT) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0xFF426B5A, 0xFF4676A0, 0xFF79659A, 0xFFAD715C, 0xFF313A40).forEach { color ->
                            val label = strings(R.string.ui_hex_color) + " #%06X".format(color and 0xFFFFFF)
                            Surface(onClick = { vm.updateSettings { it.copy(newTabColor = color) } }, enabled = !importing,
                                shape = CircleShape, color = Color(color), contentColor = Color.White,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.size(48.dp).semantics {
                                    contentDescription = label; selected = settings.newTabColor == color
                                }) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (settings.newTabColor == color) Icon(NagiIcons.Check, null, Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    TextButton(enabled = !importing, onClick = { editingColor = true }, modifier = Modifier.testTag("new-tab-custom-color")) {
                        Text(strings(R.string.ui_background_color))
                    }
                }
                if (vm.canChooseNewTabPhoto) {
                    OutlinedButton(enabled = !importing, shape = NagiShapes.Rounded, onClick = { pickPhoto() },
                        modifier = Modifier.testTag("new-tab-choose-photo")) {
                        Text(strings(if (importing) R.string.ui_new_tab_importing else R.string.ui_new_tab_choose_photo))
                    }
                    Text(strings(R.string.ui_new_tab_photo_description), style = MaterialTheme.typography.bodySmall)
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(strings(R.string.ui_new_tab_done)) } })
    if (editingColor) ThemeColorEditor(settings.newTabColor, onDismiss = { editingColor = false },
        title = strings(R.string.ui_background_color), onSave = { color ->
            vm.updateSettings { it.copy(newTabColor = color) }; editingColor = false
        })
}
