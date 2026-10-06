package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.MotionEvent
import android.os.SystemClock
import org.json.JSONObject
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

class WebViewEngineTest {
    private lateinit var server: FixtureServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: BrowserEngine
    private lateinit var ime: TestIme
    private val downloads = CopyOnWriteArrayList<DownloadRequest>()
    private val uploads = CopyOnWriteArrayList<FileSelectionRequest>()
    private val fullscreenRequests = CopyOnWriteArrayList<View>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) { uploads.add(request); result(null) }
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) { result(emptySet()) }
        override fun download(request: DownloadRequest) { downloads.add(request) }
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) { fullscreenRequests.add(view); exit() }
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun await(predicate: (PageState) -> Boolean): PageState = runBlocking {
        try { withTimeout(15_000) { engine.state.first(predicate) } }
        catch (e: TimeoutCancellationException) { throw AssertionError("Page did not reach expected state: ${engine.state.value}", e) }
    }
    private fun clickElement(id: String) {
        main { engine.evaluateJavascript("var r=document.getElementById('$id').getBoundingClientRect();document.title='$id:'+JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})") }
        val rect = JSONObject(await { it.title.startsWith("$id:") }.title.removePrefix("$id:"))
        main {
            val view = (engine as AndroidEngineSurface).surface
            val scale = view.width / rect.getDouble("w")
            val x = (rect.getDouble("x") * scale).toFloat(); val y = (rect.getDouble("y") * scale).toFloat()
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); down.recycle(); up.recycle()
        }
    }
    @Before fun setup() {
        ime = TestIme()
        server = FixtureServer(); scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            (engine as AndroidEngineSurface).surface.id = View.generateViewId()
            it.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close(); server.close(); ime.close() }
    @Test fun javascriptCookiesLocalStorageIndexedDbNavigationAndFindWork() {
        main { engine.loadUrl("${server.origin}/one") }
        await { it.title == "Fixture One" && !it.isLoading }
        main { engine.evaluateJavascript("""
            var timer=setInterval(()=>{if(window.idbReady){clearInterval(timer);
            document.title=localStorage.getItem('nagi')+'|'+document.cookie+'|indexeddb';}},20);
        """) }
        val stored = await { it.title.contains("indexeddb") }
        assertTrue(stored.title.contains("stored")); assertTrue(stored.title.contains("nagi=cookie"))
        main { engine.findInPage("needle") }; await { it.findMatches > 0 }
        main { engine.loadUrl("${server.origin}/two") }; await { it.title == "Fixture Two" && !it.isLoading }
        assertTrue(engine.state.value.canGoBack)
        main { engine.goBack() }; await { it.url.endsWith("/one") && !it.isLoading }
        assertTrue(engine.state.value.canGoForward)
        main { engine.goForward() }; await { it.title == "Fixture Two" && !it.isLoading }
    }
    @Test fun engineSnapshotRestoresNavigationAndDesktopModeCanBeChanged() {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        main { engine.loadUrl("${server.origin}/two") }; await { it.title == "Fixture Two" && !it.isLoading }
        var snapshot: EngineSnapshot? = null
        main { snapshot = engine.saveState(); engine.destroy() }
        assertNotNull(snapshot)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            it.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
        main { assertTrue(engine.restoreState(snapshot!!)) }
        await { it.url.endsWith("/two") && it.canGoBack }
        main { engine.setDesktopMode(true); engine.loadUrl("${server.origin}/ua") }
        await { it.title == "Fixture Desktop" && it.desktopMode && !it.isLoading }
    }
    @Test fun downloadListenerPreservesAuthenticationCookieAndFilename() {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        main { engine.loadUrl("${server.origin}/download") }
        runBlocking { withTimeout(15_000) { while (downloads.isEmpty()) delay(25) } }
        assertEquals("nagi.txt", downloads.first().suggestedName)
        assertTrue(downloads.first().cookies?.contains("nagi=cookie") == true)
        await { it.url == "${server.origin}/one" && !it.isLoading }
        assertEquals("Fixture One", engine.state.value.title)
    }
    @Test fun webPageFieldsKeepJapaneseChineseAndKoreanComposition() {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        main {
            (engine as AndroidEngineSurface).surface.requestFocus()
            engine.evaluateJavascript("var e=document.getElementById('editor'),r=e.getBoundingClientRect();e.oninput=function(){document.title=this.value};document.title=JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})")
        }
        val rect = JSONObject(await { it.title.startsWith("{") }.title)
        main {
            val view = (engine as AndroidEngineSurface).surface
            val scale = view.width / rect.getDouble("w")
            val x = (rect.getDouble("x") * scale).toFloat(); val y = (rect.getDouble("y") * scale).toFloat()
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); down.recycle(); up.recycle()
        }
        val ic = ime.connection((engine as AndroidEngineSurface).surface.id)
        for ((preedit, result) in listOf("にほんご" to "日本語", "zhongwen" to "中文", "한구" to "한국어")) {
            val length = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.length ?: 0
            main { ic.setSelection(0, length); ic.commitText("", 1); ic.setComposingText(preedit, 1) }
            await { it.title == preedit }
            main { ic.commitText(result, 1); ic.finishComposingText() }
            await { it.title == result }
            main { ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER)) }
            await { it.title.contains(result) }
        }
    }
    @Test fun targetBlankPopupUsesNewTabEvent() = runBlocking {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        val result = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { engine.events.first { it is EngineEvent.OpenTab } as EngineEvent.OpenTab } }
        clickElement("popup")
        assertEquals("${server.origin}/two", result.await().url)
    }
    @Test fun fileUploadAndFullscreenReachNativeHost() {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        clickElement("upload")
        runBlocking { withTimeout(10_000) { while (uploads.isEmpty()) delay(25) } }
        assertFalse(uploads.first().multiple)
        clickElement("fullscreen")
        runBlocking { withTimeout(10_000) { while (fullscreenRequests.isEmpty()) delay(25) } }
    }
}
