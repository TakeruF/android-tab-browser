package com.takeruf.nagi.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.takeruf.nagi.ui.theme.NagiShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.ToolButton
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val NONE = -2
private const val INVENTORY = -1

/**
 * Settings › About › Open-source licenses, shown in the Settings content pane: the bundled
 * components, then each full license text. Back steps text → list → About.
 */
@Composable
internal fun LicensesPane(wide: Boolean, onBack: () -> Unit) {
    val strings = rememberNagiStrings()
    var selected by rememberSaveable { mutableIntStateOf(NONE) }
    BackHandler { if (selected == NONE) onBack() else selected = NONE }
    val entry = LicenseCatalog.entries.getOrNull(selected)
    val title = when {
        selected == INVENTORY -> strings(R.string.ui_all_bundled_modules)
        entry != null -> entry.name
        else -> strings(R.string.ui_open_source_licenses)
    }
    val gutter = if (wide) 32.dp else 16.dp
    Column(Modifier.fillMaxSize().testTag("licenses-screen")) {
        if (wide) {
            // Wide: the pane title becomes a breadcrumb (About Nagi › Open-source licenses › …).
            // The Settings bar already has the only back arrow; earlier crumbs step back instead.
            val crumbs = buildList<Pair<String, (() -> Unit)?>> {
                add(strings(R.string.ui_about_nagi) to onBack)
                if (selected != NONE) add(strings(R.string.ui_open_source_licenses) to { selected = NONE })
                add(title to null)
            }
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 20.dp)
                .testTag("licenses-breadcrumb"), verticalAlignment = Alignment.CenterVertically) {
                crumbs.forEachIndexed { index, (label, onClick) ->
                    if (index > 0) Icon(NagiIcons.ChevronRight, null, Modifier.padding(horizontal = 6.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(label, (if (onClick == null) Modifier.weight(1f, fill = false) else Modifier
                            .clip(NagiShapes.Rounded).clickable(role = Role.Button, onClick = onClick)),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        color = if (onClick == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        } else {
            // Compact: replaces the Settings top bar, so this is the screen's only back arrow.
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(NagiIcons.ArrowBack, strings(if (selected == NONE) R.string.ui_back_to_settings else R.string.ui_back)) {
                    if (selected == NONE) onBack() else selected = NONE
                }
                Text(title, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        when {
            selected == INVENTORY -> LicenseText(listOf(LicenseCatalog.INVENTORY), source = null, gutter)
            entry != null -> LicenseText(entry.files, entry.source, gutter)
            else -> LazyColumn(Modifier.fillMaxSize().testTag("licenses-list"),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text(strings(R.string.ui_open_source_licenses_description), Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    SettingsSection(strings(R.string.ui_open_source_licenses), NagiIcons.Info, showHeading = false) {
                        LicenseCatalog.entries.forEachIndexed { index, item ->
                            SettingsAction(item.name, Modifier.testTag("license-entry-$index"), subtitle = item.license) { selected = index }
                        }
                    }
                }
                item {
                    SettingsSection(strings(R.string.ui_all_bundled_modules), NagiIcons.Info, showHeading = false) {
                        SettingsAction(strings(R.string.ui_all_bundled_modules), Modifier.testTag("license-inventory"),
                            subtitle = LicenseCatalog.INVENTORY) { selected = INVENTORY }
                    }
                }
            }
        }
    }
}

/** Full texts read from assets off the main thread; long files stay responsive line by line. */
@Composable
private fun LicenseText(files: List<String>, source: String?, gutter: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val lines by produceState<List<String>?>(null, files) {
        value = withContext(Dispatchers.IO) {
            files.flatMapIndexed { index, file ->
                val text = context.assets.open("${LicenseCatalog.DIRECTORY}/$file").bufferedReader().use { it.readText() }
                (if (index > 0) listOf("", "────────", "") else emptyList()) + listOf(file, "") + text.lines()
            }
        }
    }
    val loaded = lines
    if (loaded == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.padding(32.dp))
        }
        return
    }
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize().testTag("license-text"),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 32.dp)) {
            if (source != null) item {
                Text(source, Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(loaded) { line ->
                Text(line, Modifier.widthIn(max = 900.dp), style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
