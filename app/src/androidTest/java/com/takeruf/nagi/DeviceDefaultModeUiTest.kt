package com.takeruf.nagi

import android.content.Context
import android.app.Activity
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.domain.model.BrowserTab
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.browser.BrowserPane
import com.takeruf.nagi.ui.browser.BrowserUiState
import com.takeruf.nagi.ui.theme.NagiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic pane only: no accounts, network, screenshots, or browser storage. */
class DeviceDefaultModeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuKeepsAllThreeChoicesDistinctAndMarksTheCurrentChoice() {
        lateinit var context: Context
        lateinit var engine: TestEngine
        compose.setContent {
            context = LocalContext.current
            DisposableEffect(context) {
                val window = (context as Activity).window
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            engine = remember { TestEngine(context) }
            NagiTheme(ThemeMode.LIGHT) {
                Box(Modifier.width(344.dp).fillMaxHeight()) {
                    BrowserPane(BrowserTab("mode-test", "personal"), engine, BrowserUiState(),
                        focused = true, split = false, showFind = false,
                        onCloseFind = {}, onFind = {}, onFocus = {}, onOmnibox = {}, onOpen = {},
                        onSplit = {}, onCloseSplit = {}, onCloseTab = {}, onSwap = {},
                        onAdBlockingChange = { _, _ -> })
                }
            }
        }
        fun open() = compose.onNodeWithContentDescription(context.getString(R.string.ui_page_menu)).performClick()
        open()
        compose.onNodeWithTag("display-mode:desktop").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("display-mode:device-default").performScrollTo().performClick()
        open()
        compose.onNodeWithTag("display-mode:device-default").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("display-mode:mobile").performScrollTo().performClick()
        open()
        compose.onNodeWithTag("display-mode:mobile").assertIsSelected().assertIsNotEnabled()
        compose.onNodeWithTag("display-mode:desktop").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(listOf(SiteDisplayMode.DEVICE_DEFAULT, SiteDisplayMode.MOBILE, SiteDisplayMode.DESKTOP), engine.selections)
        }
    }

    private class TestEngine(context: Context) : BrowserEngine, AndroidEngineSurface {
        override val surface = View(context)
        override val state = MutableStateFlow(PageState(url = "https://example.test/", desktopMode = true))
        override val events = emptyFlow<EngineEvent>()
        override val supportsDeviceDefaultMode = true
        val selections = mutableListOf<SiteDisplayMode>()
        override fun setDesktopMode(enabled: Boolean) {
            selections += if (enabled) SiteDisplayMode.DESKTOP else SiteDisplayMode.MOBILE
            state.value = state.value.copy(desktopMode = enabled, deviceDefaultMode = false)
        }
        override fun setDeviceDefaultMode() {
            selections += SiteDisplayMode.DEVICE_DEFAULT
            state.value = state.value.copy(desktopMode = false, deviceDefaultMode = true)
        }
        override fun loadUrl(url: String) {}
        override fun reload() {}
        override fun goBack() {}
        override fun goForward() {}
        override fun canGoBack() = false
        override fun canGoForward() = false
        override fun evaluateJavascript(script: String) {}
        override fun findInPage(query: String) {}
        override fun findNext(forward: Boolean) {}
        override fun clearFind() {}
        override fun setVisible(visible: Boolean) {}
        override fun saveState(): EngineSnapshot? = null
        override fun restoreState(snapshot: EngineSnapshot) = false
        override fun destroy() {}
    }
}
