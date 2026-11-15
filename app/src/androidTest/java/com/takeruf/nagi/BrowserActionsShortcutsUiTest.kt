package com.takeruf.nagi

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

/** Inject into the focused Android window, including the Omnibox's Dialog window. */
class BrowserActionsShortcutsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var web: WebView
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(15_000, condition)
    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun scale(): Float {
        var value = 0f
        @Suppress("DEPRECATION")
        main { value = web.scale }
        return value
    }
    private fun shortcut(code: Int, modifiers: Int = KeyEvent.META_CTRL_ON) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(now, now, action, code, 0, modifiers,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        instrumentation.waitForIdleSync()
    }
    private fun resumedPrivate(): PrivateActivity? {
        var value: PrivateActivity? = null
        main { value = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<PrivateActivity>().firstOrNull() }
        return value
    }

    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/keyboard-page" to """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1,minimum-scale=.25,maximum-scale=5">
            <title>Keyboard fixture</title><body><h1>Keyboard fixture</h1><input id="draft">
            <div style="width:100px;height:100px;background:#426951"></div><script>window.marker='same-document';</script></body>
        """.trimIndent()))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false) }
            container.spaces.create("Keyboard actions QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            container.tabs.create(spaceId, server.origin + "/keyboard-page")
        }
        waitFor {
            main { views(compose.activity.window.decorView).firstOrNull { it.url == server.origin + "/keyboard-page" }?.let { web = it } }
            ::web.isInitialized && js("document.readyState") == "\"complete\""
        }
        js("document.getElementById('draft').value='unsent 日本語';true")
    }
    @After fun cleanup() {
        resumedPrivate()?.let { activity -> main { activity.closePrivateBrowsing() } }
        if (::spaceId.isInitialized) runBlocking {
            container.spaces.delete(spaceId)
            container.settings.update { originalSettings }
        }
        if (::server.isInitialized) server.close()
    }

    @Test fun historyAndSettingsShortcutsWorkThroughTheFrontOmniboxWindow() {
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNodeWithTag("command-input").performTextReplacement("unfinished 日本語")
        shortcut(KeyEvent.KEYCODE_H)
        waitFor { compose.onAllNodesWithText(compose.activity.getString(R.string.ui_search_history)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("command-input").assertDoesNotExist()
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.onNodeWithTag("settings-screen").assertExists()
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNodeWithTag("command-input").assertExists()
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.onNodeWithTag("command-input").assertDoesNotExist()
        compose.onNodeWithTag("settings-screen").assertExists()
        compose.returnToBrowserFromSettings()
        assertEquals("\"unsent 日本語\"", js("document.getElementById('draft').value"))
        assertEquals("\"same-document\"", js("window.marker"))
    }

    @Test fun pageZoomKeysPreserveDraftAndDoNotZoomBehindSettingsOrOmnibox() {
        val initial = scale()
        shortcut(KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        waitFor { scale() > initial * 1.02f }
        val enlarged = scale()
        shortcut(KeyEvent.KEYCODE_COMMA)
        compose.onNodeWithTag("settings-screen").assertExists()
        shortcut(KeyEvent.KEYCODE_PLUS)
        assertEquals(enlarged, scale(), 0.02f)
        compose.returnToBrowserFromSettings()
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNodeWithTag("command-input").performTextReplacement("unfinished")
        shortcut(KeyEvent.KEYCODE_MINUS)
        assertEquals(enlarged, scale(), 0.02f)
        compose.onNodeWithTag("command-input").assertTextEquals("unfinished")
        shortcut(KeyEvent.KEYCODE_ESCAPE, 0)
        waitFor {
            var focused = false
            main { focused = compose.activity.window.decorView.hasWindowFocus() }
            focused && compose.onAllNodesWithTag("command-input").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        shortcut(KeyEvent.KEYCODE_0)
        waitFor { kotlin.math.abs(scale() - initial) < 0.03f }
        assertEquals("\"unsent 日本語\"", js("document.getElementById('draft').value"))
        assertEquals("\"same-document\"", js("window.marker"))
        assertEquals(1, server.requests.count { it.path == "/keyboard-page" })
    }

    @Test fun privateShortcutUsesExistingPrivateActivityAndDoesNotNestIt() {
        var supported = false
        main { supported = PrivateProfiles.supported() }
        Assume.assumeTrue("The installed WebView does not support private profile isolation", supported)
        shortcut(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        waitFor { resumedPrivate() != null }
        val activity = resumedPrivate()!!
        shortcut(KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        assertSame(activity, resumedPrivate())
        main { activity.closePrivateBrowsing() }
        waitFor { resumedPrivate() == null }
        assertEquals("\"unsent 日本語\"", js("document.getElementById('draft').value"))
    }
}
