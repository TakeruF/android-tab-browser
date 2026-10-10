package com.takeruf.nagi

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class VideoUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var web: WebView
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(predicate: () -> Boolean) = runBlocking {
        withTimeout(15_000) { while (!predicate()) delay(100) }
    }
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun shortcut(key: Int) = main {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0, KeyEvent.META_CTRL_ON))
    }
    private fun open(path: String = "/video") {
        shortcut(KeyEvent.KEYCODE_L)
        compose.onNode(hasSetTextAction()).performTextReplacement(server.origin + path)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitFor { runBlocking { container.workspace.dao.observeTabs().first().any { it.url == server.origin + path && it.title.startsWith("Video") } } }
        main { web = views(compose.activity.window.decorView).filterIsInstance<WebView>().first { it.url == server.origin + path } }
        waitFor { js("document.readyState") == "\"complete\"" }
    }
    private fun tap(selector: String, inFrame: Boolean = false) {
        val script = if (inFrame) """
            (()=>{const r=document.querySelector('#player-frame').getBoundingClientRect();
            return JSON.stringify({x:r.x+70,y:r.y+385,w:innerWidth});})()
        """ else """
            (()=>{const r=document.querySelector(${JSONObject.quote(selector)}).getBoundingClientRect();
            return JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth});})()
        """
        val rect = JSONObject(JSONTokener(js(script)).nextValue() as String)
        tapAt(rect.getDouble("x"), rect.getDouble("y"), rect.getDouble("w"))
    }
    private fun tapAt(x: Double, y: Double, viewport: Double) = main {
        val scale = web.width / viewport
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, (x * scale).toFloat(), (y * scale).toFloat(), 0)
        val up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, (x * scale).toFloat(), (y * scale).toFloat(), 0)
        web.dispatchTouchEvent(down); web.dispatchTouchEvent(up); down.recycle(); up.recycle()
    }
    private fun ticks() = js("window.videoTicks").toIntOrNull() ?: 0
    private fun assertAdvances() { val before = ticks(); waitFor { ticks() >= before + 3 } }
    private fun home() { instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close() }
    private fun returnToApp() {
        val activity = compose.activity
        // Let the system finish pinning before sending the launcher intent back into the task.
        if (activity.isInPictureInPictureMode) SystemClock.sleep(1000)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n ${instrumentation.targetContext.packageName}/.MainActivity")).use { it.readBytes() }
        waitFor { !activity.isInPictureInPictureMode && activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        instrumentation.waitForIdleSync()
    }
    private fun screenshot(name: String) {
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { it.copy(autoVideoPip = true, videoPopups = true, desktopDefault = false, automaticSearchRegion = false) }
            container.spaces.create("Video QA ${System.nanoTime()}")
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Page menu").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { returnToApp(); server.close() }

    @Test fun fullscreenHomeKeepsPlayingInPipAndRestoresSamePlayer() {
        open(); tap("#start-video"); assertAdvances(); tap("#fullscreen-video")
        compose.onNodeWithTag("video-fullscreen").assertExists()
        waitFor { js("!!document.fullscreenElement") == "true" }; assertAdvances(); SystemClock.sleep(1000)
        screenshot("video-fullscreen")
        home(); waitFor { compose.activity.isInPictureInPictureMode }
        assertAdvances(); SystemClock.sleep(1500); screenshot("video-system-pip")
        fun pipAction() = main {
            android.app.PendingIntent.getBroadcast(compose.activity, 0,
                Intent("${instrumentation.targetContext.packageName}.VIDEO_PLAYBACK").setPackage(instrumentation.targetContext.packageName),
                android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)!!.send()
        }
        pipAction()
        waitFor { js("document.getElementById('player').paused") == "true" }
        SystemClock.sleep(300)
        pipAction()
        assertAdvances()
        returnToApp(); compose.onNodeWithTag("video-fullscreen").assertExists(); assertAdvances()
        compose.onNodeWithText("Exit fullscreen").performClick()
        waitFor { js("!!document.fullscreenElement") == "false" }
    }
    @Test fun popupKeepsPlayingAcrossTabsAndSupportsDragResizePauseExpandAndReturn() {
        open(); tap("#start-video"); assertAdvances()
        waitFor { js("!!document.querySelector('[data-nagi-video-assistant]')") == "true" }
        tap("[data-nagi-video-assistant]")
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        assertAdvances()
        shortcut(KeyEvent.KEYCODE_T)
        compose.onNodeWithText("Esc").performClick()
        compose.onNodeWithTag("video-popup").assertExists(); assertAdvances()
        screenshot("video-popup-other-tab")
        val before = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("video-popup-drag").performTouchInput { swipeUp() }
        val after = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
        assertTrue(after.top < before.top)
        compose.onNodeWithTag("video-popup-resize").performClick()
        val resizedWidth = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("video-popup-play-pause").performClick()
        waitFor { js("document.getElementById('player').paused") == "true" }
        compose.onNodeWithTag("video-popup-play-pause").performClick(); assertAdvances()
        compose.onNodeWithTag("video-popup-expand").performClick()
        compose.onNodeWithTag("video-fullscreen").assertExists(); assertAdvances()
        compose.onNodeWithTag("video-minimize").performClick()
        home(); waitFor { compose.activity.isInPictureInPictureMode }; assertAdvances()
        returnToApp(); compose.onNodeWithTag("video-popup").assertExists()
        assertEquals(resizedWidth, compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.onNodeWithTag("video-popup-return").performClick()
        compose.onNodeWithTag("video-popup").assertDoesNotExist()
        waitFor { js("!!document.fullscreenElement") == "false" }; assertAdvances()
        tap("#fullscreen-video"); compose.onNodeWithTag("video-minimize").performClick()
        compose.onNodeWithTag("video-popup-close").performClick()
        waitFor { js("document.getElementById('player').paused") == "true" }
    }
    @Test fun crossOriginFrameAssistantKeepsPlayingAndClosingSourceTabClosesPopup() {
        open("/video-frame")
        fun tapFrame(name: String) {
            waitFor { js("!!window.frameRects?." + name) == "true" }
            val rect = JSONObject(JSONTokener(js("""
                (()=>{const f=document.querySelector('#player-frame').getBoundingClientRect(),r=window.frameRects.$name;
                return JSON.stringify({x:f.x+r.x,y:f.y+r.y,w:innerWidth});})()
            """)).nextValue() as String)
            tapAt(rect.getDouble("x"), rect.getDouble("y"), rect.getDouble("w"))
        }
        tapFrame("start"); assertAdvances(); tapFrame("assistant")
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        assertAdvances()
        runBlocking { container.spaces.create("Other Video Space ${System.nanoTime()}") }
        compose.onNodeWithTag("video-popup").assertExists(); assertAdvances()
        home(); waitFor { compose.activity.isInPictureInPictureMode }; assertAdvances(); returnToApp()
        val sourceId = runBlocking { container.workspace.dao.observeTabs().first().first { it.url == server.origin + "/video-frame" }.id }
        runBlocking { container.tabs.close(sourceId) }
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun bothSettingsAreIndependentAndOffDisablesAssistantAndHomePip() {
        open(); tap("#start-video"); assertAdvances()
        compose.onNodeWithContentDescription("Settings").performClick()
        val pipTitle = "Automatically enter picture-in-picture"
        val popupTitle = "Pop-out videos"
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(pipTitle))
        compose.onNodeWithText(pipTitle).performClick()
        waitFor { runBlocking { !container.settings.settings.first().autoVideoPip } }
        assertTrue(runBlocking { container.settings.settings.first().videoPopups })
        compose.onNodeWithText(popupTitle).performClick()
        waitFor { runBlocking { !container.settings.settings.first().videoPopups } }
        compose.onNodeWithContentDescription("Back to browser").performClick()
        tap("#start-video"); assertAdvances()
        waitFor { js("document.querySelector('[data-nagi-video-assistant]').style.display") == "\"none\"" }
        tap("#fullscreen-video")
        compose.onNodeWithTag("video-minimize").assertDoesNotExist()
        home(); SystemClock.sleep(1200); assertFalse(compose.activity.isInPictureInPictureMode)
        returnToApp()
        compose.activityRule.scenario.recreate()
        assertFalse(runBlocking { container.settings.settings.first().autoVideoPip })
        assertFalse(runBlocking { container.settings.settings.first().videoPopups })
    }
    @Test fun pausedVideoAndGenericFullscreenDoNotAutomaticallyEnterPip() {
        open(); tap("#start-video"); assertAdvances(); tap("#fullscreen-video")
        main { web.evaluateJavascript("document.getElementById('player').pause()", null) }
        waitFor { js("document.getElementById('player').paused") == "true" }
        SystemClock.sleep(1000); home(); SystemClock.sleep(1200)
        assertFalse(compose.activity.isInPictureInPictureMode); returnToApp()
        compose.onNodeWithText("Exit fullscreen").performClick()
        main { web.loadUrl(server.origin + "/one") }
        waitFor { js("document.title") == "\"Fixture One\"" }
        tap("#fullscreen")
        compose.onNodeWithTag("video-minimize").assertDoesNotExist()
        home(); SystemClock.sleep(1200); assertFalse(compose.activity.isInPictureInPictureMode)
    }
}
