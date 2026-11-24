package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Visits originate from real WebViews; switching the selected Space scopes the open history route. */
class SpaceHistoryUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private val createdSpaces = mutableListOf<String>()

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        server = FixtureServer(pages = mapOf(
            "/space-history-a" to "<!doctype html><title>History Space A page</title><p>Only A</p>",
            "/space-history-b" to "<!doctype html><title>History Space B page</title><p>Only B</p>"))
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
        }
    }

    @After fun cleanup() {
        runBlocking {
            createdSpaces.forEach { container.spaces.delete(it) }
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    private fun visitNewSpace(name: String, path: String): String {
        val spaceId = runBlocking {
            container.spaces.create(name)
            container.settings.settings.first().selectedSpaceId.also {
                createdSpaces += it
                container.tabs.create(it, server.origin + path)
            }
        }
        compose.waitUntil(20_000) { runBlocking {
            container.workspace.dao.observeHistoryForSpace(spaceId).first().any { it.url == server.origin + path }
        } }
        return spaceId
    }

    private fun expectHistory(spaceName: String, visiblePath: String, hiddenPath: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("history-space-name") and hasText(spaceName))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1 &&
                compose.onAllNodesWithTag("library-row:${server.origin}$visiblePath")
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).size == 1
        }
        compose.onNodeWithTag("library-row:${server.origin}$visiblePath").assertIsDisplayed()
        compose.onNodeWithTag("library-row:${server.origin}$hiddenPath").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "space-history-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun phoneLightHistoryIsScopedThroughSwitchDeleteAndRecreation() = exercise(411, ThemeMode.LIGHT)
    @Test fun tabletDarkHistoryIsScopedThroughSwitchDeleteAndRecreation() = exercise(1000, ThemeMode.DARK)

    private fun exercise(width: Int, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(theme = theme) } }
        val a = visitNewSpace("History Space A", "/space-history-a")
        val b = visitNewSpace("History Space B", "/space-history-b")
        runBlocking { container.spaces.select(a) }
        if (compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.ui_history)).fetchSemanticsNodes().isEmpty())
            compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_history)).performClick()
        expectHistory("History Space A", "/space-history-a", "/space-history-b")
        capture("$width-a")
        compose.onNode(hasSetTextAction()).performTextReplacement("space-history")
        runBlocking { container.spaces.select(b) }
        expectHistory("History Space B", "/space-history-b", "/space-history-a")
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        capture("$width-b")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_remove_1_s, "History Space B page")).performClick()
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.observeHistoryForSpace(b).first().isEmpty() } }
        assertEquals(1, runBlocking { container.workspace.dao.observeHistoryForSpace(a).first().size })
        compose.onNodeWithTag("library-row:${server.origin}/space-history-a").assertDoesNotExist()
        runBlocking { container.spaces.select(a) }
        expectHistory("History Space A", "/space-history-a", "/space-history-b")
        compose.activityRule.scenario.recreate()
        expectHistory("History Space A", "/space-history-a", "/space-history-b")
        runBlocking { container.settings.update { it.copy(spacesEnabled = false) } }
        compose.onNodeWithTag("history-space-name").assertDoesNotExist()
        compose.onNodeWithTag("library-row:${server.origin}/space-history-a").assertIsDisplayed()
        compose.onNodeWithTag("library-row:${server.origin}/space-history-b").assertDoesNotExist()
    }
}
