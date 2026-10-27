package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Verify the app-level popup host, including dismissal when its tab is no longer visible. */
class PageTranslationPopupAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    @Before fun setup() { server = FixtureServer() }
    @After fun cleanup() { server.close() }

    @Test fun menuShowsTopRightCardAndSwitchingTabsDismissesIt() {
        val container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { it.copy(selectedSpaceId = "personal") }
            container.tabs.create("personal", server.origin + "/article")
        }
        compose.waitUntil(20_000) { server.requests.any { it.path == "/article" } }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).onFirst().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("translate-page").fetchSemanticsNodes().firstOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("translate-page").performClick()
        val popup = compose.onNodeWithTag("translation-notice").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(root.right - 16 * density, popup.right, density)
        assertTrue("Popup should be below the address bar near the top", popup.top < root.top + 120 * density)
        compose.onNodeWithContentDescription("Powered by Google Translate").assertDoesNotExist()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "translation-tablet-popup.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        compose.onNodeWithTag("translation-wifi").assertDoesNotExist()
        compose.onNodeWithTag("translation-about").assertDoesNotExist()
        compose.onNodeWithTag("translation-more").performClick()
        compose.onNodeWithTag("translation-wifi").assertIsDisplayed()
        compose.onNodeWithTag("translation-about").assertIsDisplayed()
        compose.waitForIdle()
        val options = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "translation-tablet-options.png").outputStream().use {
            options.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        options.recycle()
        compose.onNodeWithTag("translation-about").performClick()
        compose.onNodeWithTag("translation-about-back").performClick()
        runBlocking { container.tabs.create("personal", "about:blank") }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("translation-notice").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun choosingTargetSavesImmediatelyAndSurvivesActivityRecreationOnAnotherPage() {
        val container = (compose.activity.application as NagiApplication).container
        val previous = runBlocking { container.settings.settings.first().translationTargetLanguage }
        fun openTranslation(path: String) {
            runBlocking { container.tabs.create("personal", server.origin + path) }
            compose.waitUntil(20_000) { server.requests.any { it.path == path } }
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).onFirst().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("translate-page").fetchSemanticsNodes().firstOrNull()?.config
                    ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
            }
            compose.onNodeWithTag("translate-page").performClick()
        }
        fun chooseTarget(code: String) {
            compose.onNodeWithTag("translation-target").performClick()
            compose.onNodeWithTag("translation-target-$code").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                runBlocking { container.settings.settings.first().translationTargetLanguage == code }
            }
            compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).performClick()
        }
        fun assertTarget(code: String) {
            val locale = compose.activity.resources.configuration.locales[0]
            compose.onNodeWithTag("translation-target").assertTextContains(java.util.Locale.forLanguageTag(code).getDisplayLanguage(locale))
        }
        try {
            runBlocking {
                container.workspace.ready.await()
                container.settings.update { it.copy(selectedSpaceId = "personal", translationTargetLanguage = "en") }
            }
            openTranslation("/article?target-first")
            chooseTarget("ja")
            compose.activityRule.scenario.recreate()
            openTranslation("/article?target-reopened")
            assertTarget("ja")
            chooseTarget("ko")
            compose.activityRule.scenario.recreate()
            openTranslation("/article?target-latest")
            assertTarget("ko")
        } finally {
            runBlocking { container.settings.update { it.copy(translationTargetLanguage = previous) } }
        }
    }
}
