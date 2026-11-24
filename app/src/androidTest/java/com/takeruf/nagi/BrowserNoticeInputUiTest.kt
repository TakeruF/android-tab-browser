package com.takeruf.nagi

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.browser.NativeSurface
import com.takeruf.nagi.ui.browser.PageTranslationNotice
import com.takeruf.nagi.ui.theme.NagiTheme
import com.takeruf.nagi.browser.translation.PageTranslationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

/** Raw window events are essential: semantics clicks bypass Compose/native hit testing. */
class BrowserNoticeInputUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var spaceId: String
    private lateinit var tabId: String
    private lateinit var web: WebView
    private val density get() = compose.activity.resources.displayMetrics.density
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun views(view: View): List<WebView> = when(view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun js(code: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { web.evaluateJavascript(code) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer(pages = mapOf("/trap" to """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Popup click trap</title><body style="margin:0;height:4000px">
            <a id="trap" href="/unwanted" style="position:fixed;inset:0;background:#cce5e0">Page link behind notice</a>
            <script>window.trapClicks=0;window.trapDowns=0;
            document.addEventListener('click',e=>{window.trapClicks++;e.preventDefault()});
            document.addEventListener('pointerdown',()=>window.trapDowns++);</script>
        """.trimIndent()))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, theme = ThemeMode.DARK) }
            container.spaces.create("Popup input QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.tabs.create(spaceId, server.origin + "/trap")
        }
        compose.waitUntil(15_000) {
            compose.runOnIdle { views(compose.activity.window.decorView).firstOrNull { it.isShown && it.url == server.origin + "/trap" }?.let { web = it } }
            ::web.isInitialized && js("document.readyState") == "\"complete\""
        }
    }
    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            container.workspace.dao.observeHistory().first().filter { it.url.startsWith(server.origin) }.forEach { container.library.deleteHistory(it.id) }
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        shell("wm size $originalSize")
        server.close()
    }

    private fun click(point: Offset, mouse: Boolean, releaseAfterUp: Boolean = false) {
        val time = SystemClock.uptimeMillis()
        fun send(action: Int, buttons: Int = 0) = compose.runOnIdle {
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = if (mouse) MotionEvent.TOOL_TYPE_MOUSE else MotionEvent.TOOL_TYPE_FINGER }
            val coordinates = MotionEvent.PointerCoords().apply { x = point.x; y = point.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 1f }
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coordinates),
                0, buttons, 1f, 1f, 0, 0, if (mouse) InputDevice.SOURCE_MOUSE else InputDevice.SOURCE_TOUCHSCREEN, 0)
            try {
                if (action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE)
                    MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType).invoke(event, MotionEvent.BUTTON_PRIMARY)
                if (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP, MotionEvent.ACTION_MOVE)) compose.activity.window.callback.dispatchTouchEvent(event)
                else compose.activity.window.callback.dispatchGenericMotionEvent(event)
            } finally { event.recycle() }
        }
        if (mouse) { send(MotionEvent.ACTION_HOVER_ENTER); send(MotionEvent.ACTION_HOVER_MOVE) }
        send(MotionEvent.ACTION_DOWN, if (mouse) MotionEvent.BUTTON_PRIMARY else 0)
        if (mouse) {
            send(MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.BUTTON_PRIMARY)
            if (!releaseAfterUp) send(MotionEvent.ACTION_BUTTON_RELEASE)
        }
        send(MotionEvent.ACTION_UP)
        if (mouse && releaseAfterUp) send(MotionEvent.ACTION_BUTTON_RELEASE)
        compose.waitForIdle()
    }

    @Test fun tabletMouseClickOnNoticeDoesNotActivateUnderlyingLink() = exercise(1000, true)
    @Test fun tabletMouseLateButtonReleaseCannotClickThroughDismissedNotice() = exercise(1000, true, true)
    @Test fun phoneTouchOnBottomNoticeDoesNotActivateUnderlyingLink() = exercise(411, false)

    private fun exercise(width: Int, mouse: Boolean, releaseAfterUp: Boolean = false) {
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(bottomSearchBar = width < 600) } }
        compose.onNodeWithTag("page-menu-$tabId").performClick()
        compose.onNodeWithTag("translate-page").performScrollTo().performClick()
        val notice = compose.onNodeWithTag("translation-notice")
        notice.assertIsDisplayed()
        // A click outside the non-modal notice must still reach the real page exactly once.
        val bounds = compose.onNodeWithTag("browser-content-$tabId").fetchSemanticsNode().boundsInWindow
        click(Offset(bounds.left + 36 * density, bounds.top + bounds.height / 2), mouse, releaseAfterUp)
        assertEquals("Outside-page click should be delivered once", "1", js("window.trapClicks"))
        notice.assertIsDisplayed()
        js("window.trapClicks=0;window.trapDowns=0;true")
        // Its title/padding is inert, but must shield the page beneath it.
        val card = notice.fetchSemanticsNode().boundsInWindow
        click(Offset(card.left + 24 * density, card.top + 20 * density), mouse, releaseAfterUp)
        assertEquals("Notice title leaked a page click", "0", js("window.trapClicks"))
        assertEquals("Notice title leaked a pointer down", "0", js("window.trapDowns"))
        val cancel = compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).fetchSemanticsNode().boundsInWindow.center
        click(cancel, mouse, releaseAfterUp)
        notice.assertDoesNotExist()
        assertEquals("Dismissing notice leaked a page click", "0", js("window.trapClicks"))
        assertEquals("Dismissing notice leaked a pointer down", "0", js("window.trapDowns"))
        click(cancel, mouse, releaseAfterUp)
        assertEquals("The page must receive subsequent clicks normally", "1", js("window.trapClicks"))
    }

    @Test fun mouseTranslateActionRunsOnceWithoutClickingPageAndDisabledButtonShieldsPage() {
        // Use the real notice over a real native WebView, with a recorded translation
        // callback so this input regression does not download ML Kit models.
        var visible by mutableStateOf(true)
        var calls = 0
        var target: String? = null
        lateinit var native: WebView
        compose.runOnIdle {
            native = WebView(compose.activity).apply { settings.javaScriptEnabled = true }
            compose.activity.setContent { NagiTheme(ThemeMode.DARK) {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NativeSurface(native, Modifier.fillMaxSize())
                    if (visible) PageTranslationNotice(PageTranslationState(), onDismiss = { visible = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).width(360.dp),
                        targetLanguage = "ja", onTranslate = { _, language, _, _ ->
                            calls++; target = language; visible = false
                        })
                }
            } }
            web = native
            native.loadUrl(server.origin + "/trap")
        }
        try {
            compose.waitUntil(10_000) { js("document.readyState") == "\"complete\"" }
            compose.onNodeWithTag("translation-source").performClick()
            compose.onNodeWithTag("translation-source-ja").performScrollTo().performClick()
            val start = compose.onNodeWithTag("translation-start").assertIsNotEnabled()
            click(start.fetchSemanticsNode().boundsInWindow.center, true)
            assertEquals(0, calls)
            assertEquals("0", js("window.trapClicks"))
            compose.onNodeWithTag("translation-notice").assertIsDisplayed()
            compose.onNodeWithTag("translation-source").performClick()
            compose.onNodeWithTag("translation-source-auto").performScrollTo().performClick()
            start.assertIsEnabled()
            val point = start.fetchSemanticsNode().boundsInWindow.center
            click(point, true)
            assertEquals(1, calls)
            assertEquals("ja", target)
            compose.onNodeWithTag("translation-notice").assertDoesNotExist()
            assertEquals("0", js("window.trapClicks"))
            click(point, true)
            assertEquals("1", js("window.trapClicks"))
        } finally {
            compose.runOnIdle {
                (native.parent as? ViewGroup)?.removeView(native)
                native.destroy()
            }
        }
    }
}
