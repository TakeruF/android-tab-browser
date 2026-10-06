package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.search.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalTestApi::class)
class ChatGptCommandBarUiTest {
    @get:Rule val compose = createComposeRule()
    private var executed: SuggestionAction? = null
    private var mode by mutableStateOf(ThemeMode.LIGHT)

    private fun content(query: String) {
        compose.setContent {
            NagiTheme(mode) {
                CommandBar(query, WorkspaceSnapshot(searchEngines = DefaultSearchEngines.all), BrowserSettings(),
                    onDismiss = {}, onExecute = { executed = it })
            }
        }
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "chatgpt-validation").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun arrowsSelectAiOrSearchAndEnterExecutesSelectedAction() {
        content("android tablet")
        compose.onNodeWithTag("suggestion:primary").assertIsSelected()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsSelected()
        screenshot("short-query-ai-selected")
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("suggestion:primary").assertIsSelected()
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(SuggestionAction.Navigate(ChatGptSearch.url("android tablet")), executed) }
    }

    @Test fun questionPrefersAiButTouchCanChooseSearchAndEditingResetsSelection() {
        content("Explain inflation?")
        compose.onNodeWithTag("suggestion:ai:chatgpt").assertIsSelected()
        screenshot("question-light")
        compose.runOnIdle { mode = ThemeMode.DARK }
        screenshot("question-dark")
        compose.onNodeWithTag("suggestion:primary").performClick()
        compose.runOnIdle { assertTrue((executed as SuggestionAction.Navigate).url.startsWith("https://www.google.com/")) }
        compose.onNode(hasSetTextAction()).performTextReplacement("android tablet")
        compose.onNodeWithTag("suggestion:primary").assertIsSelected()
        compose.onNodeWithTag("suggestion:ai:chatgpt").performClick()
        compose.runOnIdle { assertEquals(SuggestionAction.Navigate(ChatGptSearch.url("android tablet")), executed) }
    }
}

/** Verifies the same action reaches the real browser session, not only the dialog callback. */
class ChatGptBrowserHandoffUiTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    @Test fun touchHandoffNavigatesActiveTabAndClosesCommandBar() {
        val container = (compose.activity.application as NagiApplication).container
        var spaceId = ""
        val original = kotlinx.coroutines.runBlocking {
            container.workspace.ready.await()
            container.settings.settings.first()
        }
        try {
            kotlinx.coroutines.runBlocking {
                container.settings.update { BrowserSettings(automaticSearchRegion = false) }
                container.spaces.create("ChatGPT QA ${System.nanoTime()}")
                spaceId = container.settings.settings.first().selectedSpaceId
            }
            val tabId = kotlinx.coroutines.runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("browser-pane-left-$tabId").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle {
                compose.activity.window.callback.dispatchKeyEvent(android.view.KeyEvent(0, 0,
                    android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_L, 0, android.view.KeyEvent.META_CTRL_ON))
            }
            compose.onNode(hasSetTextAction()).performTextReplacement("Nagi browser test")
            compose.onNodeWithTag("suggestion:ai:chatgpt").performClick()
            try {
                compose.waitUntil(10_000) {
                    kotlinx.coroutines.runBlocking {
                        container.workspace.dao.tabs(spaceId).any { it.url == ChatGptSearch.url("Nagi browser test") }
                    }
                }
            } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
                val actual = kotlinx.coroutines.runBlocking { container.workspace.dao.tabs(spaceId).map { it.url } }
                val views = mutableListOf<String>()
                fun inspect(view: android.view.View) {
                    if (view is android.webkit.WebView) views += "url=${view.url}, original=${view.originalUrl}, progress=${view.progress}"
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i))
                }
                compose.runOnIdle { inspect(compose.activity.window.decorView) }
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val outputDirectory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?.let(::File) ?: compose.activity.getExternalFilesDir(null)!!
                outputDirectory.mkdirs()
                File(outputDirectory, "handoff-audit.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                throw AssertionError("ChatGPT handoff URL not observed; actual tab URLs: $actual; WebViews: $views", failure)
            }
            compose.onNodeWithTag("suggestion:ai:chatgpt").assertDoesNotExist()
        } finally {
            kotlinx.coroutines.runBlocking {
                if (spaceId.isNotEmpty()) container.spaces.delete(spaceId)
                container.settings.update { original }
            }
        }
    }
}
