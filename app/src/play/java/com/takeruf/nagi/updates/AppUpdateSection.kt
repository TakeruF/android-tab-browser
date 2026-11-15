package com.takeruf.nagi.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.BuildConfig
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes

@Composable
fun AppUpdateSection(@Suppress("UNUSED_PARAMETER") updates: AppUpdates,
    @Suppress("UNUSED_PARAMETER") onInstall: () -> Unit, showVersion: Boolean = true) {
    val strings = rememberNagiStrings()
    val uriHandler = LocalUriHandler.current
    val openPlay = { uriHandler.openUri("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}") }
    // Settings › About shows updates as one row of its card list; tapping it opens Google Play.
    if (!showVersion) {
        Column(Modifier.fillMaxWidth().testTag("app-updates").clickable(onClickLabel = strings(R.string.ui_open_google_play), onClick = openPlay)
            .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(strings(R.string.ui_app_updates))
            Text(strings(R.string.ui_play_updates), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Surface(shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("app-updates")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings(R.string.ui_app_updates), style = MaterialTheme.typography.titleMedium)
            if (showVersion) Text(strings(R.string.ui_current_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
            Text(strings(R.string.ui_play_updates))
            TextButton(onClick = openPlay) {
                Text(strings(R.string.ui_open_google_play))
            }
        }
    }
}
