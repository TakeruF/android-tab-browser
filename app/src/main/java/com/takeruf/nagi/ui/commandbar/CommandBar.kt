package com.takeruf.nagi.ui.commandbar

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import com.takeruf.nagi.ui.theme.NagiShapes
import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.rememberNagiStrings

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.*
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.components.AddressActionButton
import com.takeruf.nagi.ui.components.SpaceIcon
import com.takeruf.nagi.ui.components.Favicon
import com.takeruf.nagi.ui.components.LocalNagiUiDimensions
import com.takeruf.nagi.ui.components.menuAfterPointerSelection
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.ui.browser.KeyboardShortcuts
import com.takeruf.nagi.ui.browser.Shortcut
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.theme.NagiSystemBars
import kotlinx.coroutines.flow.first

@Composable
fun CommandBar(initialValue: String, workspace: WorkspaceSnapshot, settings: BrowserSettings,
    onDismiss: () -> Unit, onExecute: (SuggestionAction) -> Unit,
    onShortcut: (Shortcut) -> Boolean = { false }, splitEnabled: Boolean = true,
    anchorBounds: Rect? = null,
    phoneEntryBounds: Rect? = null, animateEntrance: Boolean = true,
    onLayoutChange: ((CommandBarLayout) -> Unit)? = null,
    onDismissLayoutNotice: (() -> Unit)? = null) {
    val strings = rememberNagiStrings()
    val dimensions = LocalNagiUiDimensions.current
    val compactSuggestions = dimensions.compact
    val centered = settings.commandBarLayout == CommandBarLayout.CENTERED
    val phoneFullscreen = !centered && with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() < 600.dp }
    val phoneMotion = remember { Animatable(if (animateEntrance) 0f else 1f) }
    val phoneSource = remember { phoneEntryBounds ?: anchorBounds }
    var closing by remember { mutableStateOf(false) }
    val dismiss = { if (phoneFullscreen && animateEntrance) closing = true else onDismiss() }
    val showPreviousUi = !centered && !settings.commandBarLayoutNoticeDismissed && onLayoutChange != null
    val input = rememberTextFieldState(initialText = initialValue, initialSelection = TextRange(0, initialValue.length))
    val contextMenu = remember { CommandBarTextContextMenu() }
    val query = input.text.toString()
    val suggestions = remember(query, workspace, settings, strings, splitEnabled) { SuggestionProvider.suggestions(query, workspace, settings, strings::translate, splitEnabled) }
    var selected by remember(query) { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val expanded = remember { MutableTransitionState(false).apply { targetState = true } }
    val execute = {
        if (input.composition == null) {
            val committed = input.text.toString()
            val fresh = SuggestionProvider.suggestions(committed, workspace, settings, strings::translate, splitEnabled)
            val suggestion = fresh.getOrNull(selected.coerceAtMost(fresh.lastIndex))
            if (suggestion != null) onExecute(suggestion.action)
            else if (!(settings.commandBar.showCommands && committed.trimStart().startsWith('>'))) {
                when (val value = InputResolver.resolve(committed, workspace.searchEngines, settings)) {
                    is ResolvedInput.Navigate -> onExecute(SuggestionAction.Navigate(value.url))
                    is ResolvedInput.Search -> onExecute(SuggestionAction.Navigate(value.url))
                    is ResolvedInput.Invalid -> Unit
                }
            }
        }
    }
    LaunchedEffect(query, selected) {
        if (suggestions.isNotEmpty()) list.scrollToItem(selected.coerceIn(0, suggestions.lastIndex))
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        NagiSystemBars()
        val windowInfo = LocalWindowInfo.current
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setDimAmount(if (centered) 0.32f else 0f)
                // The input is already at its toolbar anchor. Window-level motion
                // would move that anchor and compete with the dropdown's reveal.
                setWindowAnimations(0)
            }
        }
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val safeLeft = with(density) { WindowInsets.safeDrawing.getLeft(this, direction).toDp() }
        val safeTop = with(density) { WindowInsets.safeDrawing.getTop(this).toDp() }
        val keyboard = LocalSoftwareKeyboardController.current
        val phoneBackground = MaterialTheme.colorScheme.surface
        LaunchedEffect(closing) {
            if (closing) {
                keyboard?.hide()
                phoneMotion.animateTo(0f, tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
                onDismiss()
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().drawBehind {
            if (phoneFullscreen) drawRect(phoneBackground.copy(alpha = phoneMotion.value))
        }.safeDrawingPadding().imePadding()) {
        val compact = maxWidth < 600.dp
        // The resting bottom-bar preference is retained. On phones the active
        // editor moves to the top, leaving the whole area above the IME for results.
        val bottomSearchBar = !phoneFullscreen && !centered && compact && settings.bottomSearchBar
        LaunchedEffect(windowInfo, bottomSearchBar, centered, settings.commandBarLayoutNoticeDismissed, closing) {
            // A Dialog has its own window. Focusing before that window is ready can
            // leave the IME attached to the non-editable browser window instead.
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            if (!closing) {
                focus.requestFocus()
                if (phoneFullscreen && phoneMotion.value < 1f)
                    phoneMotion.animateTo(1f, tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
            }
        }
        if (phoneFullscreen) Box(Modifier.matchParentSize().testTag("command-phone-screen"))
        // Keep taps outside the panel dismissible even though the dialog fills the window.
        Box(Modifier.matchParentSize().pointerInput(phoneFullscreen, dismiss) { detectTapGestures { if (!phoneFullscreen) dismiss() } })
        // With nothing to suggest, the bar is just the input: no divider, list or "No matches",
        // and it takes the same pill silhouette as the address bar and new-tab search field.
        val hasSuggestions = suggestions.isNotEmpty()
        // Bounds come from the actual address field in window pixels. Re-measure on
        // resize, and clamp to this dialog's safe area as the keyboard opens. New-tab
        // and keyboard entry points use that same toolbar, with a top-edge fallback.
        val inputHeight = if (phoneFullscreen) (if (compactSuggestions) 40.dp else 48.dp)
            else if (centered) (if (compactSuggestions) 64.dp else 80.dp) else anchorBounds?.let { with(density) { it.height.toDp() } }
            ?: if (bottomSearchBar) 44.dp else 40.dp
        val panelWidth = if (phoneFullscreen) maxWidth else if (centered) minOf(720.dp, maxWidth * if (compact) 0.96f else 0.85f) else (anchorBounds?.let { with(density) { it.width.toDp() } }
            ?: minOf(720.dp, (maxWidth - 8.dp).coerceAtLeast(0.dp))).coerceIn(0.dp, maxWidth)
        val panelX = if (phoneFullscreen) 0.dp else if (centered) (maxWidth - panelWidth) / 2 else ((anchorBounds?.let { with(density) { it.left.toDp() } } ?: (safeLeft + 4.dp)) - safeLeft)
            .coerceIn(0.dp, (maxWidth - panelWidth).coerceAtLeast(0.dp))
        val panelTop = if (phoneFullscreen || centered || bottomSearchBar) 0.dp else
            ((anchorBounds?.let { with(density) { it.top.toDp() } } ?: (safeTop + 4.dp)) - safeTop)
                .coerceIn(0.dp, (maxHeight - inputHeight).coerceAtLeast(0.dp))
        val panelHeight = if (phoneFullscreen || centered) maxHeight else if (bottomSearchBar)
            ((anchorBounds?.let { with(density) { it.bottom.toDp() } } ?: (maxHeight + safeTop)) - safeTop)
                .coerceIn(0.dp, maxHeight)
        else maxHeight - panelTop
        Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = panelX, y = panelTop)
            .width(panelWidth).height(panelHeight),
            contentAlignment = if (centered) Alignment.Center else if (bottomSearchBar) Alignment.BottomCenter else Alignment.TopCenter) {
        // Retain the idle field's half-height corner radius even as the list grows.
        Surface(shape = if (phoneFullscreen) RectangleShape else if (centered && hasSuggestions) RoundedCornerShape(28.dp) else RoundedCornerShape(inputHeight / 2),
            color = if (phoneFullscreen) Color.Transparent else if (centered) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface, shadowElevation = if (phoneFullscreen) 0.dp else if (centered) 16.dp else 8.dp,
            modifier = Modifier.fillMaxWidth().then(if (phoneFullscreen) Modifier.fillMaxHeight() else Modifier.heightIn(max = 640.dp)).testTag("command-panel")
                .pointerInput(Unit) { detectTapGestures { /* Consume taps on the panel background. */ } }
                .onPreviewKeyEvent { event ->
                    // The IME gets pre-dispatch first. If a composition key still reaches the app,
                    // consume it here so Compose cannot move focus or execute an unfinished query.
                    if (event.type != KeyEventType.KeyDown) false
                    else if (input.composition != null) event.key in setOf(Key.Enter, Key.NumPadEnter, Key.DirectionDown, Key.DirectionUp, Key.Escape)
                    else {
                        // This editor owns a Dialog window, so Activity's Window.Callback
                        // does not receive its keys. Forward only browser navigation
                        // chords; leave text-editing combinations with the IME/field.
                        val shortcut = KeyboardShortcuts.map(event.nativeKeyEvent)
                        val globalShortcut = shortcut?.tabNumber != null || when (shortcut) {
                            Shortcut.HISTORY, Shortcut.SETTINGS, Shortcut.PRIVATE_BROWSING -> true
                            else -> false
                        }
                        if (globalShortcut && shortcut != null && onShortcut(shortcut)) true else when (event.key) {
                            Key.DirectionDown -> if (inputFocused) { selected = (selected + 1).coerceAtMost((suggestions.size - 1).coerceAtLeast(0)); true } else false
                            Key.DirectionUp -> if (inputFocused) { selected = (selected - 1).coerceAtLeast(0); true } else false
                            Key.Enter, Key.NumPadEnter -> if (inputFocused) { execute(); true } else false
                            Key.Escape -> { dismiss(); true }
                            else -> false
                        }
                    }
                }) {
            SearchPanelLayout(inputAtBottom = bottomSearchBar) {
                @Composable
                fun SearchInput() {
                    val textStyle = if (centered) MaterialTheme.typography.bodyLarge else if (phoneFullscreen || bottomSearchBar) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
                    Row(Modifier.then(if (phoneFullscreen) Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        .zIndex(1f).graphicsLayer {
                            val remaining = 1f - phoneMotion.value
                            translationX = ((phoneSource?.left ?: (safeLeft + 8.dp).toPx()) - safeLeft.toPx() - 8.dp.toPx()) * remaining
                            translationY = ((phoneSource?.top ?: (safeTop + 4.dp).toPx()) - safeTop.toPx() - 4.dp.toPx()) * remaining
                        } else Modifier).fillMaxWidth().height(inputHeight).testTag("command-input-row")
                        .then(if (phoneFullscreen) Modifier.clip(RoundedCornerShape(inputHeight / 2)).background(MaterialTheme.colorScheme.surfaceContainerHigh) else Modifier)
                        .padding(start = if (centered) 24.dp else 14.dp, end = if (centered) 12.dp else 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(NagiIcons.Search, null, Modifier.size(if (centered) 24.dp else 16.dp))
                        CompositionLocalProvider(LocalTextContextMenuDropdownProvider provides contextMenu) {
                        BasicTextField(state = input, lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            onKeyboardAction = { execute() },
                            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorator = { editor ->
                                // BasicTextField installs its native toolbar inside the field.
                                // Reuse that provider for secondary clicks, keeping Compose's
                                // editing/classification actions and the existing selection.
                                val toolbar = LocalTextContextMenuToolbarProvider.current
                                SideEffect { contextMenu.toolbar = toolbar }
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Text(strings(if (compact || !settings.commandBar.showCommands) R.string.ui_search_or_enter_url
                                        else R.string.ui_search_enter_a_url_or_type_for_commands),
                                        style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    editor()
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxHeight().testTag("command-input").focusRequester(focus)
                                .menuAfterPointerSelection({ input.selection }, { input.composition == null && inputFocused })
                                .onFocusChanged { inputFocused = it.isFocused }.semantics {
                                stateDescription = if (input.composition == null) strings(R.string.ui_ready) else strings(R.string.ui_composing)
                            })
                        }
                        AddressActionButton(onClick = dismiss, modifier = Modifier.testTag("command-close")) {
                            Icon(NagiIcons.X, strings(R.string.ui_close_1_s, strings(R.string.ui_search)), Modifier.size(if (centered) 24.dp else 16.dp))
                        }
                    }
                }
                SearchInput()
                // Reveal only the results. The input stays at its original location,
                // and list text keeps its natural size instead of being scaled.
                // Compose's animation clock honors Android's animator duration scale.
                AnimatedVisibility(visibleState = expanded,
                    modifier = Modifier.graphicsLayer { if (phoneFullscreen) alpha = phoneMotion.value },
                    enter = if (centered || phoneFullscreen) EnterTransition.None else expandVertically(animationSpec = tween(200, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                        expandFrom = if (bottomSearchBar) Alignment.Bottom else Alignment.Top),
                    exit = ExitTransition.None) {
                if (hasSuggestions || showPreviousUi) Column {
                if (!bottomSearchBar) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (hasSuggestions) LazyColumn(state = list, modifier = Modifier.weight(1f, fill = false).testTag("command-suggestions"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (compactSuggestions) 4.dp else 8.dp)) {
                    itemsIndexed(suggestions, key = { _, item -> item.id }) { index, item ->
                        val active = selected == index
                        val titleColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        val detailColor = if (active) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(Modifier.fillMaxWidth().clip(NagiShapes.Rounded).background(if (active) MaterialTheme.colorScheme.secondaryContainer else
                            androidx.compose.ui.graphics.Color.Transparent, NagiShapes.Rounded)
                            .testTag("suggestion:${item.id}")
                            .semantics { this.selected = active }
                            .clickable { onExecute(item.action) }.heightIn(min = if (compactSuggestions) 40.dp else if (phoneFullscreen) 48.dp else 56.dp)
                            .padding(horizontal = 12.dp, vertical = if (compactSuggestions) 4.dp else if (phoneFullscreen) 6.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                val tabAction = item.action as? SuggestionAction.SelectTab
                                val tab = tabAction?.let { action -> workspace.tabs.find { it.id == action.id } }
                                val spaceAction = item.action as? SuggestionAction.SelectSpace
                                val space = spaceAction?.let { action -> workspace.spaces.find { it.id == action.id } }
                                if (tab != null) {
                                    if (tab.url == "about:blank") Icon(NagiIcons.House, null, Modifier.size(20.dp), tint = titleColor)
                                    else Favicon(tab.faviconUrl, siteUrl = tab.url, iconSize = 20.dp)
                                } else if (space != null) {
                                    SpaceIcon(space.icon, titleColor)
                                } else {
                                    val command = (item.action as? SuggestionAction.Command)?.command
                                    Icon(when (command) {
                                        BrowserCommand.NEW_TAB -> NagiIcons.Plus
                                        BrowserCommand.SPLIT -> NagiIcons.Columns2
                                        BrowserCommand.CLOSE_SPLIT -> NagiIcons.PanelsTopLeft
                                        BrowserCommand.RESTORE_TAB -> NagiIcons.History
                                        BrowserCommand.TOGGLE_SIDEBAR -> NagiIcons.SidebarOpen
                                        BrowserCommand.FIND -> NagiIcons.Search
                                        BrowserCommand.SETTINGS -> NagiIcons.Settings
                                        BrowserCommand.HISTORY -> NagiIcons.History
                                        BrowserCommand.DESKTOP -> NagiIcons.Monitor
                                        null -> when (item.category) {
                                            SuggestionCategory.SEARCH -> NagiIcons.Search
                                            SuggestionCategory.TABS -> NagiIcons.PanelsTopLeft
                                            SuggestionCategory.HISTORY -> NagiIcons.History
                                            SuggestionCategory.SPACES -> NagiIcons.Layers
                                            SuggestionCategory.COMMANDS -> NagiIcons.Zap
                                        }
                                    }, null, modifier = Modifier.size(20.dp), tint = titleColor)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                if (item.category == SuggestionCategory.TABS || item.category == SuggestionCategory.HISTORY)
                                    Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                        color = detailColor)
                            }
                        }
                    }
                }
                if (showPreviousUi) {
                    if (hasSuggestions) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onLayoutChange?.invoke(CommandBarLayout.CENTERED) },
                            modifier = Modifier.weight(1f, fill = false).testTag("command-use-centered").heightIn(min = dimensions.controlSize),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(strings(R.string.ui_command_bar_use_centered), style = MaterialTheme.typography.labelMedium)
                        }
                        if (onDismissLayoutNotice != null) TextButton(onClick = onDismissLayoutNotice,
                            modifier = Modifier.testTag("command-dismiss-layout-notice").heightIn(min = dimensions.controlSize),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(strings(R.string.ui_command_bar_dismiss_notice), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (bottomSearchBar) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                }
            }
        }
        }
        }
    }
}

private class CommandBarTextContextMenu : TextContextMenuProvider {
    var toolbar: TextContextMenuProvider? = null

    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        toolbar?.showTextContextMenu(dataProvider)
    }
}

/** Keep the same field/focus/InputConnection when switching between top, bottom and centered layouts. */
@Composable
private fun SearchPanelLayout(inputAtBottom: Boolean, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val input = measurables.first().measure(constraints.copy(minHeight = 0))
        val results = measurables.getOrNull(1)?.measure(constraints.copy(minHeight = 0,
            maxHeight = (constraints.maxHeight - input.height).coerceAtLeast(0)))
        val height = (input.height + (results?.height ?: 0)).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            input.placeRelative(0, if (inputAtBottom) height - input.height else 0)
            results?.placeRelative(0, if (inputAtBottom) 0 else input.height)
        }
    }
}
