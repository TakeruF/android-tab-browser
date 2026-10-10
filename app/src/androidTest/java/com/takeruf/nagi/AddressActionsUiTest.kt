package com.takeruf.nagi

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.browser.BrowserPane
import com.takeruf.nagi.ui.browser.BrowserUiState
import com.takeruf.nagi.ui.theme.NagiTheme
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class AddressActionsUiTest {
    @get:Rule val compose = createComposeRule()
    private var paneWidth by mutableIntStateOf(400)
    private var settings by mutableStateOf(BrowserUiState())
    private lateinit var context: Context

    private fun content(split: Boolean = false) {
        compose.setContent {
            context = LocalContext.current
            val engine = remember { TestEngine(context) }
            NagiTheme(ThemeMode.LIGHT) {
                Box(Modifier.width(paneWidth.dp).fillMaxHeight()) {
                    BrowserPane(BrowserTab("test", "personal"), engine, settings,
                        focused = true, split = split, showFind = false,
                        onCloseFind = {}, onFind = {}, onFocus = {}, onOmnibox = {}, onOpen = {},
                        onSplit = {}, onCloseSplit = {}, onCloseTab = {}, onSwap = {},
                        onAdBlockingChange = { host, enabled ->
                            settings = settings.copy(settings = settings.settings.copy(adBlockExcludedHosts =
                                if (enabled) settings.settings.adBlockExcludedHosts - host
                                else settings.settings.adBlockExcludedHosts + host))
                        })
                }
            }
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(context.getString(R.string.ui_page_menu)).performClick()
    }

    @Test fun resizingMovesOnlyHiddenActionsToTheTopInPriorityOrder() {
        content()
        for (splitWidth in listOf(400, 344, 312, 280, 400)) {
            compose.runOnIdle { paneWidth = splitWidth }
            val visible = when (splitWidth) { 400 -> 3; 344 -> 2; 312 -> 1; else -> 0 }
            val inline = listOf("address-share-url:test", "address-copy-url:test", "ad-blocking-shield")
            val menu = listOf("menu-share-url:test", "menu-copy-url:test", "site-ad-blocking")
            inline.forEachIndexed { index, tag ->
                if (index < visible) compose.onNodeWithTag(tag).assertIsDisplayed()
                else compose.onNodeWithTag(tag).assertDoesNotExist()
            }
            openMenu()
            menu.take(visible).forEach { compose.onNodeWithTag(it).assertDoesNotExist() }
            val hiddenRows = menu.drop(visible).map {
                compose.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            }
            val readerTop = compose.onNodeWithTag("reader-mode").fetchSemanticsNode().boundsInRoot.top
            assertTrue(hiddenRows.zipWithNext().all { (first, second) -> first < second })
            assertTrue(hiddenRows.all { it < readerTop })
            val directory = File(context.getExternalFilesDir(null), "address-actions-validation").apply { mkdirs() }
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                File(directory, "$splitWidth.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("input keyevent 4")).use { it.readBytes() }
            compose.waitForIdle()
            compose.onNode(isPopup()).assertDoesNotExist()
        }
    }

    @Test fun narrowSplitKeepsCopyAndSiteBlockingActionsWorking() {
        paneWidth = 280
        content(split = true)
        openMenu()
        compose.onNodeWithTag("menu-copy-url:test").performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals("https://example.com/article", context.getSystemService(ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.text?.toString())
        }
        openMenu()
        compose.onNodeWithTag("site-ad-blocking").performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        compose.runOnIdle { assertTrue("example.com" in settings.settings.adBlockExcludedHosts) }
        openMenu()
        compose.onNodeWithTag("site-ad-blocking").performClick()
        compose.runOnIdle { assertTrue(settings.settings.adBlockExcludedHosts.isEmpty()) }
    }

    private class TestEngine(context: Context) : BrowserEngine, AndroidEngineSurface {
        override val surface = View(context)
        override val state = MutableStateFlow(PageState(url = "https://example.com/article", title = "Article"))
        override val events = emptyFlow<EngineEvent>()
        override fun loadUrl(url: String) {}
        override fun reload() {}
        override fun goBack() {}
        override fun goForward() {}
        override fun canGoBack() = false
        override fun canGoForward() = false
        override fun evaluateJavascript(script: String) {}
        override fun setDesktopMode(enabled: Boolean) {}
        override fun findInPage(query: String) {}
        override fun findNext(forward: Boolean) {}
        override fun clearFind() {}
        override fun setVisible(visible: Boolean) {}
        override fun saveState(): EngineSnapshot? = null
        override fun restoreState(snapshot: EngineSnapshot) = false
        override fun destroy() {}
    }
}
