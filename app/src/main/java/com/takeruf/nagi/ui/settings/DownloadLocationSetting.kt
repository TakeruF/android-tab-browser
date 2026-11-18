package com.takeruf.nagi.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.downloads.DownloadFolder
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.ui.theme.NagiSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

/**
 * Settings › General › Download location: the system Downloads folder (default) or a folder
 * picked with the system folder picker. The grant is persisted so downloads keep working
 * after restarts, and released when the folder is replaced or reset.
 */
@Composable
internal fun DownloadLocationSetting(folder: String?, onChange: (String?, (String) -> Unit) -> Unit) {
    val strings = rememberNagiStrings()
    // Completion callbacks can outlive this Settings composition or its Activity.
    val context = LocalContext.current.applicationContext
    var choosing by remember { mutableStateOf(false) }
    fun release(previous: String) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(previous), GRANT) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && runCatching { context.contentResolver.takePersistableUriPermission(uri, GRANT) }.isSuccess) {
            onChange(uri.toString(), ::release)
        }
    }
    // Name and availability are read off the main thread; a revoked or deleted folder falls back to Downloads.
    val state by produceState<Pair<String?, Boolean>>(null to true, folder) {
        value = if (folder == null) null to true else withContext(Dispatchers.IO) {
            val uri = Uri.parse(folder)
            (DownloadFolder.displayName(context, uri) ?: uri.lastPathSegment) to DownloadFolder.writable(context, uri)
        }
    }
    val (name, available) = state
    val subtitle = when {
        folder == null -> strings(R.string.ui_download_location_default)
        !available -> strings(R.string.ui_download_location_unavailable, name.orEmpty())
        else -> name.orEmpty()
    }
    SettingsAction(strings(R.string.ui_download_location), Modifier.testTag("download-location"), subtitle = subtitle) { choosing = true }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { NagiSystemBars(); Text(strings(R.string.ui_download_location)) },
        text = {
            Column(Modifier.selectableGroup()) {
                @Composable
                fun Option(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected, onClick = null)
                        Text(label)
                    }
                }
                Option(strings(R.string.ui_download_location_default_option), folder == null, "download-location-default") {
                    onChange(null, ::release)
                    choosing = false
                }
                if (folder != null) Option(name.orEmpty(), true, "download-location-folder") { choosing = false }
                TextButton(onClick = { choosing = false; picker.launch(folder?.let(Uri::parse)) }, shape = NagiShapes.Rounded,
                    modifier = Modifier.padding(top = 8.dp).testTag("download-location-choose")) {
                    Text(strings(R.string.ui_download_location_choose))
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = false }, shape = NagiShapes.Rounded) { Text(strings(R.string.ui_cancel)) } })
}
