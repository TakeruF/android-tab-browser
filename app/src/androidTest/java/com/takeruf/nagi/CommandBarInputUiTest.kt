package com.takeruf.nagi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CommandBarInputUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun hidingEverySuggestionResolvesCommittedTextIncludingCommandPrefix() {
        val actions = mutableListOf<SuggestionAction>()
        compose.setContent {
            NagiTheme(ThemeMode.LIGHT) {
                CommandBar("", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                    BrowserSettings(commandBar = CommandBarSettings(false, false, false, false, false)),
                    onDismiss = {}, onExecute = { actions += it })
            }
        }
        val input = compose.onNodeWithTag("command-input")
        input.performTextReplacement(">New tab")
        input.performImeAction()
        compose.runOnIdle {
            assertEquals(listOf(SuggestionAction.Navigate(InputResolver.search(DefaultSearchEngines.all.first { it.id == "google" },
                ">New tab").url)), actions)
            actions.clear()
        }
        input.performTextReplacement("日本語 & 中文")
        input.performImeAction()
        compose.runOnIdle {
            assertEquals(listOf(SuggestionAction.Navigate(InputResolver.search(DefaultSearchEngines.all.first { it.id == "google" },
                "日本語 & 中文").url)), actions)
        }
    }
}
