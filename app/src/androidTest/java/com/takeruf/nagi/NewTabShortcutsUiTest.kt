package com.takeruf.nagi

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NewTabShortcutsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String
    private lateinit var pinId: String
    private lateinit var left: WebView
    private lateinit var right: WebView
    private lateinit var pin: WebView

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun visible(path: String): WebView? {
        var result: WebView? = null
        main { result = views(compose.activity.window.decorView).firstOrNull {
            it.isShown && it.width > 0 && it.url == "${server.origin}/$path"
        } }
        return result
    }
    private fun js(web: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Before fun setup() {
        server = FixtureServer(pages = listOf("left", "right", "pin").associate { name ->
            "/resume-$name" to """
                <!doctype html><title>Resume $name</title>
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <h1>Resume $name</h1><textarea id="draft">Initial value</textarea>
                <script>window.liveToken='resume-$name';</script>
            """.trimIndent()
        })
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            val dark = InstrumentationRegistry.getArguments().getString("resumeTheme") == "dark"
            container.settings.update { BrowserSettings(automaticSearchRegion = false,
                theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.spaces.create("Resume QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/resume-left", select = false)
            rightId = container.tabs.create(spaceId, "${server.origin}/resume-right", select = false)
            container.tabs.pair(leftId, rightId)
            pinId = container.tabs.create(spaceId, "${server.origin}/resume-pin")
            container.tabs.togglePin(pinId)
        }
        compose.waitUntil(15_000) { visible("resume-pin") != null }
        pin = visible("resume-pin")!!
        compose.waitUntil(15_000) { js(pin, "document.readyState") == "\"complete\"" }
        js(pin, "document.getElementById('draft').value='Unsent pin 日本語';true")
        runBlocking { container.tabs.select(leftId) }
        compose.waitUntil(15_000) { visible("resume-left") != null && visible("resume-right") != null }
        left = visible("resume-left")!!
        right = visible("resume-right")!!
        compose.waitUntil(15_000) {
            js(left, "document.readyState") == "\"complete\"" && js(right, "document.readyState") == "\"complete\""
        }
        js(left, "document.getElementById('draft').value='Unsent left 日本語';true")
        js(right, "document.getElementById('draft').value='Unsent right 日本語';true")
        runBlocking { container.tabs.create(spaceId) }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("new-tab-shortcuts").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("new-tab-shortcuts").assertIsDisplayed()
        compose.onNodeWithTag("new-tab-shortcut-$pinId").assertExists()
        compose.onNodeWithTag("new-tab-shortcut-$leftId").assertExists()
        compose.onNodeWithTag("new-tab-shortcut-$rightId").assertDoesNotExist()
    }

    @After fun cleanup() {
        if (::spaceId.isInitialized) runBlocking { container.spaces.delete(spaceId) }
        if (::server.isInitialized) server.close()
    }

    @Test fun pinnedShortcutReturnsToSameLiveDocument() {
        capture("resume-home.png")
        compose.onNodeWithTag("new-tab-shortcut-$pinId").performClick()
        compose.waitUntil(15_000) { visible("resume-pin") != null }
        assertSame(pin, visible("resume-pin"))
        assertEquals("\"Unsent pin 日本語\"", js(pin, "document.getElementById('draft').value"))
        assertEquals("\"resume-pin\"", js(pin, "window.liveToken"))
        assertEquals(1, server.requests.count { it.path == "/resume-pin" })
    }

    @Test fun splitShortcutRestoresBothLiveDocumentsAndDrafts() {
        compose.onNodeWithTag("new-tab-shortcut-$leftId").performClick()
        compose.waitUntil(15_000) { visible("resume-left") != null && visible("resume-right") != null }
        assertSame(left, visible("resume-left"))
        assertSame(right, visible("resume-right"))
        for ((name, web) in listOf("left" to left, "right" to right)) {
            assertEquals("\"Unsent $name 日本語\"", js(web, "document.getElementById('draft').value"))
            assertEquals("\"resume-$name\"", js(web, "window.liveToken"))
            assertEquals(1, server.requests.count { it.path == "/resume-$name" })
        }
        assertEquals(rightId, runBlocking { container.workspace.dao.tab(leftId)!!.splitRightTabId })
        capture("resume-split.png")
    }
}
