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
    @Test fun compactCommandsHideBothSplitActionsAndKeepTabSelection() {
        for (query in listOf("", ">", ">split")) {
            val results = SuggestionProvider.suggestions(query, workspace, BrowserSettings(), splitEnabled = false)
            assertFalse(results.any { it.action == SuggestionAction.Command(BrowserCommand.SPLIT) })
            assertFalse(results.any { it.action == SuggestionAction.Command(BrowserCommand.CLOSE_SPLIT) })
        }
        val wide = SuggestionProvider.suggestions(">split", workspace, BrowserSettings())
        assertTrue(wide.any { it.action == SuggestionAction.Command(BrowserCommand.SPLIT) })
        assertTrue(wide.any { it.action == SuggestionAction.Command(BrowserCommand.CLOSE_SPLIT) })
    }

    @Test fun enabledSpacesKeepCrossSpaceSearchAndSwitching() {
        val search = SuggestionProvider.suggestions("New tab", workspace, BrowserSettings())
        assertTrue(search.any { it.action == SuggestionAction.SelectTab("hidden") })
        val defaults = SuggestionProvider.suggestions("", workspace, BrowserSettings())
        assertTrue(defaults.any { it.action == SuggestionAction.SelectSpace("work") })
    }
    @Test fun missingSelectedSpaceUsesSameFallbackAsBrowser() {
        val results = SuggestionProvider.suggestions("", workspace,
            BrowserSettings(spacesEnabled = false, selectedSpaceId = "missing"))
        assertTrue(results.any { it.action == SuggestionAction.SelectTab("local") })
        assertFalse(results.any { it.action == SuggestionAction.SelectTab("hidden") })
    }

    @Test fun emptyInputOnlyListsTheSelectedSpacesTabs() {
        for ((spaceId, tabId) in listOf("personal" to "local", "work" to "hidden")) {
            for (input in listOf("", "   ")) {
                val results = SuggestionProvider.suggestions(input, workspace, BrowserSettings(selectedSpaceId = spaceId))
                assertEquals(listOf(SuggestionAction.SelectTab(tabId)), results.filter { it.category == SuggestionCategory.TABS }.map { it.action })
                assertTrue(results.any { it.action == SuggestionAction.Command(BrowserCommand.NEW_TAB) })
            }
        }
    }

    @Test fun explicitSearchLabelsForeignTabsWithTheirLocalizedSpaceName() {
        val results = SuggestionProvider.suggestions("New tab", workspace, BrowserSettings(), localize = {
            when (it) { "Work" -> "仕事"; "New tab" -> "新しいタブ"; else -> it }
        })
        val local = results.single { it.id == "tab:local" }
        val foreign = results.single { it.id == "tab:hidden" }
        assertEquals("about:blank", local.subtitle)
        assertEquals("仕事 · about:blank", foreign.subtitle)
        assertEquals("新しいタブ", foreign.title)
        assertEquals(SuggestionAction.SelectTab("hidden"), foreign.action)

        val customSpace = workspace.copy(spaces = listOf(Space("personal", "Personal"), Space("work", "My work")))
        assertEquals("My work · about:blank", SuggestionProvider.suggestions("New tab", customSpace,
            BrowserSettings(), localize = { "translated:$it" }).single { it.id == "tab:hidden" }.subtitle)
        val renamedBuiltIn = workspace.copy(spaces = listOf(Space("personal", "Personal"), Space("work", "Personal")))
        assertEquals("Personal · about:blank", SuggestionProvider.suggestions("New tab", renamedBuiltIn,
            BrowserSettings(), localize = { "translated:$it" }).single { it.id == "tab:hidden" }.subtitle)
    }

    @Test fun distinctTabIdsWithTheSameUrlRemainSelectable() {
        val sameUrl = workspace.copy(tabs = listOf(
            BrowserTab("same-a", "personal", "https://same.test", "Same page"),
            BrowserTab("same-b", "personal", "https://same.test", "Same page"),
            BrowserTab("same-c", "work", "https://same.test", "Same page"),
        ))
        fun tabIds(input: String, settings: BrowserSettings) = SuggestionProvider.suggestions(input, sameUrl, settings)
            .filter { it.category == SuggestionCategory.TABS }.map { (it.action as SuggestionAction.SelectTab).id }
        assertEquals(listOf("same-a", "same-b"), tabIds("", BrowserSettings()))
        assertEquals(listOf("same-a", "same-b", "same-c"), tabIds("Same", BrowserSettings()))
        assertEquals(listOf("same-a", "same-b"), tabIds("Same", BrowserSettings(spacesEnabled = false)))
    }
}
