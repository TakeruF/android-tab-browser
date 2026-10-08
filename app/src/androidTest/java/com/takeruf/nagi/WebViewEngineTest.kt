package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.MotionEvent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.takeruf.nagi.ui.browser.BrowserSurface
import com.takeruf.nagi.testing.AutofillHarnessService
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
    private lateinit var siteDisplayModes: SiteDisplayModeStore
    private lateinit var ime: TestIme
    private val downloads = CopyOnWriteArrayList<DownloadRequest>()
    private val uploads = CopyOnWriteArrayList<FileSelectionRequest>()
    private val fullscreenRequests = CopyOnWriteArrayList<View>()
    private val contextActions = CopyOnWriteArrayList<List<PageContextAction>>()
    private val copiedLinks = CopyOnWriteArrayList<String>()
    private val generatedFiles = CopyOnWriteArrayList<Pair<GeneratedDownload, ByteArray>>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun showContextMenu(title: String, actions: List<PageContextAction>) { contextActions.add(actions) }
        override fun copyLink(url: String) { copiedLinks.add(url) }
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) { uploads.add(request); result(null) }
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) { result(emptySet()) }
        override fun download(request: DownloadRequest) { downloads.add(request) }
        override fun confirmGeneratedDownload(request: GeneratedDownload, result: (Boolean) -> Unit) { result(true) }
        override fun saveGeneratedDownload(request: GeneratedDownload, file: java.io.File) {
            generatedFiles.add(request to file.readBytes()); file.delete()
        }
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val displayPreferences = context.getSharedPreferences("engine_test_site_modes", android.content.Context.MODE_PRIVATE)
        displayPreferences.edit().clear().commit()
        siteDisplayModes = SiteDisplayModeStore(context, displayPreferences)
        server = FixtureServer(); scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false, siteDisplayModes = siteDisplayModes)
            (engine as AndroidEngineSurface).surface.id = View.generateViewId()
            it.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close(); server.close(); ime.close() }
    @Test fun blobAndDataExportsKeepNameMimeAndBytesEvenAfterObjectUrlRevocation() {
        main { engine.loadUrl("${server.origin}/features") }; await { it.title == "Fixture Features" && !it.isLoading }
        clickElement("blob-download")
        runBlocking { withTimeout(15_000) { while (generatedFiles.size < 1) delay(25) } }
        assertEquals("generated.txt", generatedFiles[0].first.name)
        assertEquals("text/plain", generatedFiles[0].first.mimeType)
        assertEquals(120000, generatedFiles[0].second.size)
        assertTrue(generatedFiles[0].second.all { it == 'x'.code.toByte() })
        clickElement("data-download")
        runBlocking { withTimeout(15_000) { while (generatedFiles.size < 2) delay(25) } }
        assertEquals("table.csv", generatedFiles[1].first.name)
        assertEquals("name,value\n日本語,42", generatedFiles[1].second.toString(Charsets.UTF_8))
    }
    @Test fun captureHintAndImageMimeReachNativePicker() {
        main { engine.loadUrl("${server.origin}/features") }; await { it.title == "Fixture Features" && !it.isLoading }
        clickElement("capture")
        runBlocking { withTimeout(5_000) { while (uploads.isEmpty()) delay(25) } }
        assertEquals(listOf("image/*"), uploads.first().mimeTypes)
        assertTrue(uploads.first().capture)
    }
    @Test fun suspensionProtectsEditedFormsAndRestoresCleanPageHistory() {
        main { engine.loadUrl("${server.origin}/features") }; await { it.title == "Fixture Features" && !it.isLoading }
        fun eligibility(): Boolean {
            val response = CompletableDeferred<Boolean>()
            main { engine.canSuspend { response.complete(it) } }
            return runBlocking { withTimeout(5_000) { response.await() } }
        }
        assertTrue(eligibility())
        main { engine.evaluateJavascript("document.getElementById('form-input').value='unsaved';document.title='Edited'") }
        await { it.title == "Edited" }
        assertFalse(eligibility())
        main { engine.loadUrl("${server.origin}/two") }; await { it.title == "Fixture Two" && !it.isLoading }
        assertTrue(eligibility())
    }
    @Test fun generatedFileIsSavedThroughAndroidDownloadsCollection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("download-test-", ".csv", context.cacheDir)
        val bytes = "name,value\n日本語,42".toByteArray()
        file.writeBytes(bytes)
        var uri: android.net.Uri? = null
        try {
            uri = com.takeruf.nagi.browser.downloads.DownloadService(context).saveGenerated(
                GeneratedDownload("nagi-fixture.csv", "text/csv", bytes.size.toLong(), server.origin), file)
            assertArrayEquals(bytes, context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
        } finally {
            uri?.let { context.contentResolver.delete(it, null, null) }; file.delete()
        }
    }

    @Test fun nativeImageDragDeliversReadableContentUriToAndroidDropTarget() {
        val dropped = CompletableDeferred<android.content.ClipData>()
        var target: View? = null
        scenario.onActivity { activity ->
            engine.destroy()
            engine = WebViewBrowserEngine(activity, host, host, { false }, false, nativePageDrag = { true })
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
            target = View(activity).apply {
                setBackgroundColor(android.graphics.Color.LTGRAY)
                setOnDragListener { _, event ->
                    if (event.action == android.view.DragEvent.ACTION_DROP) dropped.complete(event.clipData)
                    true
                }
            }
            activity.addContentView(target, android.widget.FrameLayout.LayoutParams(-1, 250, android.view.Gravity.BOTTOM))
        }
        main { engine.loadUrl("${server.origin}/features") }; await { it.title == "Fixture Features" && !it.isLoading }
        main { engine.evaluateJavascript("var r=document.getElementById('drag-image').getBoundingClientRect();document.title='drag:'+JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})") }
        val rect = JSONObject(await { it.title.startsWith("drag:") }.title.removePrefix("drag:"))
        val start = android.graphics.PointF()
        val end = android.graphics.PointF()
        main {
            val view = (engine as AndroidEngineSurface).surface
            val position = IntArray(2); view.getLocationOnScreen(position)
            val scale = view.width / rect.getDouble("w")
            start.set(position[0] + (rect.getDouble("x") * scale).toFloat(), position[1] + (rect.getDouble("y") * scale).toFloat())
            target!!.getLocationOnScreen(position)
            end.set(position[0] + target!!.width / 2f, position[1] + target!!.height / 2f)
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun pointer(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        pointer(MotionEvent.ACTION_DOWN, start.x, start.y)
        runBlocking { delay(900) }
        for (step in 1..15) {
            pointer(MotionEvent.ACTION_MOVE, start.x + (end.x - start.x) * step / 15, start.y + (end.y - start.y) * step / 15)
            runBlocking { delay(40) }
        }
        pointer(MotionEvent.ACTION_UP, end.x, end.y)
        val clip = runBlocking { withTimeout(10_000) { dropped.await() } }
        val uri = clip.getItemAt(0).uri
        assertNotNull(uri)
        assertEquals("content", uri.scheme)
        assertTrue(uri.authority!!.endsWith(".DropDataProvider"))
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertNotNull(android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }
    @Test fun suspendedWebViewRestoresHistoryAndScrollPosition() {
        lateinit var pool: com.takeruf.nagi.browser.tabs.EnginePool
        scenario.onActivity { activity ->
            engine.destroy()
            pool = com.takeruf.nagi.browser.tabs.EnginePool(factory = { _, desktop ->
                WebViewBrowserEngine(activity, host, host, { false }, desktop)
            })
            engine = pool.acquire("a", "${server.origin}/one", false)
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
        await { it.title == "Fixture One" && !it.isLoading }
        main { engine.loadUrl("${server.origin}/scroll") }; await { it.title == "Fixture Scroll" && !it.isLoading }
        main { (engine as AndroidEngineSurface).surface.scrollTo(0, 700); pool.suspendBackground(0, 0) }
        runBlocking { withTimeout(5_000) { while (true) {
            var sleeping = false; main { sleeping = pool.peek("a") == null }
            if (sleeping) break; delay(25)
        } } }
        scenario.onActivity { activity ->
            pool.setVisible(setOf("a"))
            engine = pool.acquire("a", "https://stale.example", false)
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
        await { it.title == "Fixture Scroll" && it.canGoBack && !it.isLoading }
        runBlocking { withTimeout(5_000) { while (true) {
            var scrolled = false; main { scrolled = (engine as AndroidEngineSurface).surface.scrollY == 700 }
            if (scrolled) break; delay(25)
        } } }
        main { engine.goBack() }; await { it.title == "Fixture One" && !it.isLoading }
    }
    @Test fun passwordProviderFillsWebsiteFieldsThroughComposeSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        val previous = Settings.Secure.getString(instrumentation.targetContext.contentResolver, "autofill_service")
        val previousInfo = automation.serviceInfo
        try {
            AutofillHarnessService.offered = false
            AutofillHarnessService.fieldHints = emptySet()
            shell("settings put secure autofill_service com.takeruf.nagi/.testing.AutofillHarnessService")
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            scenario.onActivity { activity ->
                val surface = (engine as AndroidEngineSurface).surface
                (surface.parent as? ViewGroup)?.removeView(surface)
                activity.setContent { BrowserSurface(engine, Modifier.fillMaxSize()) }
            }
            main { engine.loadUrl("${server.origin}/login") }
            await { it.title == "Fixture Login" && !it.isLoading }
            clickElement("login-user")
            runBlocking { withTimeout(15_000) { while (!AutofillHarnessService.offered) delay(50) } }
            assertTrue(AutofillHarnessService.fieldHints.contains("username"))
            assertTrue(AutofillHarnessService.fieldHints.contains("current-password"))
            var suggestion: AccessibilityNodeInfo? = null
            runBlocking { withTimeout(15_000) {
                while (suggestion == null) {
                    suggestion = automation.windows.asSequence().mapNotNull { it.root }
                        .flatMap { it.findAccessibilityNodeInfosByText(AutofillHarnessService.LABEL).asSequence() }.firstOrNull()
                    if (suggestion == null) delay(50)
                }
            } }
            val bounds = android.graphics.Rect()
            suggestion!!.getBoundsInScreen(bounds)
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            main { engine.evaluateJavascript("""
                var check=setInterval(()=>{if(document.getElementById('login-user').value==='fixture-user'
                    && document.getElementById('login-password').value==='fixture-password'){
                    clearInterval(check);document.title='Fixture Autofilled';}},50);
            """) }
            await { it.title == "Fixture Autofilled" }
        } finally {
            automation.serviceInfo = previousInfo
            if (previous == null) shell("settings delete secure autofill_service")
            else shell("settings put secure autofill_service $previous")
            assertEquals(previous, Settings.Secure.getString(instrumentation.targetContext.contentResolver, "autofill_service"))
        }
    }
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
            engine = WebViewBrowserEngine(it, host, host, { false }, false, siteDisplayModes = siteDisplayModes)
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
    @Test fun validatedUppercaseSearchTemplateCanNavigate() {
        val provider = com.takeruf.nagi.domain.model.SearchEngine("case-qa", "Case QA", "caseqa", "HTTPS://example.com/?q={query}")
        assertNull(com.takeruf.nagi.browser.search.InputResolver.validateEngine(provider))
        val url = com.takeruf.nagi.browser.search.InputResolver.search(provider, "tablet").url
        main { engine.loadUrl(url) }
        // The navigation contract must accept every scheme admitted by provider validation.
        assertEquals(url, engine.state.value.url)
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
    private fun longPressElement(id: String) {
        main { engine.evaluateJavascript("var r=document.getElementById('$id').getBoundingClientRect();document.title='$id:'+JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})") }
        val rect = JSONObject(await { it.title.startsWith("$id:") }.title.removePrefix("$id:"))
        var x = 0f; var y = 0f
        val now = SystemClock.uptimeMillis()
        main {
            val view = (engine as AndroidEngineSurface).surface
            val scale = view.width / rect.getDouble("w")
            x = (rect.getDouble("x") * scale).toFloat(); y = (rect.getDouble("y") * scale).toFloat()
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).let { view.dispatchTouchEvent(it); it.recycle() }
        }
        runBlocking { withTimeout(5_000) { while (contextActions.isEmpty()) delay(25) } }
        main { MotionEvent.obtain(now, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y, 0).let {
            (engine as AndroidEngineSurface).surface.dispatchTouchEvent(it); it.recycle()
        } }
    }
    @Test fun linkAndImageLongPressExposeCopyNewTabAndDownloadActions() = runBlocking {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        longPressElement("next")
        val linkActions = contextActions.last()
        main { linkActions.first { it.label == "Copy link" }.execute() }
        assertEquals("${server.origin}/two", copiedLinks.last())
        val opened = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { engine.events.first { it is EngineEvent.OpenTab } as EngineEvent.OpenTab } }
        main { linkActions.first { it.label == "Open link in new tab" }.execute() }
        assertEquals("${server.origin}/two", opened.await().url)
        contextActions.clear()
        longPressElement("context-image")
        main { contextActions.last().first { it.label == "Save image" }.execute() }
        assertEquals("${server.origin}/custom-icon.png", downloads.last().url)
        assertTrue(downloads.last().cookies?.contains("nagi=cookie") == true)
    }
    @Test fun siteDataClearRemovesCookieLocalStorageAndIndexedDbAcrossNewEngines() = runBlocking {
        main { engine.loadUrl("${server.origin}/one") }; await { it.title == "Fixture One" && !it.isLoading }
        main { engine.evaluateJavascript("var timer=setInterval(()=>{if(window.idbReady){clearInterval(timer);document.title='storage-ready'}},20)") }
        await { it.title == "storage-ready" }
        main { engine.destroy() }
        BrowserDataCleaner.clear(InstrumentationRegistry.getInstrumentation().targetContext)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            it.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
            engine.loadUrl("${server.origin}/two")
        }
        await { it.title == "Fixture Two" && !it.isLoading }
        main { engine.evaluateJavascript("indexedDB.databases().then(d=>document.title=JSON.stringify([document.cookie,localStorage.getItem('nagi'),d.map(x=>x.name)]))") }
        assertEquals("[\"\",null,[]]", await { it.title.startsWith("[") }.title)
    }
    @Test fun stalledInitialRequestKeepsUrlTimesOutAndCanNavigateAgain() = runBlocking {
        val url = "${server.origin}/stall"
        main { engine.loadUrl(url) }
        val timedOut = withTimeout(40_000) { engine.state.first { it.error?.contains("taking too long") == true } }
        assertEquals(url, timedOut.url); assertFalse(timedOut.isLoading)
        main { engine.loadUrl("${server.origin}/two") }
        val recovered = await { it.title == "Fixture Two" && !it.isLoading }
        assertNull(recovered.error)
    }
}
