package com.takeruf.nagi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
fun ThemeColorEditor(current: Long, onDismiss: () -> Unit, onPreview: (Long) -> Unit = {}, onSave: (Long) -> Unit) {
    val strings = rememberNagiStrings()
    var hex by remember { mutableStateOf("%06X".format(current and 0xFFFFFF)) }
    val valid = hex.removePrefix("#").matches(Regex("[0-9a-fA-F]{6}"))
    LaunchedEffect(hex) {
        if (valid) onPreview(0xFF000000 or hex.removePrefix("#").toLong(16))
    }
    AlertDialog(onDismissRequest = onDismiss, title = { NagiSystemBars(); Text(strings(R.string.ui_custom_theme_color)) },
        text = {
            OutlinedTextField(hex, { hex = it }, label = { Text(strings(R.string.ui_hex_color)) }, singleLine = true,
                isError = !valid, supportingText = { Text(strings(R.string.ui_six_hexadecimal_digits_for_example_3568c0)) },
                leadingIcon = {
                    if (valid) Surface(Modifier.size(24.dp), shape = CircleShape,
                        color = Color(0xFF000000 or hex.removePrefix("#").toLong(16)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {}
                })
        }, confirmButton = { TextButton(shape = NagiShapes.Rounded, enabled = valid, onClick = {
            onSave(0xFF000000 or hex.removePrefix("#").toLong(16))
        }) { Text(strings(R.string.ui_save)) } }, dismissButton = { TextButton(shape = NagiShapes.Rounded, onClick = onDismiss) { Text(strings(R.string.ui_cancel)) } })
}
