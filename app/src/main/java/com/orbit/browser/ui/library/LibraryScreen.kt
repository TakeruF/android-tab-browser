package com.orbit.browser.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
    var query by remember { mutableStateOf("") }
    val formatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to browser", onClick = onBack)
            Text(if (history) "History" else "Bookmarks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search ${if (history) "history" else "bookmarks"}") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
        if (history) {
            val entries = state.workspace.history.filter { it.url.contains(query, true) || it.title.contains(query, true) }
            if (entries.isEmpty()) EmptyLibrary("Your browsing history will appear here.")
            LazyColumn { items(entries, key = { it.id }) { entry ->
                LibraryRow(entry.title, entry.url, entry.faviconUrl, formatter.format(Date(entry.visitedAt)),
                    { onOpen(entry.url) }, { vm.deleteHistory(entry.id) })
            } }
        } else {
            val entries = state.workspace.bookmarks.filter { !it.isFavorite && (it.url.contains(query, true) || it.title.contains(query, true)) }
            if (entries.isEmpty()) EmptyLibrary("Save pages from the page menu. Favorites live in your sidebar.")
            LazyColumn { items(entries, key = { it.id }) { entry -> LibraryRow(entry.title, entry.url, entry.faviconUrl, "", { onOpen(entry.url) }, { vm.removeBookmark(entry.id) }) } }
        }
    }
}
@Composable
private fun EmptyLibrary(text: String) { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
@Composable
private fun LibraryRow(title: String, url: String, icon: String?, detail: String, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Favicon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolButton(Icons.Outlined.DeleteOutline, "Remove $title", onClick = onDelete)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
