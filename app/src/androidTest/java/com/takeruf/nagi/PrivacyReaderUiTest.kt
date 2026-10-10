package com.takeruf.nagi

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlinx.coroutines.flow.first

class PrivacyReaderUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    @Before fun setup() { server = FixtureServer() }
    @After fun cleanup() { server.close() }
    private fun pageMenu() {
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).onFirst().performClick()
    }
    private fun loadArticle(path: String = "/article") {
        val container = (compose.activity.application as NagiApplication).container
        kotlinx.coroutines.runBlocking {
            container.workspace.ready.await()
            container.settings.update { it.copy(selectedSpaceId = "personal", adBlockingEnabled = true, adBlockExcludedHosts = emptySet()) }
            container.tabs.create("personal", server.origin + path)
        }
        compose.waitUntil(20_000) { server.requests.any { it.path == path } }
        compose.waitForIdle()
    }
    @Test fun readerMenuFontControlsAndOriginalPage() {
        loadArticle()
        pageMenu()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("reader-mode").fetchSemanticsNodes().firstOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("reader-mode").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("reader-pane").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-font-larger").performClick()
        compose.onNodeWithTag("reader-font-smaller").performClick()
        val screenshot = compose.onRoot().captureToImage()
        val bitmap = screenshot.asAndroidBitmap()
        val file = File(compose.activity.getExternalFilesDir(null), "reader-mode.png")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("exit-reader").performClick()
        compose.onNodeWithTag("reader-pane").assertDoesNotExist()
    }
    @Test fun privateModeOpensWithSecureWindowAndClosesBackToNormal() {
        loadArticle()
        pageMenu()
        compose.onNodeWithTag("open-private-mode").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("close-private-mode").fetchSemanticsNodes().isNotEmpty() }
        var secured = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val lifecycle = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
            secured = lifecycle.getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filterIsInstance<PrivateActivity>().any { it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0 }
        }
        assertTrue(secured)
        compose.onNodeWithTag("close-private-mode").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("close-private-mode").fetchSemanticsNodes().isEmpty() }
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).onFirst().assertExists()
    }
    private fun shield() { compose.onAllNodesWithTag("ad-blocking-shield").onFirst().performClick() }
    @Test fun shieldPausesVisitAndResumesWithoutSavingException() {
        loadArticle("/article?local-ad&shield-visit")
        shield()
        compose.onNodeWithTag("blocking-visit-toggle").assertTextContains("Pause blocking for this visit", substring = true)
        val bitmap = compose.onNodeWithTag("ad-blocking-popup").captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "shield-menu.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("blocking-visit-toggle").performClick()
        compose.waitUntil(20_000) { server.requests.any { it.path == "/ads/custom_ads.js" } }
        val container = (compose.activity.application as NagiApplication).container
        assertTrue(kotlinx.coroutines.runBlocking { container.settings.settings.first() }.adBlockExcludedHosts.isEmpty())
        shield()
        compose.onNodeWithText("Paused for this visit").assertExists()
        compose.onNodeWithTag("blocking-visit-toggle").assertTextContains("Resume blocking", substring = true).performClick()
        shield()
        compose.onNodeWithText("Blocking ads and trackers").assertExists()
        compose.onNodeWithTag("blocking-visit-toggle").assertTextContains("Pause blocking for this visit", substring = true)
    }
    @Test fun shieldCanSaveAndRemoveSiteExceptionWithAutomaticReload() {
        loadArticle("/article?local-ad&shield-site")
        shield()
        compose.onNodeWithTag("blocking-site-toggle").performClick()
        compose.waitUntil(20_000) { server.requests.any { it.path == "/ads/custom_ads.js" } }
        shield()
        compose.onNodeWithText("Allowed on this site").assertExists()
        compose.onNodeWithTag("blocking-site-toggle").assertTextContains("Enable blocking on this site", substring = true).performClick()
        val container = (compose.activity.application as NagiApplication).container
        compose.waitUntil(10_000) { kotlinx.coroutines.runBlocking {
            "127.0.0.1" !in container.settings.settings.first().adBlockExcludedHosts
        } }
        compose.waitForIdle()
        shield()
        compose.onNodeWithText("Blocking ads and trackers").assertExists()
        compose.onNodeWithTag("blocking-visit-toggle").assertExists()
    }
    @Test fun visibleShieldKeepsSiteToggleOutOfPageMenu() {
        loadArticle()
        pageMenu()
        compose.onNodeWithTag("site-ad-blocking").assertDoesNotExist()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 4")).use { it.readBytes() }
        compose.waitForIdle()
        shield()
        compose.onNodeWithTag("blocking-site-toggle").performClick()
        shield()
        compose.onNodeWithTag("blocking-site-toggle").assertTextContains("Enable blocking", substring = true).performClick()
        pageMenu()
        compose.onNodeWithTag("site-ad-blocking").assertDoesNotExist()
    }
}
