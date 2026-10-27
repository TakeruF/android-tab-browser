package com.takeruf.nagi.ui.browser

import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.NewTabBackground
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NewTabPage(spaceName: String, onSearch: () -> Unit, compactWindow: Boolean = false,
    settings: BrowserSettings = BrowserSettings(), onCustomize: () -> Unit = {}) {
    val strings = rememberNagiStrings()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NewTabBackground(settings, Modifier.matchParentSize())
        val customized = settings.newTabBackground != NewTabBackground.DEFAULT
        val compact = compactWindow || maxWidth < 320.dp
        Column(Modifier.padding(if (compact) 16.dp else 32.dp).widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = NagiShapes.Rounded, color = if (customized) MaterialTheme.colorScheme.surface.copy(alpha = 0.94f) else Color.Transparent) {
                Text(spaceName, modifier = if (customized) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(20.dp))
            Surface(onClick = onSearch, shape = NagiShapes.Rounded, color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NagiIcons.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (compact) strings(R.string.ui_search) else strings(R.string.ui_where_would_you_like_to_go), Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!compact) Text("Ctrl L", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FilledTonalIconButton(onClick = onCustomize, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            .testTag("customize-new-tab")) {
            Icon(NagiIcons.Palette, strings(R.string.ui_new_tab_background))
        }
    }
}
