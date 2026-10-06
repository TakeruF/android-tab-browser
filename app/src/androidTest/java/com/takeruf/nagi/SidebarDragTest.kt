package com.takeruf.nagi

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.domain.model.Bookmark
import com.takeruf.nagi.data.room.BookmarkEntity
import com.takeruf.nagi.data.room.entity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class SidebarDragTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var alpha: String
    private lateinit var beta: String
    private lateinit var gamma: String
    private var originalFavorites = emptyList<BookmarkEntity>()
    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            // Earlier browsing tests add favorites. Keep the drag geometry deterministic,
            // then restore those entries rather than accumulating fixture state.
            originalFavorites = container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }
            originalFavorites.forEach { container.library.removeBookmark(it.id) }
            listOf("Gmail", "ChatGPT", "GitHub").forEachIndexed { index, title ->
                container.workspace.dao.putBookmark(Bookmark("drag-fixture-$index", "https://example.com/$title", title,
                    isFavorite = true, createdAt = index.toLong()).entity())
            }
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create("Research")
            spaceId = container.settings.settings.first().selectedSpaceId
            alpha = container.tabs.create(spaceId, "https://example.com/alpha", false)
            beta = container.tabs.create(spaceId, "https://example.com/beta", false)
            gamma = container.tabs.create(spaceId, "https://example.com/gamma", false)
            listOf(alpha to "Design references", beta to "Reading list", gamma to "Project notes").forEach { (id, title) ->
                container.tabs.updatePage(id, "https://example.com/$id", title, null)
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tab Project notes").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() = runBlocking {
        container.workspace.dao.observeBookmarks().first().filter { it.isFavorite }.forEach { container.library.removeBookmark(it.id) }
        originalFavorites.forEach { container.workspace.dao.putBookmark(it) }
        if (::spaceId.isInitialized) container.spaces.delete(spaceId)
        if (::beta.isInitialized && container.workspace.dao.tab(beta)?.spaceId == "work") container.tabs.close(beta)
    }
    private fun tab(title: String) = compose.onNodeWithContentDescription("Tab $title")
    private fun saveScreen(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun drag(source: SemanticsNodeInteraction, destination: SemanticsNodeInteraction, after: Boolean = false, cancel: Boolean = false) {
        val bounds = source.fetchSemanticsNode().boundsInRoot
        val target = destination.fetchSemanticsNode().boundsInRoot
        val end = Offset(target.center.x - bounds.left, (if (after) target.bottom - 5f else target.top + 5f) - bounds.top)
        source.performTouchInput {
            down(Offset(bounds.width * 0.45f, bounds.height / 2f)); advanceEventTime(650)
            val start = Offset(bounds.width * 0.45f, bounds.height / 2f)
            for (i in 1..16) moveTo(start + (end - start) * (i / 16f), delayMillis = 20)
            if (cancel) cancel() else up()
        }
    }
    @Test fun wholeRowDragReordersPinsUnpinsAndCancelDoesNotMove() {
        drag(tab("Design references"), tab("Project notes"), after = true)
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).last().id == alpha } }
        drag(tab("Project notes"), compose.onNodeWithText("Keep your everyday tabs here"))
        compose.waitUntil { runBlocking { container.workspace.dao.tab(gamma)!!.isPinned } }
        drag(tab("Reading list"), tab("Design references"), after = true, cancel = true)
        compose.waitForIdle()
        assertFalse(runBlocking { container.workspace.dao.tab(beta)!!.isPinned })
        assertEquals(listOf(beta, alpha), runBlocking { container.workspace.dao.tabs(spaceId).filter { !it.isPinned && it.id in listOf(alpha, beta) }.map { it.id } })
        drag(tab("Project notes"), tab("Reading list"), after = false)
        compose.waitUntil { runBlocking { !container.workspace.dao.tab(gamma)!!.isPinned } }
        assertEquals(listOf(gamma, beta, alpha), runBlocking { container.workspace.dao.tabs(spaceId).filter { it.id in listOf(alpha, beta, gamma) }.map { it.id } })
    }
    @Test fun dragToFavoritesBackToPinnedAndAnotherSpaceSurvivesRecreation() {
        val favorites = compose.onNodeWithContentDescription("Favorite Gmail")
        drag(tab("Design references"), favorites)
        compose.waitUntil { compose.onAllNodesWithContentDescription("Favorite Design references").fetchSemanticsNodes().isNotEmpty() }
        drag(compose.onNodeWithContentDescription("Favorite Design references"), compose.onNodeWithText("Keep your everyday tabs here"))
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == "Design references" && it.isPinned && it.closedAt == null } } }
        drag(tab("Reading list"), compose.onNodeWithContentDescription("Switch to Work"))
        compose.waitUntil { runBlocking { container.workspace.dao.tab(beta)!!.spaceId == "work" } }
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithContentDescription("Tab Design references").fetchSemanticsNodes().isNotEmpty() }
        saveScreen("arc-sidebar")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Automatic search by region").assertExists()
        saveScreen("regional-search-settings")
    }
    @Test fun hoverActionsAndSecondaryClickDoNotSelectOrDrag() {
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId }
        val reading = tab("Reading list")
        reading.performMouseInput { moveTo(center) }
        compose.onNodeWithContentDescription("Actions for Reading list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Reading list").assertIsDisplayed()
        saveScreen("sidebar-hover-actions")
        reading.performMouseInput { exit() }
        compose.onNodeWithContentDescription("Actions for Reading list").assertDoesNotExist()
        reading.performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
        compose.onNodeWithText("Pin tab").assertIsDisplayed()
        assertEquals(active, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
        assertNull(runBlocking { container.workspace.dao.tab(beta)!!.closedAt })
        saveScreen("sidebar-context-menu")
        compose.onNodeWithText("Pin tab").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.tab(beta)!!.isPinned } }
        compose.onNodeWithContentDescription("Favorite Gmail").performMouseInput {
            moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary)
        }
        compose.onNodeWithText("Remove favorite").assertIsDisplayed()
    }

    @Test fun customEmojiAndNewTabHomeSurviveRecreation() {
        compose.onNodeWithContentDescription("Space actions").performClick()
        compose.onNodeWithText("Edit Space").performClick()
        compose.onNodeWithText("Emoji or custom icon").performTextReplacement("🧑‍💻")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon == "🧑‍💻" } }
        compose.onNode(hasText("New tab") and hasContentDescription("Tab New tab").not()).performClick()
        compose.onAllNodesWithContentDescription("New tab home", useUnmergedTree = true).onFirst().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil { compose.onAllNodesWithText("🧑‍💻").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("New tab home", useUnmergedTree = true).onFirst().assertIsDisplayed()
        saveScreen("sidebar-custom-emoji-home")
    }

    @Test fun mouseDragsWithoutLongPressAndLongListAutoScrolls() {
        val source = tab("Design references")
        val from = source.fetchSemanticsNode().boundsInRoot
        val target = tab("Project notes").fetchSemanticsNode().boundsInRoot
        source.performMouseInput {
            moveTo(Offset(from.width * 0.45f, from.height / 2f)); press()
            moveTo(Offset(from.width * 0.45f, target.bottom - 5f - from.top)); release()
        }
        compose.waitUntil { runBlocking { container.workspace.dao.tabs(spaceId).last().id == alpha } }
        runBlocking {
            repeat(25) { i ->
                val id = container.tabs.create(spaceId, "https://example.com/$i", false)
                container.tabs.updatePage(id, "https://example.com/$i", "Reference $i", null)
            }
        }
        compose.waitUntil { compose.onAllNodesWithContentDescription("Tab Reference 0").fetchSemanticsNodes().isNotEmpty() }
        val src = tab("Reading list")
        val bounds = src.fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithContentDescription("Sidebar tabs").fetchSemanticsNode().boundsInRoot
        src.performTouchInput {
            down(Offset(bounds.width * 0.45f, bounds.height / 2)); advanceEventTime(650)
            moveTo(Offset(bounds.width * 0.45f, viewport.bottom - 16f - bounds.top), delayMillis = 100)
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Tab Reference 24").fetchSemanticsNodes().isNotEmpty() }
        saveScreen("arc-sidebar-drag")
        compose.onRoot().performTouchInput { cancel() }
        assertEquals(spaceId, runBlocking { container.workspace.dao.tab(beta)!!.spaceId })
    }
}
