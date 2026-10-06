package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.search.DefaultSearchEngines
import com.takeruf.nagi.browser.search.SuggestionAction
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.WorkspaceSnapshot
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class BraceUrlCommandBarUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingBraceUrlShowsOpenCandidateAndSupportsEnterAndTouch() {
        var executed: SuggestionAction? = null
        compose.setContent {
            NagiTheme(com.takeruf.nagi.domain.model.ThemeMode.LIGHT) {
                CommandBar("", WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all), BrowserSettings(),
                    onDismiss = {}, onExecute = { executed = it })
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("https://qwen.ai/?q={query}")
        compose.onNodeWithTag("suggestion:navigate").assertIsSelected()
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), "brace-url.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle {
            assertEquals(SuggestionAction.Navigate("https://qwen.ai/?q=%7Bquery%7D"), executed)
            executed = null
        }
        compose.onNodeWithTag("suggestion:navigate").performClick()
        compose.runOnIdle {
            assertEquals(SuggestionAction.Navigate("https://qwen.ai/?q=%7Bquery%7D"), executed)
        }
    }
}
