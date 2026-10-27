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

class PageTranslationUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var engine: TestEngine
    private var showTranslation by mutableStateOf(false)
    private var showExternal by mutableStateOf(false)
    private var targetLanguage by mutableStateOf<String?>(null)
    private var alwaysLanguages by mutableStateOf<Map<String, String>>(emptyMap())
    private fun content(width: Int = 400, url: String = "https://example.com/article") {
        compose.setContent {
            context = LocalContext.current
            engine = remember { TestEngine(context, url) }
            val page by engine.state.collectAsState()
            NagiTheme(ThemeMode.LIGHT) {
                Box(Modifier.width(width.dp).fillMaxHeight()) {
                    BrowserPane(BrowserTab("translation-test", "personal"), engine, BrowserUiState(), true,
                        false, false, {}, {}, {}, {}, {}, {}, {}, {}, {}, onTranslationOptions = { showTranslation = true })
                    if (showTranslation) PageTranslationNotice(page.translation,
                        onDismiss = { showTranslation = false }, modifier = browserNoticePlacement(width.dp),
                        targetLanguage = targetLanguage, onTargetLanguageChange = { targetLanguage = it },
                        alwaysTranslateLanguages = alwaysLanguages,
                        onAlwaysTranslationChange = { source, target -> alwaysLanguages =
                            if (target == null) alwaysLanguages - source else alwaysLanguages + (source to target) },
                        onTranslate = { source, target, wifiOnly, always ->
                            showTranslation = false
                            engine.translatePage(source, target, wifiOnly, always)
                        })
                    if (showExternal) ExternalAppNotice(BlockedExternalAppRequest("https://example.com", {}), {}, {}, {},
                        modifier = browserNoticePlacement(width.dp))
                }
            }
        }
    }
    private fun menu() = compose.onNodeWithContentDescription(context.getString(R.string.ui_page_menu)).performClick()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun menuOpensLanguageOptionsAndPassesSelectionAndRestoresOriginal() {
        content()
        menu()
        compose.onNodeWithTag("translate-page").assertIsDisplayed().performClick()
        compose.onNodeWithTag("translation-notice").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.ui_translate_with_google)).assertIsDisplayed()
        compose.onNodeWithTag("translation-source").performClick()
        compose.onNodeWithTag("translation-source-en").performScrollTo().performClick()
        compose.onNodeWithTag("translation-target").performClick()
        compose.onNodeWithTag("translation-target-ja").performScrollTo().performClick()
        screenshot("translation-dialog")
        compose.onNodeWithTag("translation-start").performClick()
        compose.runOnIdle { assertEquals(Triple("en", "ja", true), engine.request) }
        compose.onNodeWithTag("translation-bar").assertIsDisplayed()
        compose.onNodeWithTag("translation-attribution").assertIsDisplayed()
        screenshot("translation-result")
        menu()
        compose.onNodeWithTag("menu-translation-restore").performClick()
        compose.onNodeWithTag("translation-bar").assertDoesNotExist()
        compose.runOnIdle { assertTrue(engine.restored) }
    }
    @Test fun narrowPaneAllowsAutoDetectionAndMobileDownloadAndCancel() {
        content(width = 280)
        menu()
        compose.onNodeWithTag("translate-page").performScrollTo().performClick()
        compose.onNodeWithTag("translation-target").performClick()
        compose.onNodeWithTag("translation-target-ja").performScrollTo().performClick()
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-wifi").performClick()
        compose.onNodeWithTag("translation-start").performClick()
        compose.runOnIdle {
            assertEquals(Triple(null, "ja", false), engine.request)
            engine.state.value = engine.state.value.copy(translation = PageTranslationState(TranslationStage.DOWNLOADING, "en", "ja"))
        }
        compose.onNodeWithTag("translation-restore").assertIsDisplayed().performClick()
        compose.onNodeWithTag("translation-bar").assertDoesNotExist()
    }
    @Test fun newTabCannotBeTranslated() {
        content(url = "about:blank")
        menu()
        compose.onNodeWithTag("translate-page").assertIsNotEnabled()
    }

    @Test fun narrowResultKeepsOfficialAttributionAndRestoreInOneRow() {
        content(width = 280)
        compose.runOnIdle {
            engine.state.value = engine.state.value.copy(translation =
                PageTranslationState(TranslationStage.TRANSLATED, "en", "ja", 5))
        }
        val bar = compose.onNodeWithTag("translation-bar").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithTag("translation-attribution").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val restore = compose.onNodeWithTag("translation-restore").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(badge.left >= bar.left && badge.right <= restore.left)
        assertTrue(badge.top >= bar.top && badge.bottom <= bar.bottom)
        assertTrue(restore.top >= bar.top && restore.bottom <= bar.bottom)
        val density = context.resources.displayMetrics.density
        assertEquals(48f * density, bar.height, density)
        compose.onNodeWithTag("translation-restore").performClick()
        compose.onNodeWithTag("translation-bar").assertDoesNotExist()
    }

    @Test fun alwaysTranslationCanBeEnabledRemovedAndRequestedWithAutomaticSourceDetection() {
        content()
        menu()
        compose.onNodeWithTag("translate-page").performClick()
        compose.onNodeWithTag("translation-source").performClick()
        compose.onNodeWithTag("translation-source-en").performScrollTo().performClick()
        compose.onNodeWithTag("translation-target").performClick()
        compose.onNodeWithTag("translation-target-ja").performScrollTo().performClick()
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-always").performClick()
        compose.runOnIdle { assertEquals(mapOf("en" to "ja"), alwaysLanguages) }
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-always").performClick()
        compose.runOnIdle { assertTrue(alwaysLanguages.isEmpty()) }
        compose.onNodeWithTag("translation-source").performClick()
        compose.onNodeWithTag("translation-source-auto").performScrollTo().performClick()
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-always").performClick()
        compose.onNodeWithTag("translation-start").performClick()
        compose.runOnIdle { assertTrue(engine.alwaysRequested) }
    }

    @Test fun selectedTargetIsKeptAfterCancelAndOverridesAnOlderPageTranslation() {
        content()
        menu()
        compose.onNodeWithTag("translate-page").performClick()
        compose.onNodeWithTag("translation-target").performClick()
        compose.onNodeWithTag("translation-target-ja").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.ui_cancel)).performClick()
        compose.runOnIdle {
            assertEquals("ja", targetLanguage)
            engine.state.value = engine.state.value.copy(translation = PageTranslationState(TranslationStage.TRANSLATED, "en", "ko", 5))
        }
        menu()
        compose.onNodeWithTag("translate-page").performClick()
        compose.onNodeWithTag("translation-start").performClick()
        compose.runOnIdle { assertEquals(Triple(null, "ja", true), engine.request) }
    }

    @Test fun translationMatchesExternalNoticePositionAndWidthWithoutAModalScrim() {
        content(width = 600)
        compose.runOnIdle { showExternal = true }
        val external = compose.onNodeWithTag("external-app-notice").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { showExternal = false }
        menu()
        compose.onNodeWithTag("translate-page").performClick()
        val translation = compose.onNodeWithTag("translation-notice").fetchSemanticsNode().boundsInRoot
        assertEquals(external.top, translation.top, 1f)
        assertEquals(external.right, translation.right, 1f)
        assertEquals(external.width, translation.width, 1f)
        compose.onNodeWithContentDescription(context.getString(R.string.ui_page_menu)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Powered by Google Translate").assertDoesNotExist()
        compose.onNodeWithTag("translation-wifi").assertDoesNotExist()
        compose.onNodeWithTag("translation-about").assertDoesNotExist()
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-wifi").assertIsDisplayed()
        compose.onNodeWithTag("translation-about").performClick()
        compose.onNodeWithTag("translation-notice").assertIsDisplayed()
        compose.onNodeWithTag("translation-about-back").performClick()
        compose.onNodeWithTag("translation-source").assertIsDisplayed()
    }

    private class TestEngine(context: Context, url: String) : BrowserEngine, AndroidEngineSurface {
        override val surface = View(context)
        override val state = MutableStateFlow(PageState(url = url, title = "Article"))
        override val events = emptyFlow<EngineEvent>()
        var request: Triple<String?, String, Boolean>? = null
        var restored = false
        var alwaysRequested = false
        override fun translatePage(source: String?, target: String, wifiOnly: Boolean, alwaysTranslate: Boolean) {
            alwaysRequested = alwaysTranslate
            request = Triple(source, target, wifiOnly)
            state.value = state.value.copy(translation = PageTranslationState(TranslationStage.TRANSLATED, source, target, 5))
        }
        override fun restoreOriginalPage() { restored = true; state.value = state.value.copy(translation = PageTranslationState()) }
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
