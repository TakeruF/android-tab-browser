package com.takeruf.nagi

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
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
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real page input reaches the common app-owned link/image and upload choice card. */
class BrowserActionsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var originalSettings: BrowserSettings
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var web: WebView
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun views(view: View): List<WebView> = when (view) {
        is WebView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun js(code: String): String {
        val answer = CompletableDeferred<String>()
        main { web.evaluateJavascript(code) { answer.complete(it) } }
        return runBlocking { withTimeout(5_000) { answer.await() } }
    }
    private fun press(id: String, hold: Boolean = false) {
        val point = JSONArray(js("(()=>{const r=document.getElementById('$id').getBoundingClientRect(),v=visualViewport;return [r.x+r.width/2-(v?.offsetLeft||0),r.y+r.height/2-(v?.offsetTop||0),v?.width||innerWidth]})()"))
        var x = 0f; var y = 0f
        val time = SystemClock.uptimeMillis()
        fun event(action: Int) = main {
            val motion = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x, y, 0)
            try { web.dispatchTouchEvent(motion) } finally { motion.recycle() }
        }
        main {
            x = (point.getDouble(0) * web.width / point.getDouble(2)).toFloat()
            y = (point.getDouble(1) * web.width / point.getDouble(2)).toFloat()
        }
        event(MotionEvent.ACTION_DOWN)
        if (hold) {
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("browser-actions-dialog").fetchSemanticsNodes().isNotEmpty() }
            event(MotionEvent.ACTION_CANCEL)
        } else {
            SystemClock.sleep(60)
            event(MotionEvent.ACTION_UP)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("browser-actions-dialog").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val image = instrumentation.uiAutomation.takeScreenshot()
        try { File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        } } finally { image.recycle() }
    }
    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/actions" to """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Browser actions fixture</title><body style="padding:48px;font:20px sans-serif">
            <a id="link" style="display:block;padding:20px" href="/two">Open second page</a>
            <input id="upload" type="file" accept="image/*" style="display:block;margin:24px 0">
            <textarea id="draft">Keep my draft</textarea></body>"""))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            originalSettings = container.settings.settings.first()
            val dark = InstrumentationRegistry.getArguments().getString("actionsTheme") == "dark"
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false, nativePageDrag = false,
                theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.spaces.create("Actions QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            val initial = container.workspace.dao.tabs(spaceId).single { it.closedAt == null }
            container.tabs.create(spaceId, server.origin + "/actions")
            container.tabs.close(initial.id)
        }
        compose.waitUntil(15_000) {
            var result: WebView? = null
            main { result = views(compose.activity.window.decorView).firstOrNull { it.isShown && it.url == server.origin + "/actions" && it.progress == 100 } }
            result?.let { web = it }
            result != null
        }
        val ready = CompletableDeferred<Unit>()
        main { web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) { ready.complete(Unit) }
        }) }
        runBlocking { withTimeout(5_000) { ready.await() } }
        SystemClock.sleep(100)
    }
    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::originalSettings.isInitialized) container.settings.update { originalSettings }
        }
        if (::server.isInitialized) server.close()
    }
    @Test fun uploadChoicesUseTheCommonCardAndCancellationKeepsTheDraft() {
        press("upload")
        compose.onNodeWithText(compose.activity.getString(R.string.ui_take_photo)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_choose_files)).assertIsDisplayed()
        capture("upload-choices.png")
        compose.onNodeWithTag("browser-actions-cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("browser-actions-dialog").fetchSemanticsNodes().isEmpty() }
        assertEquals("0", js("document.getElementById('upload').files.length"))
        assertEquals("\"Keep my draft\"", js("document.getElementById('draft').value"))
    }
    @Test fun linkActionsUseTheCommonCardAndCopyTheOriginalDestination() {
        press("link", hold = true)
        compose.onNodeWithText(compose.activity.getString(R.string.ui_copy_link)).assertIsDisplayed()
        capture("link-actions.png")
        compose.onNodeWithText(compose.activity.getString(R.string.ui_copy_link)).performClick()
        var copied = ""
        main { copied = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString() }
        assertEquals(server.origin + "/two", copied)
        assertEquals("\"Keep my draft\"", js("document.getElementById('draft').value"))
    }
}
