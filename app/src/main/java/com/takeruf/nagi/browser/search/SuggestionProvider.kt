package com.takeruf.nagi.browser.search

import com.takeruf.nagi.domain.model.*

enum class SuggestionCategory(val label: String) { SEARCH("Search"), TABS("Open tabs"), HISTORY("History"), SPACES("Spaces"), COMMANDS("Commands") }
enum class BrowserCommand(val label: String, val hint: String) {
    NEW_TAB("New tab", "Ctrl T"), SPLIT("New split view", "Two panes"),
    CLOSE_SPLIT("Close split view", "One pane"), RESTORE_TAB("Restore closed tab", "Ctrl Shift T"),
    TOGGLE_SIDEBAR("Toggle sidebar", "More room"), FIND("Find in page", "Ctrl F"),
    SETTINGS("Open settings", "Preferences"), HISTORY("Open history", "Recent visits"), BOOKMARKS("Bookmarks", "Saved pages"),
    DESKTOP("Toggle desktop site", "User agent"),
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
    private fun engineTitle(engine: SearchEngine, query: String, localize: (String) -> String, primary: Boolean = false): String {
        val name = SearchEngineNames.display(engine, localize = localize)
        return when {
            engine.id in AiSearchEngines.ids -> localize("Ask %1\$s about “%2\$s”").format(name, query)
            primary -> localize("Search %1\$s for “%2\$s”").format(name, query)
            else -> localize("Search %1\$s").format(name)
        }
    }

    fun suggestions(input: String, workspace: WorkspaceSnapshot, settings: BrowserSettings, localize: (String) -> String = { it }): List<Suggestion> {
        val query = input.trim()
        val result = mutableListOf<Suggestion>()
        val commandOnly = query.startsWith('>')
        val match = query.removePrefix(">").trim()
        if (!commandOnly && query.isNotEmpty()) {
            when (val resolved = InputResolver.resolve(query, workspace.searchEngines, settings)) {
                is ResolvedInput.Navigate -> result += Suggestion("navigate", SuggestionCategory.SEARCH, localize("Open %1\$s").format(resolved.url), localize("Website"), SuggestionAction.Navigate(resolved.url))
                is ResolvedInput.Search -> {
                    val commonIds = CommonSearchEngines.ids(settings)
                    val search = Suggestion("primary", SuggestionCategory.SEARCH, engineTitle(resolved.engine, resolved.query, localize, primary = true), resolved.engine.keyword, SuggestionAction.Navigate(resolved.url))
                    val aiEngine = AiSearchEngines.default(settings, workspace.searchEngines)
                    fun aiSuggestion(engine: SearchEngine) = Suggestion("ai:${engine.id}", SuggestionCategory.SEARCH,
                        engineTitle(engine, resolved.query, localize), engine.keyword,
                        SuggestionAction.Navigate(InputResolver.search(engine, resolved.query).url))
                    val ai = aiSuggestion(aiEngine)
                    val explicitEngine = workspace.searchEngines.any {
                        it.id in commonIds && it.keyword.equals(query.substringBefore(' '), ignoreCase = true)
                    } && query.contains(' ')
                    result += when {
                        resolved.engine.id == aiEngine.id -> listOf(search)
                        !explicitEngine && ChatGptSearch.preferFor(resolved.query) -> listOf(ai, search)
                        else -> listOf(search, ai)
                    }
                    if (aiEngine.id != "chatgpt" && "chatgpt" in commonIds) result += aiSuggestion(AiSearchEngines.chatGpt)
                    workspace.searchEngines.filter { it.id != resolved.engine.id && it.id != aiEngine.id && it.id in commonIds }.forEach { engine ->
                        result += Suggestion("engine:${engine.id}", SuggestionCategory.SEARCH, engineTitle(engine, resolved.query, localize), engine.keyword,
                            SuggestionAction.Navigate(InputResolver.search(engine, resolved.query).url))
                    }
                }
                is ResolvedInput.Invalid -> Unit
            }
        }
        if (!commandOnly) {
            workspace.tabs.filter { it.closedAt == null && it.archivedAt == null &&
                (query.isEmpty() || it.title.contains(query, true) || it.url.contains(query, true)) }.take(5).forEach {
                result += Suggestion("tab:${it.id}", SuggestionCategory.TABS, (if (it.url == "about:blank") localize("New tab") else it.title), it.url, SuggestionAction.SelectTab(it.id))
            }
            if (query.isNotEmpty()) workspace.history.distinctBy { it.url }.filter { it.title.contains(query, true) || it.url.contains(query, true) }.take(4).forEach {
                result += Suggestion("history:${it.id}", SuggestionCategory.HISTORY, it.title, it.url, SuggestionAction.Navigate(it.url))
            }
            workspace.spaces.filter { query.isEmpty() || it.name.contains(query, true) }.forEach {
                result += Suggestion("space:${it.id}", SuggestionCategory.SPACES, localize("Switch to %1\$s").format(if (it.id in listOf("personal", "work") && it.name in listOf("Personal", "Work")) localize(it.name) else it.name), localize("Space"), SuggestionAction.SelectSpace(it.id))
            }
        }
        BrowserCommand.entries.filter { match.isEmpty() || (it.label.contains(match, true) || localize(it.label).contains(match, true)) }.forEach {
            result += Suggestion("command:${it.name}", SuggestionCategory.COMMANDS, localize(it.label), localize(it.hint), SuggestionAction.Command(it))
        }
        return result
    }
}
