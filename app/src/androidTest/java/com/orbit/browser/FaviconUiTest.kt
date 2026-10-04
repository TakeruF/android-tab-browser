package com.orbit.browser

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.orbit.browser.data.room.entity
import com.orbit.browser.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

@OptIn(ExperimentalTestApi::class)
class FaviconUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var spaceId: String
    private val ids = List(3) { UUID.randomUUID().toString() }

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as OrbitApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create("Favicon QA")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.workspace.dao.putBookmark(Bookmark(ids[0], "${server.origin}/icon-page", "Remote icon", isFavorite = true, createdAt = -3).entity())
            container.workspace.dao.putBookmark(Bookmark(ids[1], "${server.origin}/old", "Missing icon", "/missing/favicon.png", isFavorite = true, createdAt = -2).entity())
            container.workspace.dao.putBookmark(Bookmark(ids[2], "${server.origin}/missing", "Unavailable icon", "${server.origin}/not-an-image", isFavorite = true, createdAt = -1).entity())
        }
    }
    @After fun cleanup() {
        runBlocking {
            ids.forEach { container.library.removeBookmark(it) }
            container.spaces.delete(spaceId)
            container.settings.update { original }
        }
        server.close()
    }
    private fun hasColor(description: String, color: Int): Boolean {
        val node = compose.onNodeWithContentDescription(description)
        if (compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty()) return false
        val bitmap = node.captureToImage().asAndroidBitmap()
        return (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == color } }
    }
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun favoritesFetchIconsRecoverMissingFilesAndKeepCustomIconsAfterReload() {
        compose.waitUntil(15_000) { hasColor("Favorite Remote icon", Color.MAGENTA) }
        compose.waitUntil(15_000) { hasColor("Favorite Missing icon", Color.MAGENTA) }
        compose.onNodeWithText("U", useUnmergedTree = true).assertExists()
        screenshot("favicon-fallback.png")

        compose.onNodeWithContentDescription("Favorite Remote icon").performClick()
        compose.waitUntil(15_000) { runBlocking {
            container.workspace.dao.tabs(spaceId).any { it.title == "Favicon Fixture" && it.faviconUrl != null }
        } }
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Pin to sidebar").performClick()
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.tabs(spaceId).any { it.isPinned } } }
        compose.waitUntil(15_000) { hasColor("Favorite Remote icon", Color.CYAN) }
        compose.waitUntil(15_000) { hasColor("Tab Favicon Fixture", Color.CYAN) }
        val path = runBlocking { container.workspace.dao.tabs(spaceId).first { it.isPinned }.faviconUrl!! }
        assertTrue(File(path).isFile)
        assertTrue(path.startsWith(compose.activity.filesDir.absolutePath))

        compose.onNodeWithContentDescription("Reload").performClick()
        compose.waitUntil(15_000) { hasColor("Tab Favicon Fixture", Color.CYAN) }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { hasColor("Favorite Remote icon", Color.CYAN) }
        compose.waitUntil(15_000) { hasColor("Tab Favicon Fixture", Color.CYAN) }
        screenshot("favicon-pinned.png")
    }
}
