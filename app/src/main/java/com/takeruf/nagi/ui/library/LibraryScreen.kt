package com.takeruf.nagi.ui.library

import androidx.compose.ui.draw.clip
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LibraryScreen(state: BrowserUiState, vm: BrowserViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val strings = rememberNagiStrings()
    val dimensions = LocalNagiUiDimensions.current
    var query by remember(state.currentSpace?.id) { mutableStateOf("") }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { SimpleDateFormat("MMM d, HH:mm", locale) }
    Column(Modifier.fillMaxSize().padding(dimensions.pagePadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(NagiIcons.ArrowBack, strings(R.string.ui_back_to_browser), onClick = onBack)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(strings(R.string.ui_history), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                if (state.settings.spacesEnabled) state.currentSpace?.let { space ->
                    Text(strings.spaceName(space), style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("history-space-name"))
                }
            }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(strings(R.string.ui_search_history)) },
            shape = NagiShapes.Rounded,
            leadingIcon = { Icon(NagiIcons.Search, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = if (dimensions.compact) 12.dp else 20.dp))
        val entries = state.history.filter { it.url.contains(query, true) || it.title.contains(query, true) }
        if (entries.isEmpty()) EmptyLibrary(strings(R.string.ui_your_browsing_history_will_appear_here))
        LazyColumn { items(entries, key = { it.id }) { entry ->
            LibraryRow(entry.title, entry.url, entry.faviconUrl, formatter.format(Date(entry.visitedAt)),
                { onOpen(entry.url) }, { vm.deleteHistory(entry.id) })
        } }
    }
}
@Composable
private fun EmptyLibrary(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
@Composable
private fun LibraryRow(title: String, url: String, icon: String?, detail: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    val strings = rememberNagiStrings()
    val dimensions = LocalNagiUiDimensions.current
    Row(Modifier.fillMaxWidth().testTag("library-row:$url").clip(NagiShapes.Rounded).clickable(onClick = onOpen)
        .padding(horizontal = 12.dp, vertical = dimensions.rowVerticalPadding)
        .heightIn(min = if (dimensions.compact) 40.dp else 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensions.sectionInset)) {
        Favicon(icon, siteUrl = url)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolButton(NagiIcons.Trash2, strings(R.string.ui_remove_1_s, title), onClick = onDelete)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
