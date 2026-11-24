package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.data.room.entity
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class FavoriteEditingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var server: FixtureServer
    private val favoriteId = "favorite-edit-qa"
    private val otherId = "favorite-edit-other-qa"

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun favorites() = runBlocking { container.workspace.dao.observeBookmarks().first().filter { it.isFavorite } }
    private fun current() = favorites().first { it.id == favoriteId }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Favorite edit QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.workspace.dao.putBookmark(Bookmark(favoriteId, "${server.origin}/one", "Edit QA",
                isFavorite = true, createdAt = -100).entity())
            container.workspace.dao.putBookmark(Bookmark(otherId, "${server.origin}/other", "Other QA",
                isFavorite = true, createdAt = -99).entity())
        }
    }

    @After fun cleanup() {
        runBlocking {
            container.library.removeBookmark(favoriteId)
            container.library.removeBookmark(otherId)
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        shell("wm size $originalSize")
        server.close()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "favorite-edit-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun openEditor() {
        compose.onNodeWithContentDescription("Favorite actions for Edit QA").performClick()
        compose.onNodeWithText("Remove favorite").assertIsDisplayed()
        compose.onNodeWithTag("edit-favorite-$favoriteId").assertIsDisplayed().performClick()
        compose.onNodeWithTag("favorite-editor").assertIsDisplayed()
    }

    @Test fun tabletLightFavoriteMenuEditsAndPersistsTheSameEntry() = exercise(1000, ThemeMode.LIGHT)
    @Test fun phoneDarkSheetFavoriteMenuEditsAndPersistsTheSameEntry() = exercise(411, ThemeMode.DARK)

    private fun exercise(width: Int, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(theme = theme) } }
        if (width < 600) {
            compose.onNodeWithTag("compact-tabs-button").performClick()
            compose.onNodeWithText("Favorites").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("Favorite actions for Edit QA").fetchSemanticsNodes().isNotEmpty()
        }
        val before = favorites()
        compose.onNodeWithContentDescription("Favorite actions for Edit QA").performClick()
        compose.onNodeWithTag("edit-favorite-$favoriteId").assertIsDisplayed()
        capture("$width-menu")
        compose.onNodeWithTag("edit-favorite-$favoriteId").performClick()
        compose.onNodeWithTag("favorite-name").assertTextContains("Edit QA")
        compose.onNodeWithTag("favorite-url").assertTextContains("${server.origin}/one")
        compose.onNodeWithTag("favorite-name").performTextReplacement("Cancelled")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(before, favorites())

        openEditor()
        compose.onNodeWithTag("favorite-name").performTextReplacement("")
        compose.onNodeWithTag("save-favorite").assertIsNotEnabled()
        compose.onNodeWithTag("favorite-name").performTextReplacement("Renamed 日本語")
        compose.onNodeWithTag("favorite-url").performTextReplacement("javascript:alert(1)")
        compose.onNodeWithTag("save-favorite").assertIsNotEnabled()
        compose.onNodeWithTag("favorite-url").performTextReplacement("${server.origin}/other")
        compose.onNodeWithText("This URL is already in Favorites").assertIsDisplayed()
        compose.onNodeWithTag("save-favorite").assertIsNotEnabled()
        val destination = "${server.origin}/two?favorite=edited"
        compose.onNodeWithTag("favorite-url").performTextReplacement(destination)
        compose.onNodeWithTag("save-favorite").assertIsEnabled()
        capture("$width-editor")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("favorite-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("favorite-name").assertTextContains("Renamed 日本語")
        compose.onNodeWithTag("favorite-url").assertTextContains(destination)
        compose.onNodeWithTag("save-favorite").performClick()
        compose.waitUntil(10_000) { current().title == "Renamed 日本語" && current().url == destination }
        assertEquals(before.map { if (it.id == favoriteId) it.copy(title = "Renamed 日本語", url = destination) else it }, favorites())
        compose.onNodeWithContentDescription("Favorite Renamed 日本語").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { server.requests.any { it.path == "/two?favorite=edited" } }
        assertEquals("Renamed 日本語", current().title)
    }
}
