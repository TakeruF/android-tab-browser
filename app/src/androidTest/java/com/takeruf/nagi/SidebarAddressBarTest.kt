package com.takeruf.nagi

import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.localization.NagiStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.assertEquals
import java.io.File

class SidebarAddressBarTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var original: BrowserSettings
    private lateinit var spaceId: String
    private val strings get() = NagiStrings(compose.activity)

    @Before fun setup() = runBlocking {
        container = (compose.activity.application as NagiApplication).container
        container.workspace.ready.await()
        original = container.settings.settings.first()
        server = FixtureServer()
        container.settings.update { it.copy(sidebarCollapsed = false, theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
        container.spaces.create("Address bar QA")
        spaceId = container.settings.settings.first().selectedSpaceId
    }

    @After fun cleanup() = runBlocking {
        container.spaces.delete(spaceId)
        container.settings.update { original }
        server.close()
    }

    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun copyIsAlwaysVisibleAndCopiesFullUrlWithFeedback() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("sidebar-address").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sidebar-copy-url").assertDoesNotExist()
        val url = "${server.origin}/one?query=hello%20world#section"
        val tabId = runBlocking { container.tabs.create(spaceId, url) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("sidebar-copy-url").fetchSemanticsNodes().isNotEmpty() }
        // A mouse elsewhere must not hide the action.
        compose.onNodeWithTag("sidebar").performMouseInput { moveTo(Offset(10f, 300f)) }
        compose.onNodeWithTag("sidebar-copy-url").assertIsDisplayed()
        screenshot("sidebar-address-always-visible")
        compose.onNodeWithTag("sidebar-copy-url").performClick()
        compose.onNodeWithTag("sidebar-copy-url").assert(hasContentDescription(strings(R.string.ui_link_copied)))
        compose.runOnIdle {
            assertEquals(url, compose.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
        }
        screenshot("sidebar-address-copied")
        compose.mainClock.advanceTimeBy(1600)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("sidebar-copy-url") and hasContentDescription(strings(R.string.ui_copy_link))).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("sidebar-copy-url").assertIsDisplayed()
        compose.onNodeWithTag("sidebar-share-url").assertIsDisplayed().assertHasClickAction()
        val toolbarCopy = compose.onNodeWithTag("address-copy-url:$tabId")
        toolbarCopy.performClick()
        toolbarCopy.assert(hasContentDescription(strings(R.string.ui_link_copied)))
        compose.runOnIdle {
            assertEquals(url, compose.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
        }
        screenshot("toolbar-address-copied")
        compose.mainClock.advanceTimeBy(1600)
        toolbarCopy.assert(hasContentDescription(strings(R.string.ui_copy_link)))
        compose.onNodeWithTag("sidebar-address").performClick()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }
}
