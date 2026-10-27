package com.takeruf.nagi

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.translation.*
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.browser.*
import com.takeruf.nagi.ui.theme.NagiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File

class GoogleSignInRecoveryUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var engine: TestEngine
    private lateinit var context: Context
    private val rejected = "https://accounts.google.com/v3/signin/rejected?hl=zh-CN"
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun content(width: Int = 900, translated: Boolean = false) {
        compose.setContent {
            context = LocalContext.current
            engine = remember { TestEngine(context, PageState(url = rejected, desktopMode = true,
                translation = if (translated) PageTranslationState(TranslationStage.TRANSLATED, "en", "ja", 5) else PageTranslationState())) }
            NagiTheme(ThemeMode.LIGHT) {
                Box(Modifier.width(width.dp).fillMaxHeight()) {
                    BrowserPane(BrowserTab("sign-in-test", "personal"), engine, BrowserUiState(), true,
                        false, false, {}, {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
    }

    @Test fun noticeUsesTheTranslationBarStyleAndRetriesTheCorrectPane() {
        content(translated = true)
        compose.onNodeWithText(context.getString(R.string.ui_google_sign_in_mobile_hint)).assertIsDisplayed()
        val translation = compose.onNodeWithTag("translation-bar").fetchSemanticsNode().boundsInRoot
        val hint = compose.onNodeWithTag("google-sign-in-recovery-bar").fetchSemanticsNode().boundsInRoot
        assertEquals(translation.left, hint.left, 1f)
        assertEquals(translation.width, hint.width, 1f)
        assertEquals(translation.height, hint.height, 1f)
        screenshot("google-sign-in-hint-wide")
        compose.onNodeWithTag("google-sign-in-mobile-retry").performClick()
        compose.runOnIdle { assertEquals(1, engine.retries) }
        compose.onNodeWithTag("google-sign-in-recovery-bar").assertDoesNotExist()
    }

    @Test fun dismissIsLocalToTheRejectedDocument() {
        content(320)
        compose.onNodeWithTag("google-sign-in-mobile-retry").assertIsDisplayed()
        screenshot("google-sign-in-hint-narrow")
        compose.onNodeWithTag("google-sign-in-hint-dismiss").performClick()
        compose.onNodeWithTag("google-sign-in-recovery-bar").assertDoesNotExist()
        compose.runOnIdle { engine.state.value = PageState(url = "https://example.com/", desktopMode = true) }
        compose.waitForIdle()
        compose.runOnIdle { engine.state.value = PageState(url = rejected, desktopMode = true) }
        compose.onNodeWithTag("google-sign-in-recovery-bar").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, engine.retries) }
    }

    @Test fun hintDoesNotAppearOnOtherSitesNormalSignInMobilePagesOrNetworkErrors() {
        content()
        for (page in listOf(PageState(url = rejected, desktopMode = false),
            PageState(url = "https://example.com/v3/signin/rejected", desktopMode = true),
            PageState(url = "https://accounts.google.com/v3/signin/identifier", desktopMode = true),
            PageState(url = rejected, desktopMode = true, isLoading = true),
            PageState(url = rejected, desktopMode = true, error = "Offline"))) {
            compose.runOnIdle { engine.state.value = page }
            compose.onNodeWithTag("google-sign-in-recovery-bar").assertDoesNotExist()
        }
    }

    private class TestEngine(context: Context, initial: PageState) : BrowserEngine, AndroidEngineSurface {
        override val state = MutableStateFlow(initial)
        override val events = emptyFlow<EngineEvent>()
        override val surface = View(context)
        var retries = 0
        override fun retryGoogleSignInInMobileMode() { retries++; state.value = state.value.copy(desktopMode = false) }
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
