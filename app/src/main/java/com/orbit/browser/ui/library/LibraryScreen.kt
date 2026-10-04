package com.orbit.browser.ui.library

import com.orbit.browser.R
import com.orbit.browser.ui.localization.rememberOrbitStrings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.orbit.browser.ui.components.OrbitIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.browser.ui.browser.*
import com.orbit.browser.ui.components.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LibraryScreen(history: Boolean, state: BrowserUiState, vm: BrowserViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val strings = rememberOrbitStrings()
    var query by remember { mutableStateOf("") }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { SimpleDateFormat("MMM d, HH:mm", locale) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(OrbitIcons.ArrowBack, strings(R.string.ui_back_to_browser), onClick = onBack)
            Text(if (history) strings(R.string.ui_history) else strings(R.string.ui_bookmarks), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(if (history) strings(R.string.ui_search_history) else strings(R.string.ui_search_bookmarks)) },
            leadingIcon = { Icon(OrbitIcons.Search, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
        if (history) {
            val entries = state.workspace.history.filter { it.url.contains(query, true) || it.title.contains(query, true) }
            if (entries.isEmpty()) EmptyLibrary(strings(R.string.ui_your_browsing_history_will_appear_here))
            LazyColumn { items(entries, key = { it.id }) { entry ->
                LibraryRow(entry.title, entry.url, entry.faviconUrl, formatter.format(Date(entry.visitedAt)),
                    { onOpen(entry.url) }, { vm.deleteHistory(entry.id) })
            } }
        } else {
            val entries = state.workspace.bookmarks.filter { !it.isFavorite && (it.url.contains(query, true) || it.title.contains(query, true)) }
            if (entries.isEmpty()) EmptyLibrary(strings(R.string.ui_save_pages_from_the_page_menu_favorites_live_in_your_sidebar))
            LazyColumn { items(entries, key = { it.id }) { entry -> LibraryRow(entry.title, entry.url, entry.faviconUrl, "", { onOpen(entry.url) }, { vm.removeBookmark(entry.id) }) } }
        }
    }
}
@Composable
private fun EmptyLibrary(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
@Composable
private fun LibraryRow(title: String, url: String, icon: String?, detail: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    val strings = rememberOrbitStrings()
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Favicon(icon, siteUrl = url)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolButton(OrbitIcons.Trash2, strings(R.string.ui_remove_1_s, title), onClick = onDelete)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
