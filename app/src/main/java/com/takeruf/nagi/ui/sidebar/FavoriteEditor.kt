package com.takeruf.nagi.ui.sidebar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.domain.model.Bookmark
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars

@Composable
internal fun FavoriteEditor(favorite: Bookmark, favorites: List<Bookmark>, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit) {
    val strings = rememberNagiStrings()
    val name = rememberTextFieldState(initialText = favorite.title)
    val url = rememberTextFieldState(initialText = favorite.url)
    val normalizedUrl = InputResolver.normalizeUrl(url.text.toString().trim())
    val duplicate = normalizedUrl != null && favorites.any {
        it.id != favorite.id && InputResolver.normalizeUrl(it.url) == normalizedUrl
    }
    val urlError = when {
        normalizedUrl == null -> strings(R.string.ui_enter_valid_url)
        duplicate -> strings(R.string.ui_favorite_url_exists)
        else -> null
    }
    val valid = name.text.isNotBlank() && urlError == null
    fun save() {
        if (valid && name.composition == null && url.composition == null)
            onSave(name.text.toString().trim(), requireNotNull(normalizedUrl))
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("favorite-editor"),
        title = { NagiSystemBars(); Text(strings(R.string.ui_edit_favorite)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state = name, label = { Text(strings(R.string.ui_name)) }, shape = NagiShapes.Rounded,
                    lineLimits = TextFieldLineLimits.SingleLine, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = name.text.isBlank(), supportingText = if (name.text.isBlank()) {
                        { Text(strings(R.string.ui_name_is_required)) }
                    } else null, modifier = Modifier.fillMaxWidth().testTag("favorite-name"))
                OutlinedTextField(state = url, label = { Text(strings(R.string.ui_url)) }, shape = NagiShapes.Rounded,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    onKeyboardAction = { save() }, isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("favorite-url"))
            }
        },
        confirmButton = {
            TextButton(onClick = ::save, enabled = valid, shape = NagiShapes.Rounded, modifier = Modifier.testTag("save-favorite")) {
                Text(strings(R.string.ui_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, shape = NagiShapes.Rounded) { Text(strings(R.string.ui_cancel)) } })
}
