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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class PageZoomUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var web: WebView
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/zoom-ui" to """
            <!doctype html><title>Page zoom fixture</title>
            <meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=0.5,maximum-scale=5">
            <body style="margin:20px;font:18px sans-serif"><h1>Page zoom</h1><div style="width:160px;height:100px;background:#244435"></div>
            <textarea id="draft">Keep this draft</textarea><script>window.liveToken='zoom-ui';</script></body>
        """.trimIndent()))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            val dark = InstrumentationRegistry.getArguments().getString("zoomTheme") == "dark"
            container.settings.update { BrowserSettings(automaticSearchRegion = false, theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.spaces.create("Zoom QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.tabs.create(spaceId, "${server.origin}/zoom-ui")
        }
        compose.waitUntil(15_000) {
            main { views(compose.activity.window.decorView).filterIsInstance<WebView>().firstOrNull { it.url == "${server.origin}/zoom-ui" }?.let { web = it } }
            ::web.isInitialized && js("document.readyState") == "\"complete\""
        }
        val drawn = CompletableDeferred<Unit>()
        main { web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { drawn.complete(Unit) }
        }); web.invalidate() }
        compose.waitUntil(5_000) { drawn.isCompleted }
    }
    @After fun cleanup() { if (::spaceId.isInitialized) runBlocking { container.spaces.delete(spaceId) }; if (::server.isInitialized) server.close() }

    @Test fun pageMenuZoomControlsChangeNativeScaleAndResetWithoutReloading() {
        val initial = js("visualViewport.scale").toDouble()
        js("document.getElementById('draft').value='Unsent 日本語';true")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).performClick()
        compose.onNodeWithTag("page-zoom-reset").assertIsNotEnabled()
        compose.onNodeWithTag("page-zoom-in").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { js("visualViewport.scale").toDouble() > initial * 1.1 }
        android.os.SystemClock.sleep(300)
        compose.waitForIdle()
        val expected = java.text.NumberFormat.getPercentInstance().format(js("visualViewport.scale").toDouble() / initial)
        compose.onNodeWithTag("page-zoom-reset").assertTextContains(expected).assertIsEnabled()
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "page-zoom-menu.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        compose.onNodeWithTag("page-zoom-reset").performClick()
        compose.waitUntil(5_000) { kotlin.math.abs(js("visualViewport.scale").toDouble() - initial) < 0.02 }
        compose.onNodeWithTag("page-zoom-reset").assertIsNotEnabled()
        assertEquals("\"Unsent 日本語\"", js("document.getElementById('draft').value"))
        assertEquals("\"zoom-ui\"", js("window.liveToken"))
        assertEquals(1, server.requests.count { it.path == "/zoom-ui" })
    }
}
