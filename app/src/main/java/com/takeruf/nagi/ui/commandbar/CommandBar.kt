package com.takeruf.nagi.ui.commandbar

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.theme.NagiSystemBars
import kotlinx.coroutines.flow.first

@Composable
fun CommandBar(initialValue: String, workspace: WorkspaceSnapshot, settings: BrowserSettings,
    onDismiss: () -> Unit, onExecute: (SuggestionAction) -> Unit) {
    val strings = rememberNagiStrings()
    val input = rememberTextFieldState(initialText = initialValue, initialSelection = TextRange(0, initialValue.length))
    val query = input.text.toString()
    val suggestions = remember(query, workspace, settings, strings) { SuggestionProvider.suggestions(query, workspace, settings, strings::translate) }
    var selected by remember(query) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    val execute = {
        if (input.composition == null) {
        val fresh = SuggestionProvider.suggestions(input.text.toString(), workspace, settings, strings::translate)
        fresh.getOrNull(selected.coerceAtMost(fresh.lastIndex))?.let { onExecute(it.action) }
            ?: run { when (val value = InputResolver.resolve(input.text.toString(), workspace.searchEngines, settings.defaultSearchEngineId)) {
                is ResolvedInput.Navigate -> onExecute(SuggestionAction.Navigate(value.url))
                is ResolvedInput.Search -> onExecute(SuggestionAction.Navigate(value.url))
                is ResolvedInput.Invalid -> Unit
            } }
        }
    }
    LaunchedEffect(query, selected) {
        if (suggestions.isNotEmpty()) list.scrollToItem(selected.coerceIn(0, suggestions.lastIndex))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        NagiSystemBars()
        val windowInfo = LocalWindowInfo.current
        LaunchedEffect(windowInfo) {
            // A Dialog has its own window. Focusing before that window is ready can
            // leave the IME attached to the non-editable browser window instead.
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            focus.requestFocus()
        }
        Box(Modifier.safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = 16.dp,
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(0.85f).heightIn(max = 640.dp)
                .onPreviewKeyEvent { event ->
                    // The IME gets pre-dispatch first. If a composition key still reaches the app,
                    // consume it here so Compose cannot move focus or execute an unfinished query.
                    if (event.type != KeyEventType.KeyDown) false
                    else if (input.composition != null) event.key in setOf(Key.Enter, Key.NumPadEnter, Key.DirectionDown, Key.DirectionUp, Key.Escape)
                    else when (event.key) {
                        Key.DirectionDown -> { selected = (selected + 1).coerceAtMost((suggestions.size - 1).coerceAtLeast(0)); true }
                        Key.DirectionUp -> { selected = (selected - 1).coerceAtLeast(0); true }
                        Key.Enter, Key.NumPadEnter -> { execute(); true }
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                }) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NagiIcons.Search, null, Modifier.padding(start = 12.dp))
                    TextField(state = input, lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        onKeyboardAction = { execute() },
                        placeholder = { Text(strings(R.string.ui_search_enter_a_url_or_type_for_commands)) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics {
                            stateDescription = if (input.composition == null) strings(R.string.ui_ready) else strings(R.string.ui_composing)
                        })
                    TextButton(onClick = onDismiss) { Text("Esc") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false), contentPadding = PaddingValues(12.dp)) {
                    itemsIndexed(suggestions, key = { _, item -> item.id }) { index, item ->
                        val active = selected == index
                        val titleColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        val detailColor = if (active) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
                        if (index == 0 || suggestions[index - 1].category != item.category) {
                            Text(strings.translate(item.category.label).uppercase(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp, 10.dp))
                        }
                        Row(Modifier.fillMaxWidth().background(if (active) MaterialTheme.colorScheme.primaryContainer else
                            androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
                            .then(if (active) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                            .semantics { this.selected = active }
                            .clickable { onExecute(item.action) }.padding(12.dp).heightIn(min = 36.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(when (item.category) {
                                SuggestionCategory.SEARCH -> NagiIcons.Search
                                SuggestionCategory.TABS -> NagiIcons.PanelsTopLeft
                                SuggestionCategory.HISTORY -> NagiIcons.History
                                SuggestionCategory.SPACES -> NagiIcons.Layers
                                SuggestionCategory.COMMANDS -> NagiIcons.Zap
                            }, null, modifier = Modifier.size(20.dp), tint = titleColor)
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (item.category == SuggestionCategory.TABS || item.category == SuggestionCategory.HISTORY)
                                    Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                        color = detailColor)
                            }
                            if (item.category != SuggestionCategory.TABS && item.category != SuggestionCategory.HISTORY)
                                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = detailColor)
                        }
                    }
                    if (suggestions.isEmpty()) item { Text(strings(R.string.ui_no_matches), Modifier.padding(16.dp)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(strings(R.string.ui_navigate_open_g_ddg_yt_gh_scholar_keyword_search),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp, 14.dp))
            }
        }
        }
    }
}
