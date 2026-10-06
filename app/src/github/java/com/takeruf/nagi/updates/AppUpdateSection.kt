package com.takeruf.nagi.updates

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.takeruf.nagi.BuildConfig
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes

@Composable
fun AppUpdateSection(updates: AppUpdates, onInstall: () -> Unit) {
    val state by updates.state.collectAsStateWithLifecycle()
    val strings = rememberNagiStrings()
    val busy = state.status in setOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING)
    Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("app-updates")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(strings(R.string.ui_app_updates), style = MaterialTheme.typography.titleMedium)
            Text(strings(R.string.ui_current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Text(strings(when (state.status) {
                UpdateStatus.IDLE -> R.string.ui_update_idle
                UpdateStatus.CHECKING -> R.string.ui_update_checking
                UpdateStatus.CURRENT -> R.string.ui_update_current
                UpdateStatus.AVAILABLE -> R.string.ui_update_available
                UpdateStatus.DOWNLOADING -> R.string.ui_update_downloading
                UpdateStatus.READY -> R.string.ui_update_ready
                UpdateStatus.ERROR -> R.string.ui_update_failed
                UpdateStatus.UNSUPPORTED -> R.string.ui_update_unsupported
            }))
            state.release?.let { release ->
                Text("Nagi ${release.versionName}")
                if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            if (state.status == UpdateStatus.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.status == UpdateStatus.DOWNLOADING) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Text("${state.progress}%")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = updates::check, enabled = !busy, shape = NagiShapes.Rounded) { Text(strings(R.string.ui_check_updates)) }
                if (state.status in setOf(UpdateStatus.AVAILABLE, UpdateStatus.ERROR) && state.release != null) {
                    Button(onClick = updates::download, shape = NagiShapes.Rounded) { Text(strings(R.string.ui_download_update)) }
                }
                if (state.status == UpdateStatus.READY) Button(onClick = onInstall, shape = NagiShapes.Rounded) { Text(strings(R.string.ui_install_update)) }
                if (state.status == UpdateStatus.DOWNLOADING) TextButton(onClick = updates::cancel) { Text(strings(R.string.ui_cancel)) }
            }
            Text(strings(R.string.ui_update_install_description), style = MaterialTheme.typography.bodySmall)
        }
    }
}
