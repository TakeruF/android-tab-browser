package com.orbit.browser.browser.search

import com.orbit.browser.domain.model.*

enum class SuggestionCategory(val label: String) { SEARCH("Search"), TABS("Open tabs"), HISTORY("History"), SPACES("Spaces"), COMMANDS("Commands") }
enum class BrowserCommand(val label: String, val hint: String) {
    NEW_TAB("New tab", "Ctrl T"), SPLIT("New split view", "Two panes"),
    CLOSE_SPLIT("Close split view", "One pane"), RESTORE_TAB("Restore closed tab", "Ctrl Shift T"),
    TOGGLE_SIDEBAR("Toggle sidebar", "More room"), FIND("Find in page", "Ctrl F"),
    SETTINGS("Open settings", "Preferences"), HISTORY("Open history", "Recent visits"),
    BOOKMARKS("Open bookmarks", "Saved pages"), DESKTOP("Toggle desktop site", "User agent"),
}
sealed interface SuggestionAction {
    data class Navigate(val url: String) : SuggestionAction
    data class SelectTab(val id: String) : SuggestionAction
    data class SelectSpace(val id: String) : SuggestionAction
    data class Command(val command: BrowserCommand) : SuggestionAction
}
data class Suggestion(val id: String, val category: SuggestionCategory, val title: String,
    val subtitle: String = "", val action: SuggestionAction)

object SuggestionProvider {
    fun suggestions(input: String, workspace: WorkspaceSnapshot, settings: BrowserSettings): List<Suggestion> {
        val query = input.trim()
        val result = mutableListOf<Suggestion>()
        val commandOnly = query.startsWith('>')
        val match = query.removePrefix(">").trim()
        if (!commandOnly && query.isNotEmpty()) {
            when (val resolved = InputResolver.resolve(query, workspace.searchEngines, settings.defaultSearchEngineId)) {
                is ResolvedInput.Navigate -> result += Suggestion("navigate", SuggestionCategory.SEARCH, "Open ${resolved.url}", "Website", SuggestionAction.Navigate(resolved.url))
                is ResolvedInput.Search -> {
                    result += Suggestion("primary", SuggestionCategory.SEARCH, "Search ${resolved.engine.name} for “${resolved.query}”", resolved.engine.keyword, SuggestionAction.Navigate(resolved.url))
                    workspace.searchEngines.filter { it.id != resolved.engine.id }.take(4).forEach { engine ->
                        result += Suggestion("engine:${engine.id}", SuggestionCategory.SEARCH, "Search ${engine.name}", engine.keyword,
                            SuggestionAction.Navigate(InputResolver.search(engine, resolved.query).url))
                    }
                }
                is ResolvedInput.Invalid -> Unit
            }
        }
        if (!commandOnly) {
            workspace.tabs.filter { it.closedAt == null && it.archivedAt == null &&
                (query.isEmpty() || it.title.contains(query, true) || it.url.contains(query, true)) }.take(5).forEach {
                result += Suggestion("tab:${it.id}", SuggestionCategory.TABS, it.title, it.url, SuggestionAction.SelectTab(it.id))
            }
            if (query.isNotEmpty()) workspace.history.distinctBy { it.url }.filter { it.title.contains(query, true) || it.url.contains(query, true) }.take(4).forEach {
                result += Suggestion("history:${it.id}", SuggestionCategory.HISTORY, it.title, it.url, SuggestionAction.Navigate(it.url))
            }
            workspace.spaces.filter { query.isEmpty() || it.name.contains(query, true) }.forEach {
                result += Suggestion("space:${it.id}", SuggestionCategory.SPACES, "Switch to ${it.name}", "Space", SuggestionAction.SelectSpace(it.id))
            }
        }
        BrowserCommand.entries.filter { match.isEmpty() || it.label.contains(match, true) }.forEach {
            result += Suggestion("command:${it.name}", SuggestionCategory.COMMANDS, it.label, it.hint, SuggestionAction.Command(it))
        }
        return result
    }
}
