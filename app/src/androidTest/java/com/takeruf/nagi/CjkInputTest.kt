package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.SearchEngine
import com.takeruf.nagi.data.room.entity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.net.URLEncoder

/** Exercises Android's real InputConnection, not just Compose text replacement semantics. */
class CjkInputTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var ime: TestIme
    private lateinit var server: FixtureServer
    @Before fun setup() {
        ime = TestIme()
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            // A device-local provider avoids redirects/CAPTCHAs rewriting the asserted query.
            // Insert at the test seam; HTTPS provider validation has separate coverage.
            container.workspace.dao.putEngine(SearchEngine("cjk-fixture", "CJK Fixture", "cjkfixture", "${server.origin}/one?q={query}").entity())
            container.settings.update { BrowserSettings(defaultSearchEngineId = "cjk-fixture", automaticSearchRegion = false) }
            container.spaces.create("CJK ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
        val tabId = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("browser-pane-left-$tabId").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        ime.close(); server.close()
        runBlocking { container.engines.delete("cjk-fixture") }
    }
    private fun connection(action: Int = EditorInfo.IME_ACTION_GO): InputConnection {
        compose.waitForIdle()
        return ime.connection(action = action)
    }
    private fun key(ic: InputConnection, code: Int) {
        compose.runOnIdle {
            ic.sendKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, code))
        }
        compose.waitForIdle()
    }
    private fun exercise(provisional: List<String>, committed: String, useImeAction: Boolean) {
        ime.awaitBinding()
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN,
            AndroidKeyEvent.KEYCODE_L, 0, AndroidKeyEvent.META_CTRL_ON)) }
        val ic = connection()
        provisional.forEach { value ->
            compose.runOnIdle { ic.setComposingText(value, 1) }
            compose.onNode(hasSetTextAction()).assertTextEquals(value)
        }
        key(ic, AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        key(ic, AndroidKeyEvent.KEYCODE_ENTER)
        compose.onNode(hasSetTextAction()).assertTextEquals(provisional.last())
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithText("Esc").assertExists()
        assertEquals("about:blank", runBlocking { container.workspace.dao.tabs(spaceId).first { it.closedAt == null }.url })
        // Editor actions can also arrive while a candidate is still being composed.
        compose.runOnIdle { ic.performEditorAction(EditorInfo.IME_ACTION_GO) }
        compose.onNode(hasSetTextAction()).assertTextEquals(provisional.last())
        compose.runOnIdle { ic.commitText(committed, 1); ic.finishComposingText() }
        compose.onNode(hasSetTextAction()).assertTextEquals(committed)
        // Reselect and reconvert a segment, exercising replacement rather than appending.
        compose.runOnIdle { ic.setComposingRegion(0, committed.length); ic.setComposingText(committed, 1); ic.finishComposingText() }
        compose.onNode(hasSetTextAction()).assertTextEquals(committed)
        compose.waitUntil(5_000) { compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "Ready" }
        if (useImeAction) compose.runOnIdle { ic.performEditorAction(EditorInfo.IME_ACTION_GO) }
        else key(ic, AndroidKeyEvent.KEYCODE_ENTER)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Esc").fetchSemanticsNodes().isEmpty() }
        val encoded = URLEncoder.encode(committed, "UTF-8")
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tabs(spaceId).any { it.url.contains(encoded) } } }
    }
    @Test fun japaneseConversionEnterCommitAndReconversion() = exercise(listOf("に", "にほん", "にほんごのけんさく"), "日本語の検索", false)
    @Test fun chinesePinyinCommitAndImeGo() = exercise(listOf("zh", "zhongwen", "zhongwensousuo"), "中文搜索", true)
    @Test fun koreanJamoCompositionAndEnter() = exercise(listOf("ㅎ", "하", "한", "한구", "한국어 검색"), "한국어 검색", false)
    @Test fun engineNameKeepsJapaneseChineseAndKoreanComposition() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("customize-search-engines").performScrollTo().performClick()
        compose.onNodeWithText("Add search engine").performScrollTo().performClick()
        compose.onNodeWithText("Name").performClick()
        val ic = connection(EditorInfo.IME_ACTION_DONE)
        for ((preedit, result) in listOf("にほんご" to "日本語", "zhongwen" to "中文", "ㅎㅏㄴㄱㅜㄱㅇㅓ" to "한국어")) {
            compose.runOnIdle { ic.setSelection(0, ic.getTextBeforeCursor(100, 0)?.length ?: 0); ic.commitText("", 1); ic.setComposingText(preedit, 1) }
            compose.onNodeWithText("Name").assertTextContains(preedit)
            compose.runOnIdle { ic.commitText(result, 1); ic.finishComposingText() }
            compose.onNodeWithText("Name").assertTextContains(result)
        }
        compose.onNodeWithText("Cancel").performClick()
    }
}
