package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Exercise the actual Reload controls while the first document has not responded yet. */
class ReloadButtonUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        container = (compose.activity.application as NagiApplication).container
        server = FixtureServer(pages = mapOf("/slow-reload-button" to
            "<!doctype html><title>Reload button fixture</title><meta name='viewport' content='width=device-width'><h1>Reloaded page</h1><script>window.reloadStamp=String(Date.now())</script>"),
            pageDelaysMs = mapOf("/slow-reload-button" to 5_000L))
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
        }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    @Test fun compactBottomMenuReloadKeepsPendingPageAndSelectedTab() = exercise(411, true, ThemeMode.DARK)
    @Test fun wideToolbarReloadKeepsPendingPageAndSelectedTab() = exercise(1000, false, ThemeMode.LIGHT)

    private fun exercise(width: Int, bottom: Boolean, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        val target = "${server.origin}/slow-reload-button"
        val id = runBlocking {
            container.settings.update { BrowserSettings(automaticSearchRegion = false, bottomSearchBar = bottom, theme = theme) }
            container.spaces.create("Reload button QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.tabs.create(spaceId, target)
        }
        compose.waitUntil(15_000) { server.requests.any { it.path == "/slow-reload-button" } }
        val before = runBlocking { container.workspace.dao.tabs(spaceId).map { it.id } }
        fun reload() {
            if (width < 600) {
                compose.onNodeWithTag("page-menu-$id").performClick()
                compose.onNodeWithText(compose.activity.getString(R.string.ui_reload)).performScrollTo().performClick()
            } else {
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_reload)).performClick()
            }
        }
        fun stamp(): String? {
            fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
                (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
            val result = CompletableDeferred<String?>()
            instrumentation.runOnMainSync {
                val web = views(compose.activity.window.decorView).filterIsInstance<WebView>().firstOrNull { it.url == target }
                if (web == null) result.complete(null)
                else web.evaluateJavascript("window.reloadStamp || null") { result.complete(it.takeUnless { it == "null" }) }
            }
            return runBlocking { withTimeout(5_000) { result.await() } }
        }
        reload()
        compose.onNodeWithTag("new-tab-page").assertDoesNotExist()
        compose.waitUntil(20_000) {
            runBlocking { container.workspace.dao.tab(id)?.let { it.url == target && it.title == "Reload button fixture" } == true }
        }
        compose.onNodeWithTag("new-tab-page").assertDoesNotExist()
        assertEquals(id, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
        assertEquals(before, runBlocking { container.workspace.dao.tabs(spaceId).map { it.id } })
        compose.waitUntil(5_000) { stamp() != null }
        val firstStamp = stamp()

        // Once committed, Reload still refreshes the same document rather than opening a new tab.
        val count = server.requests.count { it.path == "/slow-reload-button" }
        reload()
        compose.waitUntil(15_000) { server.requests.count { it.path == "/slow-reload-button" } > count }
        compose.waitUntil(20_000) { stamp()?.let { it != firstStamp } == true }
        compose.onNodeWithTag("new-tab-page").assertDoesNotExist()
        assertEquals(target, runBlocking { container.workspace.dao.tab(id)?.url })
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "reload-button-$width.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
