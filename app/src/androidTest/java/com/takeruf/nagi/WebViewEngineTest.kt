package com.takeruf.nagi

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.ExtractedTextRequest
import android.view.MotionEvent
import android.os.SystemClock
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
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
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var server: FixtureServer
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: BrowserEngine
    private lateinit var siteDisplayModes: SiteDisplayModeStore
    private lateinit var ime: TestIme
    private val downloads = CopyOnWriteArrayList<DownloadRequest>()
    private var downloadResult: ((Boolean) -> Unit)? = null
    private val uploads = CopyOnWriteArrayList<FileSelectionRequest>()
    private val fullscreenRequests = CopyOnWriteArrayList<View>()
    private val contextActions = CopyOnWriteArrayList<List<PageContextAction>>()
    private val copiedLinks = CopyOnWriteArrayList<String>()
    private val messages = CopyOnWriteArrayList<String>()
    private val generatedFiles = CopyOnWriteArrayList<Pair<GeneratedDownload, ByteArray>>()
    private val host = object : BrowserHost, FullscreenHost {
        override fun showContextMenu(title: String, actions: List<PageContextAction>) { contextActions.add(actions) }
        override fun copyLink(url: String) { copiedLinks.add(url) }
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) { uploads.add(request); result(null) }
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) { result(emptySet()) }
        override fun download(request: DownloadRequest) { downloads.add(request) }
        override fun download(request: DownloadRequest, result: (Boolean) -> Unit) { downloads.add(request); downloadResult = result }
        override fun confirmGeneratedDownload(request: GeneratedDownload, result: (Boolean) -> Unit) { result(true) }
        override fun saveGeneratedDownload(request: GeneratedDownload, file: java.io.File) {
            generatedFiles.add(request to file.readBytes()); file.delete()
        }
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) { messages.add(message) }
        override fun showFullscreen(view: View, exit: () -> Unit) { fullscreenRequests.add(view); exit() }
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun await(predicate: (PageState) -> Boolean): PageState = runBlocking {
        try { withTimeout(15_000) { engine.state.first(predicate) } }
        catch (e: TimeoutCancellationException) { throw AssertionError("Page did not reach expected state: ${engine.state.value}", e) }
    }
    private fun clickElement(id: String, throughSystem: Boolean = false) {
        main { engine.evaluateJavascript("var r=document.getElementById('$id').getBoundingClientRect();document.title='$id:'+JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})") }
        val rect = JSONObject(await { it.title.startsWith("$id:") }.title.removePrefix("$id:"))
        var screenPoint: Pair<Int, Int>? = null
        main {
            val view = (engine as AndroidEngineSurface).surface
            val scale = view.width / rect.getDouble("w")
            val x = (rect.getDouble("x") * scale).toFloat(); val y = (rect.getDouble("y") * scale).toFloat()
            if (throughSystem) {
                val location = IntArray(2); view.getLocationOnScreen(location)
                screenPoint = location[0] + x.toInt() to location[1] + y.toInt()
            } else {
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
                view.dispatchTouchEvent(down); view.dispatchTouchEvent(up); down.recycle(); up.recycle()
            }
        }
        screenPoint?.let { (x, y) ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input tap $x $y")).use { it.readBytes() }
            instrumentation.waitForIdleSync()
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
    @Test fun darkPreferencePreservesImplicitWhiteCanvasAndAuthoredDarkBackground() {
        server.close()
        server = FixtureServer(pages = mapOf("/theme-canvas" to """
            <!doctype html><meta name="viewport" content="width=device-width">
            <style>html,body{margin:0;color:#111;background:transparent!important}header{height:80px;background:#fff}</style>
            <header>Light site header</header><main>Black text with an implicit background</main>
            <script>requestAnimationFrame(()=>requestAnimationFrame(()=>document.title='Canvas ready'))</script>
        """.trimIndent()))
        main { engine.loadUrl("${server.origin}/theme-canvas") }
        await { it.title == "Canvas ready" && !it.isLoading }
        main {
            engine.setDarkTheme(true)
            engine.evaluateJavascript("requestAnimationFrame(()=>requestAnimationFrame(()=>document.title='Dark preference applied'))")
        }
        await { it.title == "Dark preference applied" }
        fun canvasPixel(): Int {
            val point = IntArray(2)
            main {
                val view = (engine as AndroidEngineSurface).surface
                view.getLocationOnScreen(point)
                point[0] += view.width * 3 / 4
                point[1] += view.height / 2
            }
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            return try { bitmap.getPixel(point[0], point[1]) } finally { bitmap.recycle() }
        }
        fun awaitCanvas(expected: Int) {
            var pixel = canvasPixel()
            try {
                runBlocking { withTimeout(5_000) {
                    while (pixel != expected) { delay(50); pixel = canvasPixel() }
                } }
            } catch (error: TimeoutCancellationException) {
                throw AssertionError("Canvas ${engine.state.value.title}: expected ${Integer.toHexString(expected)}, actual ${Integer.toHexString(pixel)}", error)
            }
        }
        // A transparent HTML/body canvas must remain readable in a dark app.
        awaitCanvas(android.graphics.Color.WHITE)
        main { engine.evaluateJavascript("""
            document.documentElement.style.setProperty('background','#101112','important');
            document.body.style.color='#eeeeee';
            document.title='Authored dark canvas';
        """.trimIndent()) }
        await { it.title == "Authored dark canvas" }
        awaitCanvas(android.graphics.Color.rgb(16,17,18))
        main { engine.setDarkTheme(false) }
        awaitCanvas(android.graphics.Color.rgb(16,17,18))
        assertEquals(1, server.requests.count { it.path == "/theme-canvas" })
    }

    @Test fun websiteColorSchemeChangesWithoutReloadingOrLosingPageState() {
        main {
            engine.setDarkTheme(true)
            engine.loadUrl("${server.origin}/one")
        }
        await { !it.isLoading && it.url.endsWith("/one") }
        main { engine.evaluateJavascript("""
            const themeStyle = document.createElement('style');
            themeStyle.textContent = 'body {color: rgb(10, 20, 30) !important} @media(prefers-color-scheme:dark) {body {color: rgb(200, 210, 220) !important}}';
            document.head.appendChild(themeStyle);
            window.themeChanges = 0;
            window.themeQuery = matchMedia('(prefers-color-scheme: dark)');
            window.reportTheme = () => document.title = 'scheme:' + themeQuery.matches + ':' + themeChanges + ':' + getComputedStyle(document.body).color;
            themeQuery.addEventListener('change', () => { themeChanges++; reportTheme(); });
            reportTheme();
        """.trimIndent()) }
        await { it.title == "scheme:true:0:rgb(200, 210, 220)" }
        main { engine.setDarkTheme(false) }
        await { it.title == "scheme:false:1:rgb(10, 20, 30)" }
        main { engine.setDarkTheme(true) }
        await { it.title == "scheme:true:2:rgb(200, 210, 220)" }
        assertEquals(1, server.requests.count { it.path == "/one" })
    }

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
        val preview = automation.takeScreenshot()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "native-drag-preview.png")
            .outputStream().use { preview.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        preview.recycle()
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
    @Test fun passwordAutofillIsEnabledByDefaultForNormalBrowsing() {
        main { engine.destroy() }
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, false)
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
        main {
            assertTrue(engine.supportsPasswordAutofill)
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES,
                (engine as AndroidEngineSurface).surface.importantForAutofill)
        }
    }

    @Test fun passwordProviderFillsWebsiteFieldsThroughComposeSurface() {
        passwordProviderFillsWebsiteFields(manual = false)
    }
    @Test fun passwordProviderFillsWithOriginalSystemKeyboard() {
        // Compare the platform keyboard with the test IME; the provider offers popup UI only.
        ime.close()
        passwordProviderFillsWebsiteFields(manual = false, originalKeyboard = true)
    }
    @Test fun manuallyRequestedPasswordFillsAfterAutomaticSuggestionsAreUnavailable() {
        passwordProviderFillsWebsiteFields(manual = true)
    }
    @Test fun manuallyRequestedPasswordFillsWithoutProviderUiCallback() {
        passwordProviderFillsWebsiteFields(manual = true, suppressUiCallback = true)
    }
    @Test fun identifierOnlyLoginCanManuallyRequestCredentialsWithoutProviderUiCallback() {
        passwordProviderFillsWebsiteFields(manual = true, suppressUiCallback = true, identifierOnly = true)
    }
    @Test fun manualAutofillWithoutMatchesStillRequestsProviderAndReportsRequest() {
        passwordProviderFillsWebsiteFields(manual = true, declineManual = true)
    }
    @Test fun manualAutofillTargetsNewlyFocusedPasswordWithoutProviderUiCallback() {
        passwordProviderFillsWebsiteFields(manual = true, suppressUiCallback = true, switchField = true)
    }
    @Test fun manualAutofillTargetsRightSplitPaneWithDuplicateViewResourceIds() {
        passwordProviderFillsWebsiteFields(manual = true, suppressUiCallback = true, splitPane = true)
    }
    @Test fun legacyWebViewWithoutFocusedMetadataDoesNotInventVirtualId() {
        Assume.assumeTrue(android.webkit.WebView.getCurrentWebViewPackage()?.versionName?.startsWith("91.") == true)
        passwordProviderFillsWebsiteFields(manual = true, suppressUiCallback = true, expectUnidentifiedField = true)
    }
    private fun passwordProviderFillsWebsiteFields(manual: Boolean, suppressUiCallback: Boolean = false, identifierOnly: Boolean = false, declineManual: Boolean = false, switchField: Boolean = false, splitPane: Boolean = false, expectUnidentifiedField: Boolean = false, originalKeyboard: Boolean = false) {
        if (identifierOnly) {
            server.close()
            server = FixtureServer(pages = mapOf("/login" to """
                <!doctype html><title>Fixture Login</title><meta name="viewport" content="width=device-width">
                <form><label for="login-user">Login ID</label>
                <input id="login-user" name="username" autocomplete="username" required>
                <button type="submit">Continue</button></form>
            """.trimIndent()))
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        fun shell(command: String) = automation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        }
        val previous = Settings.Secure.getString(instrumentation.targetContext.contentResolver, "autofill_service")
        val previousInfo = automation.serviceInfo
        var stage = "load form"
        var otherTab: BrowserEngine? = null
        try {
            AutofillHarnessService.declineManualRequests = declineManual
            AutofillHarnessService.offered = false
            AutofillHarnessService.fieldHints = emptySet()
            AutofillHarnessService.offerOnlyOnManualRequest = manual
            AutofillHarnessService.manualRequests = 0
            AutofillHarnessService.automaticRequestsDeclined = 0
            AutofillHarnessService.focusedIdMatches = false
            AutofillHarnessService.fieldStructureValid = false
            AutofillHarnessService.totalRequests = 0
            AutofillHarnessService.nullResponses = 0
            shell("settings put secure autofill_service ${instrumentation.targetContext.packageName}/com.takeruf.nagi.testing.AutofillHarnessService")
            // Secure-settings observers and WebView's provider initialization are asynchronous.
            // Construct the tested engine only after Android reports the selected service.
            runBlocking { withTimeout(10_000) { while (true) {
                var ready = false
                scenario.onActivity { activity ->
                    val manager = activity.getSystemService(android.view.autofill.AutofillManager::class.java)
                    ready = manager.isEnabled && (android.os.Build.VERSION.SDK_INT < 28 ||
                        manager.autofillServiceComponentName == android.content.ComponentName(instrumentation.targetContext, AutofillHarnessService::class.java))
                }
                if (ready) break
                delay(50)
            } } }
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            scenario.onActivity { activity ->
                val oldSurface = (engine as AndroidEngineSurface).surface
                (oldSurface.parent as? ViewGroup)?.removeView(oldSurface)
                engine.destroy()
                engine = WebViewBrowserEngine(activity, host, host, { false }, false)
                // A newer background tab must not replace this tab's autofill callback.
                if (manual || splitPane) otherTab = WebViewBrowserEngine(activity, host, host, { false }, false)
                if (suppressUiCallback) activity.getSystemService(android.view.autofill.AutofillManager::class.java)
                    .registerCallback(object : android.view.autofill.AutofillManager.AutofillCallback() {})
                val surface = (engine as AndroidEngineSurface).surface
                (surface.parent as? ViewGroup)?.removeView(surface)
                activity.setContent {
                    androidx.compose.material3.MaterialTheme(colorScheme = if (splitPane)
                        androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) {
                        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                            if (splitPane) BrowserSurface(checkNotNull(otherTab), Modifier.weight(1f).fillMaxHeight())
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                if (manual) {
                                    com.takeruf.nagi.ui.browser.BrowserPane(
                                        tab = com.takeruf.nagi.domain.model.BrowserTab("autofill", "fixture"), engine = engine,
                                        state = com.takeruf.nagi.ui.browser.BrowserUiState(), focused = true,
                                        split = splitPane, showFind = false, onCloseFind = {}, onFind = {}, onFocus = {},
                                        onOmnibox = {}, onOpen = {}, onSplit = {}, onCloseSplit = {}, onCloseTab = {}, onSwap = {})
                                } else BrowserSurface(engine, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                if (splitPane) {
                    val otherSurface = (checkNotNull(otherTab) as AndroidEngineSurface).surface
                    assertEquals(surface.id, otherSurface.id)
                    assertNotEquals(surface.autofillId, otherSurface.autofillId)
                    checkNotNull(otherTab).loadUrl("${server.origin}/login")
                }
            }
            main { engine.loadUrl("${server.origin}/login") }
            await { it.title == "Fixture Login" && !it.isLoading }
            instrumentation.waitForIdleSync()
            compose.waitForIdle()
            clickElement("login-user", throughSystem = true)
            stage = "focus HTML field"
            runBlocking { withTimeout(5_000) { while (true) {
                var focused = false
                main { focused = (engine as AndroidEngineSurface).surface.hasFocus() }
                if (focused) break
                delay(50)
            } } }
            if (!originalKeyboard) ime.awaitBinding()
            main {
                val surface = (engine as AndroidEngineSurface).surface
                assertEquals(View.IMPORTANT_FOR_AUTOFILL_YES, surface.importantForAutofill)
                var ancestor = surface.parent
                while (ancestor is View) {
                    assertNotEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, ancestor.importantForAutofill)
                    assertNotEquals(View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS, ancestor.importantForAutofill)
                    ancestor = ancestor.parent
                }
            }
            if (manual) {
                stage = "decline automatic request"
                runBlocking { withTimeout(10_000) { while (AutofillHarnessService.automaticRequestsDeclined == 0) delay(50) } }
                assertFalse(AutofillHarnessService.offered)
                if (switchField) {
                    clickElement("login-password", throughSystem = true)
                    instrumentation.waitForIdleSync()
                }
                stage = "open page menu"
                compose.onNodeWithContentDescription("Page menu").performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitForIdle()
                stage = "choose manual autofill"
                compose.onNodeWithTag("request-password-autofill").performSemanticsAction(SemanticsActions.OnClick) { it() }
                compose.waitForIdle()
            }
            if (expectUnidentifiedField) {
                val expected = instrumentation.targetContext.getString(R.string.ui_autofill_field_unidentified)
                runBlocking { withTimeout(5_000) { while (expected !in messages) delay(50) } }
                assertEquals(0, AutofillHarnessService.manualRequests)
                assertTrue(AutofillHarnessService.automaticRequestsDeclined > 0)
                return
            }
            if (declineManual) {
                runBlocking { withTimeout(10_000) { while (AutofillHarnessService.manualRequests == 0 || messages.none { it == "Requested suggestions from your autofill service." }) delay(50) } }
                assertTrue(AutofillHarnessService.manualRequests > 0)
                assertTrue(AutofillHarnessService.nullResponses > 0)
                assertFalse(AutofillHarnessService.offered)
                assertFalse(messages.any { it.startsWith("Tap a login field first") })
                return
            }
            stage = "offer credentials"
            runBlocking { withTimeout(15_000) { while (!AutofillHarnessService.offered) delay(50) } }
            assertTrue("The requested virtual ID must identify the currently focused HTML field", AutofillHarnessService.focusedIdMatches)
            assertTrue("HTML fields need distinct IDs, text types, and nonempty native bounds", AutofillHarnessService.fieldStructureValid)
            assertTrue(AutofillHarnessService.fieldHints.contains("username"))
            if (!identifierOnly) assertTrue(AutofillHarnessService.fieldHints.contains("current-password"))
            var suggestionBounds: android.graphics.Rect? = null
            runBlocking { withTimeout(15_000) {
                var previousBounds: android.graphics.Rect? = null
                var stableSamples = 0
                while (suggestionBounds == null) {
                    if (android.os.Build.VERSION.SDK_INT >= 36) automation.clearCache()
                    val suggestion = automation.windows.asSequence().mapNotNull { it.root }
                        .flatMap { it.findAccessibilityNodeInfosByText(AutofillHarnessService.LABEL).asSequence() }.firstOrNull()
                    val bounds = android.graphics.Rect()
                    suggestion?.getBoundsInScreen(bounds)
                    if (suggestion?.isVisibleToUser == true && !bounds.isEmpty && bounds == previousBounds) stableSamples++
                    else stableSamples = 0
                    previousBounds = bounds
                    if (stableSamples >= 2) suggestionBounds = bounds else delay(50)
                }
            } }
            val bounds = checkNotNull(suggestionBounds)
            if (manual) shell("screencap -p /data/local/tmp/nagi-autofill-manual-suggestion.png")
            shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
            main { engine.evaluateJavascript("""
                var check=setInterval(()=>{if(document.getElementById('login-user').value==='fixture-user'
                    && (${identifierOnly} || document.getElementById('login-password').value==='fixture-password')){
                    clearInterval(check);document.title='Fixture Autofilled';}},50);
            """) }
            await { it.title == "Fixture Autofilled" }
            if (manual) assertTrue(AutofillHarnessService.manualRequests > 0)
        } catch (error: Throwable) {
            shell("screencap -p /data/local/tmp/nagi-autofill-failure.png")
            throw AssertionError("Autofill failed at: $stage; manualRequests=${AutofillHarnessService.manualRequests}; messages=$messages", error)
        } finally {
            main { otherTab?.destroy() }
            AutofillHarnessService.offerOnlyOnManualRequest = false
            AutofillHarnessService.declineManualRequests = false
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
    @Test fun freshDownloadSignalsCleanupOnlyAfterNativeAcceptance() = runBlocking {
        main { engine.loadUrl("${server.origin}/download") }
        withTimeout(15_000) { while (downloadResult == null) delay(25) }
        assertNull(withTimeoutOrNull(300) { engine.events.first { it is EngineEvent.DownloadStartedWithoutDocument } })
        main { downloadResult!!.invoke(true) }
        val event = withTimeout(5_000) { engine.events.first { it is EngineEvent.DownloadStartedWithoutDocument } }
        assertEquals(EngineEvent.DownloadStartedWithoutDocument, event)
    }
    @Test fun navigationDuringDownloadConfirmationPreventsCleanup() = runBlocking {
        main { engine.loadUrl("${server.origin}/download") }
        withTimeout(15_000) { while (downloadResult == null) delay(25) }
        main { engine.loadUrl("${server.origin}/one") }
        await { it.title == "Fixture One" && !it.isLoading }
        main { downloadResult!!.invoke(true) }
        assertNull(withTimeoutOrNull(300) { engine.events.first { it is EngineEvent.DownloadStartedWithoutDocument } })
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
