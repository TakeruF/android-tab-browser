package com.takeruf.nagi

import android.app.Application
import android.content.res.Configuration
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.localization.NagiStrings
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalizationTest {
    @Test fun resourcesFormatAndCommandsMatchInAllFourLanguages() {
        for ((language, label) in listOf("en" to "New tab", "ja" to "新しいタブ", "ko" to "새 탭", "zh-CN" to "新标签页")) {
            val configuration = Configuration().apply { setLocale(Locale.forLanguageTag(language)) }
            val context = RuntimeEnvironment.getApplication().createConfigurationContext(configuration)
            val strings = NagiStrings(context)
            assertEquals(label, strings(R.string.ui_new_tab))
            val tab = BrowserTab("test", "personal")
            assertEquals(label, strings.tabTitle(tab))
            assertEquals("User's name", strings.spaceName(Space("custom", "User's name")))
            assertEquals("User's name", strings.spaceName(Space("personal", "User's name")))
            val command = SuggestionProvider.suggestions(">" + label, WorkspaceSnapshot(), BrowserSettings(), strings::translate)
            assertTrue(command.any { it.action == SuggestionAction.Command(BrowserCommand.NEW_TAB) && it.title == label })
            val search = SuggestionProvider.suggestions("dy 日英 한국 中文", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all),
                BrowserSettings(), strings::translate).first()
            assertTrue(search.title.contains("抖音"))
            assertTrue(search.title.contains("日英 한국 中文"))
            assertFalse(search.title.contains("%1"))
            if (language != "en") assertNotEquals("Name is required", strings.translate("Name is required"))
        }
    }
}
