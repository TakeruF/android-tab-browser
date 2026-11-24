package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale

/** Real app overlays follow toolbar placement without replacing the live page or request. */
class BottomBrowserNoticeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var tabId: String
    private lateinit var server: FixtureServer
    private lateinit var web: WebView
    private val pageUrl get() = "${server.origin}/notice"
    private val density get() = compose.activity.resources.displayMetrics.density

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun js(script: String): String {
        val answer = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { answer.complete(it) } }
        return runBlocking { withTimeout(5_000) { answer.await() } }
    }
    private fun resize(widthDp: Int) {
        shell("wm size ${(widthDp * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - widthDp) < 10 }
        compose.waitForIdle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer(pages = mapOf("/notice" to """
            <!doctype html><title>Notice placement fixture</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <body style="font:20px sans-serif;padding:16px">
            <input id="draft" value="Unsent 日本語"><p><a id="external" href="nagiqa://open/notice">Open test app</a></p>
            <script>window.noticeToken='same document';</script></body>
        """.trimIndent()))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false,
                bottomSearchBar = true, blockExternalApps = true, translationTargetLanguage = "en", theme = ThemeMode.LIGHT) }
            container.spaces.create("Notice placement QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.tabs.create(spaceId, pageUrl)
        }
        resize(411)
        compose.waitUntil(15_000) {
            main { views(compose.activity.window.decorView).filterIsInstance<WebView>()
                .firstOrNull { it.isShown && it.url == pageUrl }?.let { web = it } }
            ::web.isInitialized && js("document.readyState") == "\"complete\""
        }
    }

    @After fun cleanup() {
        if (::container.isInitialized) runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(250)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "bottom-notice-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun tapElement(id: String) {
        js("document.getElementById('$id').scrollIntoView({block:'center'});true")
        val drawn = CompletableDeferred<Unit>()
        main {
            web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { drawn.complete(Unit) }
            })
            web.invalidate()
        }
        compose.waitUntil(5_000) { drawn.isCompleted }
        val rect = JSONObject(JSONTokener(js("""(()=>{const r=document.getElementById('$id').getBoundingClientRect(),v=visualViewport;
            return JSON.stringify({x:r.x+r.width/2-(v?.offsetLeft||0),y:r.y+r.height/2-(v?.offsetTop||0),w:v?.width||innerWidth})})()""")).nextValue() as String)
        main {
            val scale = web.width / rect.getDouble("w")
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN,
                (rect.getDouble("x") * scale).toFloat(), (rect.getDouble("y") * scale).toFloat(), 0)
            val up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, down.x, down.y, 0)
            web.dispatchTouchEvent(down); web.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
    }

    private fun assertBottom(tag: String) {
        val popup = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val toolbar = compose.onNodeWithTag("browser-address-bar-$tabId").fetchSemanticsNode().boundsInWindow
        assertEquals("Notice should sit 16 dp above the bottom toolbar", toolbar.top - 16 * density, popup.bottom, 2f)
        assertTrue("Notice must remain inside the page width", popup.left >= 0 && popup.right <= compose.activity.window.decorView.width)
    }

    private fun assertTop(tag: String) {
        val popup = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val toolbar = compose.onNodeWithTag("browser-address-bar-$tabId").fetchSemanticsNode().boundsInWindow
        assertTrue("Notice should follow the top address bar", popup.top >= toolbar.bottom && popup.top < toolbar.bottom + 24 * density)
    }

    private fun assertPageRetained() {
        main { assertSame(web, views(compose.activity.window.decorView).filterIsInstance<WebView>().first { it.isShown && it.url == pageUrl }) }
        assertEquals("\"same document\"", js("window.noticeToken"))
        assertEquals("\"Unsent 日本語\"", js("document.getElementById('draft').value"))
        assertEquals(1, server.requests.count { it.path == "/notice" })
    }

    @Test fun externalRequestTracksBottomToolbarAndClearsTheRealKeyboard() {
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK) } }
        tapElement("external")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("external-app-notice").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("nagiqa://open/notice").assertIsDisplayed()
        assertBottom("external-app-notice")
        capture("external-bottom-dark")
        tapElement("draft")
        compose.waitUntil(10_000) {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            val keyboardTop = compose.activity.window.decorView.height - (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == true &&
                kotlin.math.abs(compose.onNodeWithTag("external-app-notice").fetchSemanticsNode().boundsInWindow.bottom - (keyboardTop - 16 * density)) < 3
        }
        val popup = compose.onNodeWithTag("external-app-notice").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val keyboardTop = compose.activity.window.decorView.height -
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertEquals("Notice should remain 16 dp above the IME", keyboardTop - 16 * density, popup.bottom, 3f)
        capture("external-keyboard-dark")
        main { compose.activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(web.windowToken, 0) }
        compose.waitUntil(10_000) {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) != true && (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0) == 0
        }
        compose.waitForIdle()
        assertBottom("external-app-notice")
        runBlocking { container.settings.update { it.copy(bottomSearchBar = false) } }
        assertTop("external-app-notice")
        capture("external-top-dark")
        compose.onNodeWithText("nagiqa://open/notice").assertIsDisplayed()
        assertPageRetained()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_dismiss_external_app_notice)).performClick()
        compose.onNodeWithTag("external-app-notice").assertDoesNotExist()
    }

    @Test fun translationSelectionSurvivesBottomTopAndWideSplitPlacement() {
        compose.onNodeWithTag("page-menu-$tabId").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("translate-page").fetchSemanticsNodes().firstOrNull()?.config
                ?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) == false
        }
        compose.onNodeWithTag("translate-page").performScrollTo().performClick()
        assertBottom("translation-notice")
        compose.onNodeWithTag("translation-target").performClick()
        compose.onNodeWithTag("translation-target-ja").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().translationTargetLanguage == "ja" } }
        capture("translation-bottom-light")
        runBlocking { container.settings.update { it.copy(bottomSearchBar = false, theme = ThemeMode.DARK) } }
        assertTop("translation-notice")
        capture("translation-top-dark")
        runBlocking { container.settings.update { it.copy(bottomSearchBar = true) } }
        assertBottom("translation-notice")
        resize(1000)
        assertTop("translation-notice")
        val rightId = runBlocking {
            container.tabs.create(spaceId, "${server.origin}/two", select = false).also { container.tabs.pair(tabId, it) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("browser-pane-right-$rightId").fetchSemanticsNodes().isNotEmpty() }
        assertTop("translation-notice")
        val locale = compose.activity.resources.configuration.locales[0]
        compose.onNodeWithTag("translation-target").assertTextContains(Locale.forLanguageTag("ja").getDisplayLanguage(locale))
        capture("translation-wide-split-dark")
        assertPageRetained()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).performClick()
        compose.onNodeWithTag("translation-notice").assertDoesNotExist()
    }
}
