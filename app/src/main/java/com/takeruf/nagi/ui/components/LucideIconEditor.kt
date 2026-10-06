package com.takeruf.nagi.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
internal fun LucideIconEditor(current: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val strings = rememberNagiStrings()
    var code by rememberSaveable { mutableStateOf(if (current.startsWith("lucide:")) current.removePrefix("lucide:") else "") }
    val resource = LucideIconCatalog.resource(code)
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("lucide-code-dialog"),
        title = { NagiSystemBars(); Text(strings(R.string.ui_lucide_code_name)) },
        text = {
            OutlinedTextField(code, { code = it }, singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                label = { Text(strings(R.string.ui_icon_code_name)) },
                placeholder = { Text("user-round") },
                isError = code.isNotEmpty() && resource == null,
                supportingText = { Text(strings(if (code.isNotEmpty() && resource == null)
                    R.string.ui_lucide_code_not_found else R.string.ui_lucide_exact_code_hint)) },
                leadingIcon = resource?.let { id -> {
                    Icon(ImageVector.vectorResource(id), strings(R.string.ui_icon_preview), Modifier.size(24.dp))
                } })
        },
        confirmButton = {
            TextButton(shape = NagiShapes.Rounded, enabled = resource != null,
                onClick = { onSelect("lucide:$code") }) { Text(strings(R.string.ui_use_icon)) }
        },
        dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = onDismiss) { Text(strings(R.string.ui_cancel)) } })
}
