package com.takeruf.nagi

import android.webkit.CookieManager
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ReleaseFeaturesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var tabId: String
    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await(); container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Release QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.tabs.create(spaceId, "${server.origin}/one")
        }
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tab(tabId)?.title == "Fixture One" } }
    }
    @After fun cleanup() { runBlocking { container.spaces.delete(spaceId) }; server.close() }
    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: return
        directory.mkdirs()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun bookmarkCanBeSavedOpenedAndRemovedFromLibrary() {
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Save bookmark").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.observeBookmarks().first().any { it.url == "${server.origin}/one" && !it.isFavorite } } }
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNodeWithText("Search bookmarks").assertExists()
        screenshot("release-bookmarks")
        compose.onNodeWithTag("library-row:${server.origin}/one").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).count { it.url == "${server.origin}/one" && it.closedAt == null } == 2 } }
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.onNode(hasContentDescription("Remove Fixture One") and hasAnyAncestor(hasTestTag("library-row:${server.origin}/one"))).performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.observeBookmarks().first().none { it.url == "${server.origin}/one" && !it.isFavorite } } }
    }

    @Test fun clearingSiteDataRequiresConfirmationAndPreservesWorkspace() {
        var cookie: String? = null
        compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }
        assertTrue(cookie?.contains("nagi=cookie") == true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Clear cookies and site data"))
        compose.onNodeWithText("Clear cookies and site data").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }
        assertNotNull(cookie)
        compose.onNodeWithText("Clear cookies and site data").performClick()
        compose.onNodeWithText("Clear data").performClick()
        compose.waitUntil(10_000) { compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }; cookie == null }
        assertEquals("${server.origin}/one", runBlocking { container.workspace.dao.tab(tabId)!!.url })
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("app-updates"))
        compose.onNodeWithText("App updates").assertIsDisplayed()
        compose.onNodeWithText("Current version: 0.1.1").assertExists()
        screenshot("release-updates")
    }
}
