package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** A drag preview and newly exposed drop sections must not resize the compact sheet. */
@OptIn(ExperimentalTestApi::class)
class CompactTabDragUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var firstId: String
    private lateinit var secondId: String
    private lateinit var server: FixtureServer

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Compact drag QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            val blank = container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!!
            firstId = container.tabs.create(spaceId, "${server.origin}/one", select = false)
            secondId = container.tabs.create(spaceId, "${server.origin}/two")
            container.tabs.updatePage(firstId, "${server.origin}/one", "Fixture One", null)
            container.tabs.close(blank)
        }
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(411 * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - 411) < 10 }
        compose.waitUntil(15_000) { server.requests.any { it.path == "/two" } }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "compact-tab-drag-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun assertSheetBounds(expected: Rect) {
        val actual = compose.onNodeWithTag("compact-tab-sheet").fetchSemanticsNode().boundsInRoot
        assertEquals("A held tab must not move the sheet's top", expected.top, actual.top, 2f)
        assertEquals("A held tab must not move the sheet's bottom", expected.bottom, actual.bottom, 2f)
        assertEquals(expected.height, actual.height, 2f)
    }

    private fun order() = runBlocking {
        container.workspace.dao.tabs(spaceId).filter { it.closedAt == null && it.archivedAt == null }
            .sortedBy { it.position }.map { it.id }
    }

    @Test fun topToolbarLightDragKeepsSheetAndPointerAligned() = exercise(bottom = false, ThemeMode.LIGHT)
    @Test fun bottomToolbarDarkDragKeepsSheetAndPointerAligned() = exercise(bottom = true, ThemeMode.DARK)

    private fun exercise(bottom: Boolean, theme: ThemeMode) {
        runBlocking { container.settings.update { it.copy(bottomSearchBar = bottom, theme = theme) } }
        compose.onNodeWithTag("compact-tabs-button").performClick()
        val sheet = compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithTag("sidebar-tab-$firstId")
        val original = source.fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("sidebar-tab-$secondId").fetchSemanticsNode().boundsInRoot
        val grab = Offset(original.width * 0.55f, original.height / 2f)
        val shift = Offset(0f, -6f)
        val originalOrder = order()
        assertEquals(listOf(firstId, secondId), originalOrder)
        capture(if (bottom) "bottom-before" else "top-before")

        source.performTouchInput {
            down(grab); advanceEventTime(650); moveTo(grab + shift, delayMillis = 16)
        }
        val preview = compose.onNodeWithTag("tab-drag-preview").assertIsDisplayed()
        assertSheetBounds(sheet)
        val density = compose.activity.resources.displayMetrics.density
        val previewBounds = preview.fetchSemanticsNode().boundsInRoot
        assertEquals(original.left + shift.x, previewBounds.left, 2f)
        assertEquals(original.top + shift.y - 12 * density, previewBounds.top, 2f)
        capture(if (bottom) "bottom-held" else "top-held")

        // Active sidebar drags consume motion before the enclosing sheet can swipe down.
        // Keep the pointer over the same horizontal grab point. Otherwise moving
        // from 55% of a full-width row to its center puts the preview off-screen,
        // and boundsInRoot reports the screen-clipped rectangle rather than its offset.
        val end = Offset(grab.x, second.bottom - original.top - 5f)
        source.performTouchInput { moveTo(end, delayMillis = 120) }
        assertSheetBounds(sheet)
        val moved = preview.fetchSemanticsNode().boundsInRoot
        assertEquals(original.left + end.x - grab.x, moved.left, 2f)
        assertEquals(original.top + end.y - grab.y - 12 * density, moved.top, 2f)
        source.performTouchInput { cancel() }
        compose.onNodeWithTag("tab-drag-preview").assertDoesNotExist()
        assertSheetBounds(sheet)
        assertEquals("Cancel must preserve order", originalOrder, order())

        // Releasing over the same target reorders only the intended pair of tabs.
        source.performTouchInput {
            down(grab); advanceEventTime(650)
            for (i in 1..8) moveTo(grab + (end - grab) * (i / 8f), delayMillis = 20)
            up()
        }
        compose.waitUntil(10_000) { order() == listOf(secondId, firstId) }
        compose.onNodeWithTag("tab-drag-preview").assertDoesNotExist()
        compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
        assertSheetBounds(sheet)
        capture(if (bottom) "bottom-reordered" else "top-reordered")
    }
}
