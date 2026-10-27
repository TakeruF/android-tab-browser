package com.takeruf.nagi

import com.takeruf.nagi.browser.translation.*
import org.junit.Assert.*
import org.junit.Test

class TranslationNavigationStateTest {
    @Test fun linksCarryTheTranslatedPairThroughRedirectsButOnlyForTheSameSourceLanguage() {
        val tab = TranslationNavigationState()
        tab.didTranslate("en", "ja", false)
        tab.followLink()
        tab.documentStarted()
        tab.documentStarted() // A redirect must not erase the pending pair.
        val pending = tab.takeContinuation()
        assertEquals(TranslationChoice("ja", false), TranslationNavigationState.choose("en", pending, emptyMap()))
        assertNull(TranslationNavigationState.choose("fr", pending, emptyMap()))
        assertNull(tab.takeContinuation())
        tab.followLink() // No successful translation in the new document.
        assertNull(tab.takeContinuation())
    }

    @Test fun directNavigationRestorationAndNewTabsDoNotInheritAManualChoice() {
        val tab = TranslationNavigationState()
        tab.didTranslate("en", "ja", true)
        tab.followLink()
        tab.reset()
        assertNull(tab.takeContinuation())
        tab.followLink()
        assertNull(tab.takeContinuation())
        assertNull(TranslationNavigationState().takeContinuation())
    }

    @Test fun alwaysRulesApplyWithoutAContinuationAndAUserSelectedTabChoiceTakesPrecedence() {
        val rules = mapOf("en" to "ko", "fr" to "ja", "ja" to "ja")
        assertEquals(TranslationChoice("ko"), TranslationNavigationState.choose("en", null, rules))
        assertNull(TranslationNavigationState.choose("de", null, rules))
        assertNull(TranslationNavigationState.choose("ja", null, rules))
        val tabChoice = TranslatedLanguagePair("en", TranslationChoice("zh", false))
        assertEquals(TranslationChoice("zh", false), TranslationNavigationState.choose("en", tabChoice, rules))
        assertEquals(TranslationChoice("ja"), TranslationNavigationState.choose("fr", tabChoice, rules))
    }
}
