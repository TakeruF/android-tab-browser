package com.takeruf.nagi

import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SpacesSuggestionsTest {
    private val workspace = WorkspaceSnapshot(
        spaces = listOf(Space("personal", "Personal"), Space("work", "Work")),
        tabs = listOf(BrowserTab("local", "personal"), BrowserTab("hidden", "work")),
    )
    @Test fun disabledSpacesExcludeSpaceActionsAndHiddenTabsEvenForEmptyInput() {
        for (query in listOf("", "New tab", "Work")) {
            val results = SuggestionProvider.suggestions(query, workspace, BrowserSettings(spacesEnabled = false))
            assertFalse(results.any { it.action is SuggestionAction.SelectSpace })
            assertFalse(results.any { it.action == SuggestionAction.SelectTab("hidden") })
        }
        assertTrue(SuggestionProvider.suggestions("", workspace, BrowserSettings(spacesEnabled = false))
            .any { it.action == SuggestionAction.SelectTab("local") })
    }
    @Test fun enabledSpacesKeepCrossSpaceSearchAndSwitching() {
        val results = SuggestionProvider.suggestions("", workspace, BrowserSettings())
        assertTrue(results.any { it.action == SuggestionAction.SelectTab("hidden") })
        assertTrue(results.any { it.action == SuggestionAction.SelectSpace("work") })
    }
    @Test fun missingSelectedSpaceUsesSameFallbackAsBrowser() {
        val results = SuggestionProvider.suggestions("", workspace,
            BrowserSettings(spacesEnabled = false, selectedSpaceId = "missing"))
        assertTrue(results.any { it.action == SuggestionAction.SelectTab("local") })
        assertFalse(results.any { it.action == SuggestionAction.SelectTab("hidden") })
    }
}
