package com.takeruf.nagi

import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Real Nagi UI, WebView callbacks, Activity recreation and the stored history together. */
@OptIn(ExperimentalTestApi::class)
class HistoryRegressionUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var spaceId: String
    private lateinit var tabId: String
    private var originalSiteMode: Boolean? = null
    private var naturalPortrait = true
    private val callbacks = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun openOmnibox() = compose.runOnIdle {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
    }
    private fun navigate(url: String) {
        openOmnibox()
        compose.onNode(hasSetTextAction()).performTextReplacement(url)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
    }
    private fun tapLink(id: String) {
        val coordinates = kotlinx.coroutines.CompletableDeferred<org.json.JSONArray>()
        web { it.evaluateJavascript("(()=>{const r=document.getElementById('$id').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth]})()") {
            value -> coordinates.complete(org.json.JSONArray(value))
        } }
        val point = runBlocking { kotlinx.coroutines.withTimeout(5_000) { coordinates.await() } }
        web { view ->
            val scale = view.width / point.getDouble(2)
            val x = (point.getDouble(0) * scale).toFloat()
            val y = (point.getDouble(1) * scale).toFloat()
            val now = android.os.SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
            val up = android.view.MotionEvent.obtain(now, now + 60, android.view.MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
    }

    private fun web(block: (WebView) -> Unit) {
        compose.waitUntil(10_000) {
            var ready = false
            compose.activityRule.scenario.onActivity { activity ->
                activity.findViewById<WebView>(R.id.browser_web_view)?.let { block(it); ready = true }
            }
            ready
        }
    }
    private fun visits() = runBlocking {
        container.workspace.dao.observeHistory().first().filter { it.url.startsWith(server.origin) }
    }
    private fun awaitPage(title: String, count: Int) {
        try {
            compose.waitUntil(15_000) {
                runBlocking { container.workspace.dao.tab(tabId)?.title == title } && visits().size == count
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Expected $title/$count visits, tab=${runBlocking { container.workspace.dao.tab(tabId) }}, visits=${visits()}, callbacks=$callbacks", e)
        }
        compose.waitUntil(10_000) { var loaded = false; web { loaded = it.progress == 100 && it.title == title }; loaded }
        compose.waitForIdle()
    }
    private fun toggle(label: Int) {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_page_menu)).performClick()
        compose.onNodeWithText(compose.activity.getString(label)).performClick()
    }
    private fun rotate(configuration: Int) {
        val portrait = configuration == Configuration.ORIENTATION_PORTRAIT
        val rotation = if (portrait == naturalPortrait) android.app.UiAutomation.ROTATION_FREEZE_0
            else android.app.UiAutomation.ROTATION_FREEZE_90
        assertTrue(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(rotation))
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == configuration }
    }
    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        val preferences = compose.activity.getSharedPreferences("site_display_modes", Context.MODE_PRIVATE)
        originalSiteMode = if (preferences.contains("127.0.0.1")) preferences.getBoolean("127.0.0.1", false) else null
        preferences.edit().remove("127.0.0.1").commit()
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = true) }
            container.spaces.create("History QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!!
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("browser-pane-left-$tabId").fetchSemanticsNodes().isNotEmpty()
        }
        compose.activityRule.scenario.onActivity {
            val sideways = it.display!!.rotation % 2 != 0
            naturalPortrait = (it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) != sideways
        }
        rotate(Configuration.ORIENTATION_PORTRAIT)
    }
    @After fun cleanup() {
        runBlocking {
            visits().forEach { container.library.deleteHistory(it.id) }
            container.spaces.delete(spaceId)
            container.settings.update { originalSettings }
        }
        val edit = compose.activity.getSharedPreferences("site_display_modes", Context.MODE_PRIVATE).edit()
        originalSiteMode?.let { edit.putBoolean("127.0.0.1", it) } ?: edit.remove("127.0.0.1")
        edit.commit()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
        server.close()
    }
    @Test fun displayTogglesRotationReloadAndRepeatedCallbacksKeepOneHistoryRow() {
        navigate("${server.origin}/ua")
        awaitPage("Fixture Desktop", 1)
        val original = visits().single()
        repeat(2) {
            toggle(R.string.ui_use_mobile_site)
            awaitPage("Fixture Mobile", 1)
            rotate(Configuration.ORIENTATION_LANDSCAPE)
            awaitPage("Fixture Mobile", 1)
            // Simulate repeated WebView completion notifications for the visible document.
            web { view ->
                view.webViewClient.onPageFinished(view, "${server.origin}/stale")
                repeat(3) { view.webViewClient.onPageFinished(view, view.url!!) }
            }
            toggle(R.string.ui_use_desktop_site)
            awaitPage("Fixture Desktop", 1)
            rotate(Configuration.ORIENTATION_PORTRAIT)
            awaitPage("Fixture Desktop", 1)
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_reload)).performClick()
        awaitPage("Fixture Desktop", 1)
        compose.activityRule.scenario.recreate()
        awaitPage("Fixture Desktop", 1)
        assertEquals(original.id, visits().single().id)
        assertEquals(original.visitedAt, visits().single().visitedAt)
        openOmnibox()
        compose.onNode(hasSetTextAction()).performTextReplacement(">history")
        compose.onNodeWithText(compose.activity.getString(R.string.ui_open_history)).performClick()
        compose.onAllNodesWithTag("library-row:${server.origin}/ua").assertCountEquals(1)
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.cacheDir, "history-regression.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
    @Test fun linkNavigationAndBackKeepActualRepeatVisits() {
        navigate("${server.origin}/one")
        awaitPage("Fixture One", 1)
        web { view ->
            val client = view.webViewClient
            view.webViewClient = object : android.webkit.WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) =
                    client.shouldOverrideUrlLoading(view, request)
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    callbacks += "start:$url"
                    client.onPageStarted(view, url, favicon)
                }
                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    callbacks += "history:$url reload=$isReload"
                    client.doUpdateVisitedHistory(view, url, isReload)
                }
                override fun onPageFinished(view: WebView, url: String) {
                    callbacks += "finish:$url visible=${view.url} title=${view.title}"
                    client.onPageFinished(view, url)
                }
            }
        }
        tapLink("next")
        awaitPage("Fixture Two", 2)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back)).performClick()
        awaitPage("Fixture One", 3)
        assertEquals(2, visits().count { it.url == "${server.origin}/one" })
        assertEquals(1, visits().count { it.url == "${server.origin}/two" })
    }
}
