package com.takeruf.nagi

import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class CommandBarSuggestionsTest {
    private val workspace = WorkspaceSnapshot(
        spaces = listOf(Space("personal", "New Space"), Space("work", "Work")),
        tabs = listOf(BrowserTab("open", "personal", "https://new.test", "New page"),
            BrowserTab("closed", "personal", title = "New closed", closedAt = 1),
            BrowserTab("archived", "personal", title = "New archived", archivedAt = 1)),
        history = listOf(HistoryEntry(1, "https://new-history.test", "New visit")),
        searchEngines = DefaultSearchEngines.all,
    )

    @Test fun categoriesCanBeHiddenIndependentlyWithoutChangingOtherSuggestions() {
        val baseline = SuggestionProvider.suggestions("New", workspace, BrowserSettings())
        assertEquals(SuggestionCategory.entries.toSet(), baseline.map { it.category }.toSet())
        val choices = listOf(
            SuggestionCategory.SEARCH to CommandBarSettings(showSearch = false),
            SuggestionCategory.TABS to CommandBarSettings(showTabs = false),
            SuggestionCategory.HISTORY to CommandBarSettings(showHistory = false),
            SuggestionCategory.SPACES to CommandBarSettings(showSpaces = false),
            SuggestionCategory.COMMANDS to CommandBarSettings(showCommands = false),
        )
        for ((category, settings) in choices) {
            assertEquals(baseline.filter { it.category != category },
                SuggestionProvider.suggestions("New", workspace, BrowserSettings(commandBar = settings)))
        }
        assertEquals(baseline, SuggestionProvider.suggestions("New", workspace, BrowserSettings()))
    }

    @Test fun hiddenCommandsTurnGreaterThanIntoOrdinaryText() {
        val hidden = BrowserSettings(commandBar = CommandBarSettings(showCommands = false))
        val result = SuggestionProvider.suggestions(">New tab", workspace, hidden)
        assertFalse(result.any { it.category == SuggestionCategory.COMMANDS })
        assertTrue(result.any { it.category == SuggestionCategory.SEARCH })
        assertTrue(SuggestionProvider.suggestions(">New tab", workspace, BrowserSettings()).all { it.category == SuggestionCategory.COMMANDS })
    }

    @Test fun visibilityDoesNotBypassSpaceOrWindowRestrictions() {
        val result = SuggestionProvider.suggestions("", workspace,
            BrowserSettings(spacesEnabled = false), splitEnabled = false)
        assertFalse(result.any { it.category == SuggestionCategory.SPACES })
        assertFalse(result.any { it.id == "tab:closed" || it.id == "tab:archived" })
        assertFalse(result.any { it.action == SuggestionAction.Command(BrowserCommand.SPLIT) ||
            it.action == SuggestionAction.Command(BrowserCommand.CLOSE_SPLIT) })
        val hidden = BrowserSettings(commandBar = CommandBarSettings(false, false, false, false, false))
        for (query in listOf("", "New", "https://new.test", ">"))
            assertTrue(SuggestionProvider.suggestions(query, workspace, hidden).isEmpty())
        assertTrue(SuggestionProvider.suggestions("New", workspace, BrowserSettings()).isNotEmpty())
    }
}
