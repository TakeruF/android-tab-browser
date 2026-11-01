package com.takeruf.nagi

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.geometry.Offset
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
    private fun waitFor(predicate: () -> Boolean) {
        val callSite = AssertionError("Video condition did not complete")
        try {
            runBlocking { withTimeout(15_000) { while (!predicate()) delay(100) } }
        } catch (failure: TimeoutCancellationException) {
            callSite.initCause(failure)
            throw callSite
        }
    }
    private fun js(script: String): String {
        val callSite = AssertionError("Video JavaScript did not answer: ${script.take(120)}")
        val result = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { result.complete(it) } }
        return try { runBlocking { withTimeout(5_000) { result.await() } } }
        catch (failure: TimeoutCancellationException) { callSite.initCause(failure); throw callSite }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun shortcut(key: Int) = main {
        compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, key, 0,
            if (key == KeyEvent.KEYCODE_ESCAPE) 0 else KeyEvent.META_CTRL_ON))
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
        if (!inFrame) {
            js("document.querySelector(${JSONObject.quote(selector)}).scrollIntoView({block:'center',inline:'center'}); true")
            awaitVisualState()
        }
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
    private fun awaitVisualState() {
        val ready = CompletableDeferred<Unit>()
        main {
            web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.complete(Unit) }
            })
            web.invalidate()
        }
        compose.waitUntil(5_000) { ready.isCompleted }
    }
    private fun tapAt(x: Double, y: Double, viewport: Double) = main {
        val scale = web.width / viewport
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, (x * scale).toFloat(), (y * scale).toFloat(), 0)
        val up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, (x * scale).toFloat(), (y * scale).toFloat(), 0)
        web.dispatchTouchEvent(down); web.dispatchTouchEvent(up); down.recycle(); up.recycle()
    }
    private fun ticks() = js("window.videoTicks").toIntOrNull() ?: 0
    private fun assertAdvances() {
        val before = ticks()
        try { waitFor { ticks() >= before + 3 } }
        catch (failure: AssertionError) {
            throw AssertionError("Video playback stalled: " + js("""
                JSON.stringify({ticks:window.videoTicks,video:Array.from(document.querySelectorAll('video')).map(v=>({
                  paused:v.paused,time:v.currentTime,ready:v.readyState,network:v.networkState,error:v.error?.code})),
                  frameReady:window.frameReady,framePaused:window.framePaused,
                  frameRectRevision:window.frameRectRevision,height:innerHeight,width:innerWidth})
            """), failure)
        }
    }
    private fun assertPageSurfaceRestored() {
        compose.waitUntil(10_000) {
            var shown = false
            main { shown = web.isShown && web.parent != null && web.width > 100 && web.height > 100 }
            shown
        }
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-target]')"))
    }
    private fun switchToNewTab() {
        shortcut(KeyEvent.KEYCODE_T)
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        shortcut(KeyEvent.KEYCODE_ESCAPE)
        compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }
    private fun showPopupControls() {
        if (compose.onAllNodesWithTag("video-popup-controls").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("video-popup-toggle-controls").performClick()
        }
        compose.onNodeWithTag("video-popup-controls").assertExists()
    }
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
            container.tabs.create(requireNotNull(container.settings.settings.first().selectedSpaceId), server.origin + "/one", select = false)
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
        compose.onNodeWithContentDescription("Exit fullscreen").performClick()
        waitFor { js("!!document.fullscreenElement") == "false" }
    }
    @Test fun desktopVideoPipDoesNotSwitchTheBrowserToMobileOrReloadThePlayer() {
        fun shell(command: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        val original = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        val density = compose.activity.resources.displayMetrics.density
        try {
            shell("wm size ${(1000 * density).toInt()}x${(800 * density).toInt()}")
            compose.waitUntil(10_000) { compose.activity.resources.configuration.screenWidthDp >= 640 }
            runBlocking {
                compose.activity.getSharedPreferences("site_display_modes", android.content.Context.MODE_PRIVATE)
                    .edit().remove("127.0.0.1").commit()
                container.settings.update { it.copy(desktopDefault = true) }
            }
            fullscreenHomeKeepsPlayingInPipAndRestoresSamePlayer()
            assertFalse(js("navigator.userAgent").contains("Android"))
            assertEquals(1, server.requests.count { it.path == "/video" })
            assertFalse(server.requests.first { it.path == "/video" }.userAgent.contains("Android"))
        } finally { shell("wm size $original") }
    }
    @Test fun popupAutomaticallyKeepsPlayingAcrossTabsAndSupportsDragResizePauseAndReturn() {
        open(); tap("#start-video"); assertAdvances()
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-assistant]')"))
        switchToNewTab()
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        assertAdvances()
        assertEquals("true", js("!!document.querySelector('[data-nagi-video-target]')"))
        compose.onNodeWithTag("video-popup-controls").assertDoesNotExist()
        screenshot("video-popup-video-only")
        val unadorned = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
        // A physical tap reveals our controls without toggling the underlying site's video.
        compose.onNodeWithTag("video-popup-toggle-controls").performTouchInput { click(center) }
        compose.onNodeWithTag("video-popup-controls").assertExists()
        assertEquals("false", js("document.getElementById('player').paused"))
        compose.waitUntil(5_000) { compose.onNodeWithTag("video-popup-time").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
            .any { !it.text.contains("--:--") } }
        assertEquals(unadorned, compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot)
        val playButton = compose.onNodeWithTag("video-popup-play-pause").fetchSemanticsNode().boundsInRoot
        assertEquals(unadorned.center.x, playButton.center.x, 1f)
        assertEquals(unadorned.center.y, playButton.center.y, 1f)
        val closeButton = compose.onNodeWithTag("video-popup-close").fetchSemanticsNode().boundsInRoot
        assertTrue(closeButton.center.x > unadorned.center.x && closeButton.center.y < unadorned.center.y)
        screenshot("video-popup-tapped-controls")
        compose.mainClock.advanceTimeBy(3_100)
        compose.waitUntil(6_000) { compose.onAllNodesWithTag("video-popup-controls").fetchSemanticsNodes().isEmpty() }
        assertAdvances()
        val before = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("video-popup-drag").performTouchInput { swipeUp() }
        val after = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
        assertTrue(after.top < before.top)
        compose.onNodeWithTag("video-popup-drag").performTouchInput {
            pinch(start0 = Offset(width * 0.35f, height / 2f), start1 = Offset(width * 0.65f, height / 2f),
                end0 = Offset(width * 0.15f, height / 2f), end1 = Offset(width * 0.85f, height / 2f))
        }
        val resizedWidth = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot.width
        assertTrue(resizedWidth > before.width)
        showPopupControls()
        compose.onNodeWithTag("video-popup-play-pause").performClick()
        waitFor { js("document.getElementById('player').paused") == "true" }
        compose.onNodeWithTag("video-popup-play-pause").performClick(); assertAdvances()
        home(); waitFor { compose.activity.isInPictureInPictureMode }; assertAdvances()
        returnToApp(); compose.onNodeWithTag("video-popup").assertExists()
        assertEquals(resizedWidth, compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot.width, 1f)
        showPopupControls()
        compose.onNodeWithTag("video-popup-return").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isEmpty() }
        waitFor { js("!!document.querySelector('[data-nagi-video-target]')") == "false" }
        assertPageSurfaceRestored()
        assertAdvances()
        switchToNewTab()
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        showPopupControls()
        compose.onNodeWithTag("video-popup-close").performClick()
        waitFor { js("document.getElementById('player').paused") == "true" }
    }
    @Test fun mouseCornersResizeWithoutMovingTheOppositeCornerOrStealingTouchControls() {
        open(); tap("#start-video"); assertAdvances(); switchToNewTab()
        compose.onNodeWithTag("video-popup").assertExists()
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("video-popup-drag").performTouchInput {
            swipe(center, center + Offset(-250f * density, -160f * density), durationMillis = 400)
        }
        js("""
            window.resizeMediaEvents=[];
            const resizePlayer=document.getElementById('player');
            for(const name of ['pause','play','seeking','seeked','ended'])resizePlayer.addEventListener(name,()=>
                resizeMediaEvents.push({name,time:resizePlayer.currentTime}));
            const originalPause=resizePlayer.pause.bind(resizePlayer);
            resizePlayer.pause=()=>{resizeMediaEvents.push({name:'pause-call',stack:new Error().stack});originalPause();};true
        """.trimIndent())
        for ((left, top) in listOf(true to true, false to true, true to false, false to false)) {
            val before = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
            val inset = 6f * density
            val start = Offset(if (left) before.left + inset else before.right - inset,
                if (top) before.top + inset else before.bottom - inset)
            val delta = Offset((if (left) -40f else 40f) * density,
                (if (top) -22.5f else 22.5f) * density)
            // Split the drag across layouts to catch feedback when the top/left origin moves.
            compose.onRoot().performMouseInput { moveTo(start); press(); moveTo(start + delta / 2f, delayMillis = 100) }
            compose.waitForIdle()
            compose.onRoot().performMouseInput { moveTo(start + delta, delayMillis = 100); release() }
            compose.waitForIdle()
            val after = compose.onNodeWithTag("video-popup").fetchSemanticsNode().boundsInRoot
            assertTrue("Corner $left/$top must grow the video", after.width > before.width + 20f * density)
            assertEquals(before.width / before.height, after.width / after.height, 0.01f)
            assertEquals(if (left) before.right else before.left, if (left) after.right else after.left, 2f)
            assertEquals(if (top) before.bottom else before.top, if (top) after.bottom else after.top, 2f)
            compose.onNodeWithTag("video-popup-controls").assertDoesNotExist()
            assertEquals("Playback after corner $left/$top: " + js("JSON.stringify(window.resizeMediaEvents)"),
                "false", js("document.getElementById('player').paused"))
        }
        assertAdvances()
        screenshot("video-popup-mouse-corner-resized")
        // The same corner remains a normal reveal tap for touch, and the nearby X stays clickable.
        compose.onNodeWithTag("video-popup-toggle-controls").performTouchInput {
            click(Offset(6f * density, 6f * density))
        }
        compose.onNodeWithTag("video-popup-controls").assertExists()
        compose.onNodeWithTag("video-popup-close").performMouseInput { click(center) }
        compose.onNodeWithTag("video-popup").assertDoesNotExist()
        waitFor { js("document.getElementById('player').paused") == "true" }
    }
    @Test fun crossOriginFrameAutomaticallyPopsOutAndClosingSourceTabClosesPopup() {
        open("/video-frame")
        fun tapFrame(name: String) {
            // A child can report DOM coordinates before the video metadata changes
            // its layout or WebView commits that layout to the native hit-test surface.
            waitFor { js("!!window.frameRects?.$name && window.frameReady >= 2") == "true" }
            js("document.querySelector('#player-frame').scrollIntoView({block:'start'}); true")
            val revision = js("window.frameRectRevision").toInt()
            awaitVisualState()
            waitFor { js("window.frameRectRevision > $revision") == "true" }
            val rect = JSONObject(JSONTokener(js("""
                (()=>{const f=document.querySelector('#player-frame').getBoundingClientRect(),r=window.frameRects.$name;
                return JSON.stringify({x:f.x+r.x,y:f.y+r.y,w:innerWidth});})()
            """)).nextValue() as String)
            tapAt(rect.getDouble("x"), rect.getDouble("y"), rect.getDouble("w"))
        }
        tapFrame("start"); assertAdvances(); switchToNewTab()
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        assertAdvances()
        runBlocking { container.spaces.create("Other Video Space ${System.nanoTime()}") }
        compose.onNodeWithTag("video-popup").assertExists(); assertAdvances()
        home(); waitFor { compose.activity.isInPictureInPictureMode }; assertAdvances(); returnToApp()
        val sourceId = runBlocking { container.workspace.dao.observeTabs().first().first { it.url == server.origin + "/video-frame" }.id }
        runBlocking { container.tabs.close(sourceId) }
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun oneTabInlineVideoAutomaticallyEntersHomePipAndRestoresPage() {
        open(); tap("#start-video"); assertAdvances()
        runBlocking {
            val space = container.settings.settings.first().selectedSpaceId
            val other = container.workspace.dao.observeTabs().first().single { it.spaceId == space && it.url == server.origin + "/one" }
            container.tabs.close(other.id)
        }
        compose.waitForIdle()
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-assistant]')"))
        assertEquals("false", js("!!document.fullscreenElement"))
        SystemClock.sleep(1000)
        home(); waitFor { compose.activity.isInPictureInPictureMode }; assertAdvances()
        screenshot("video-inline-home-pip")
        returnToApp()
        compose.waitUntil { compose.onAllNodesWithTag("video-fullscreen").fetchSemanticsNodes().isEmpty() }
        waitFor { js("!!document.querySelector('[data-nagi-video-target]')") == "false" }
        assertPageSurfaceRestored()
        assertAdvances()
        assertEquals(1, server.requests.count { it.path == "/video" })
    }
    @Test fun selectingSourceTabClosesPopupAndKeepsSameVideoPlaying() {
        open(); tap("#start-video"); assertAdvances()
        val sourceId = runBlocking { container.workspace.dao.observeTabs().first().first { it.url == server.origin + "/video" }.id }
        switchToNewTab()
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
        waitFor { runBlocking {
            val space = container.settings.settings.first().selectedSpaceId
            container.workspace.dao.spaces().first { it.id == space }.activeTabId != sourceId
        } }
        compose.onNodeWithTag("video-popup").assertExists(); assertAdvances()
        val before = ticks()
        runBlocking { container.tabs.select(sourceId) }
        compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isEmpty() }
        waitFor { js("!!document.fullscreenElement") == "false" }
        assertPageSurfaceRestored()
        assertAdvances()
        assertTrue(ticks() > before)
        assertEquals(1, server.requests.count { it.path == "/video" })
    }
    @Test fun returningFromPopupPreservesDesktopPageZoomAcrossRepeatedTabSwitches() {
        open()
        js("document.querySelector('meta[name=viewport]').content='width=1440'; true")
        tap("#start-video"); assertAdvances()
        main { web.zoomBy(1.25f) }
        assertAdvances()
        val originalScale = js("visualViewport.scale").toDouble()
        val originalWidth = web.width
        val sourceId = runBlocking { container.workspace.dao.observeTabs().first().first { it.url == server.origin + "/video" }.id }
        repeat(3) {
            switchToNewTab()
            compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isNotEmpty() }
            assertAdvances()
            runBlocking { container.tabs.select(sourceId) }
            compose.waitUntil { compose.onAllNodesWithTag("video-popup").fetchSemanticsNodes().isEmpty() }
            assertPageSurfaceRestored()
            waitFor { web.width == originalWidth }
            assertAdvances()
            assertEquals("Page zoom changed after popup return $it", originalScale,
                js("visualViewport.scale").toDouble(), 0.02)
        }
        assertEquals(1, server.requests.count { it.path == "/video" })
    }
    @Test fun bothAutomaticSettingsAreIndependentAndPersistWhenDisabled() {
        open(); tap("#start-video"); assertAdvances()
        compose.onNodeWithContentDescription("Settings").performClick()
        val pipTitle = "Automatically enter picture-in-picture"
        val popupTitle = "Automatically pop out videos"
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(pipTitle))
        compose.onNodeWithText(pipTitle).performClick()
        waitFor { runBlocking { !container.settings.settings.first().autoVideoPip } }
        assertTrue(runBlocking { container.settings.settings.first().videoPopups })
        compose.onNodeWithText(popupTitle).performClick()
        waitFor { runBlocking { !container.settings.settings.first().videoPopups } }
        compose.onNodeWithContentDescription("Back to browser").performClick()
        tap("#start-video"); assertAdvances()
        switchToNewTab()
        compose.onNodeWithTag("video-popup").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("Exit fullscreen").performClick()
        main { web.loadUrl(server.origin + "/one") }
        waitFor { js("document.title") == "\"Fixture One\"" }
        tap("#fullscreen")
        compose.onNodeWithTag("video-minimize").assertDoesNotExist()
        home(); SystemClock.sleep(1200); assertFalse(compose.activity.isInPictureInPictureMode)
    }
}
