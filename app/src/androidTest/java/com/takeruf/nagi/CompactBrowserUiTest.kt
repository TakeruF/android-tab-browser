package com.takeruf.nagi

import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalTestApi::class)
class CompactBrowserUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var originalSize: String

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }

    private fun resize(widthDp: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(widthDp * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) {
            kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - widthDp) < 20
        }
        compose.waitForIdle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            compose.activity.getSharedPreferences("site_display_modes", android.content.Context.MODE_PRIVATE)
                .edit().remove("127.0.0.1").remove("localhost").commit()
            container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Phone QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
        }
        resize(360)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("compact-browser-bar").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        shell("wm size $originalSize")
        server.close()
    }

    private fun open(url: String) {
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId }
        compose.onNodeWithTag("url-drop-$active").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(url)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
    }

    private fun awaitTitle(title: String) {
        compose.waitUntil(15_000) {
            runBlocking { container.workspace.dao.tabs(spaceId).any { it.title == title && it.closedAt == null } }
        }
        compose.waitForIdle()
    }

    private fun webViews(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown && it.width > 0 } }
        return result
    }

    private fun pageData(view: WebView): org.json.JSONObject {
        val result = kotlinx.coroutines.CompletableDeferred<String>()
        compose.runOnIdle {
            view.evaluateJavascript("""JSON.stringify({width:innerWidth,scale:visualViewport.scale,
                mobile:navigator.userAgentData?.mobile,ua:navigator.userAgent,
                viewport:document.querySelector('meta[name=viewport]').content,
                token:window.__resizeToken,draft:document.getElementById('draft').value,
                notes:document.getElementById('notes').value,editable:document.getElementById('editable').textContent})""") {
                result.complete(it)
            }
        }
        val encoded = runBlocking { kotlinx.coroutines.withTimeout(10_000) { result.await() } }
        return org.json.JSONObject(org.json.JSONArray("[$encoded]").getString(0))
    }

    private fun enterDraft(view: WebView) {
        compose.runOnIdle {
            view.evaluateJavascript("""window.__resizeToken='original document';
                document.getElementById('draft').value='入力中のデータ';
                document.getElementById('notes').value='unfinished long message';
                document.getElementById('editable').textContent='編集中の文章';
                document.getElementById('draft').dispatchEvent(new Event('input',{bubbles:true}));""", null)
        }
        assertDraft(view)
    }

    private fun assertDraft(view: WebView) {
        val data = pageData(view)
        assertEquals("original document", data.getString("token"))
        assertEquals("入力中のデータ", data.getString("draft"))
        assertEquals("unfinished long message", data.getString("notes"))
        assertEquals("編集中の文章", data.getString("editable"))
    }

    @Test fun thumbControlsOpenSelectCloseTabsAndSwitchSpaces() {
        open("${server.origin}/one"); awaitTitle("Fixture One")
        val first = runBlocking { container.workspace.dao.tabs(spaceId).first { it.title == "Fixture One" }.id }
        compose.onNodeWithTag("sidebar").assertDoesNotExist()
        val pane = compose.onNodeWithTag("browser-pane-left-$first").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag("compact-browser-bar").fetchSemanticsNode().boundsInRoot
        assertEquals(bar.width, pane.width, 1f)
        compose.onNodeWithTag("compact-tabs-button").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("New tab").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("${server.origin}/two")
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        awaitTitle("Fixture Two")
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription("Tab Fixture One").performScrollTo().performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId == first } }
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription("Close Fixture One").performClick()
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.tab(first)?.closedAt != null } }
        compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Tabs").performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        val other = runBlocking { container.workspace.dao.spaces().first { it.id != spaceId } }
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription("Switch to ${other.name}").performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().selectedSpaceId == other.id } }
        assertFalse(runBlocking { container.settings.settings.first().sidebarCollapsed })
    }

    @Test fun resizingKeepsWebViewScrollHistoryAndOmniboxInput() {
        open("${server.origin}/scroll"); awaitTitle("Fixture Scroll")
        val view = webViews().single()
        compose.runOnIdle { view.scrollTo(0, 420) }
        compose.waitUntil(5_000) { view.scrollY > 0 }
        var originalScroll = 0
        compose.runOnIdle { originalScroll = view.scrollY }
        for (width in listOf(320, 480, 1000, 360)) {
            resize(width)
            assertSame(view, webViews().single())
            compose.waitUntil(10_000) {
                var restored = false
                compose.runOnIdle { restored = view.progress == 100 && view.scrollY == originalScroll }
                restored
            }
        }
        open("${server.origin}/two"); awaitTitle("Fixture Two")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { var matches = false; compose.runOnIdle { matches = view.url == "${server.origin}/scroll" }; matches }
        compose.onNodeWithContentDescription("Forward").performClick()
        compose.waitUntil(10_000) { var matches = false; compose.runOnIdle { matches = view.url == "${server.origin}/two" }; matches }
        compose.onNodeWithContentDescription("Search or enter URL").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("unfinished foldable query")
        resize(1000)
        compose.onNode(hasSetTextAction()).assertTextEquals("unfinished foldable query")
        resize(360)
        compose.onNode(hasSetTextAction()).assertTextEquals("unfinished foldable query")
        compose.onNodeWithContentDescription("Close Search").performClick()
        assertSame(view, webViews().single())
    }

    @Test fun tabSheetDismissalDoesNotNavigateAndWideSplitKeepsBothPages() {
        open("${server.origin}/one"); awaitTitle("Fixture One")
        val first = runBlocking { container.workspace.dao.tabs(spaceId).first { it.title == "Fixture One" }.id }
        val second = runBlocking { container.tabs.create(spaceId, "${server.origin}/two") }
        awaitTitle("Fixture Two")
        runBlocking { container.tabs.pair(first, second) }
        compose.waitUntil(10_000) { webViews().size == 2 }
        val views = webViews().toSet()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertIsDisplayed()
        shell("input keyevent 4")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("compact-tab-sheet").fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { container.workspace.dao.tabs(spaceId).filter { it.id in setOf(first, second) }.all { it.closedAt == null } })
        resize(1000)
        compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
        compose.onNodeWithTag("sidebar").assertIsDisplayed()
        assertEquals(views, webViews().toSet())
        resize(360)
        compose.onNodeWithTag("compact-browser-bar").assertIsDisplayed()
        assertEquals(views, webViews().toSet())
    }

    private fun awaitDisplayMode(desktop: Boolean, paneCount: Int = 1) {
        compose.waitUntil(15_000) {
            val views = webViews()
            var loaded = false
            compose.runOnIdle { loaded = views.size == paneCount && views.all { it.title == "Fixture ${if (desktop) "Desktop" else "Mobile"}" && it.progress == 100 } }
            loaded
        }
    }

    @Test fun resizePreservesTheDocumentAndDraftUntilTheNextNavigationOrReload() {
        val url = "${server.origin}/ua-resize"
        open(url)
        awaitDisplayMode(false)
        val original = webViews().single()
        enterDraft(original)
        for ((width, desktop) in listOf(1000 to true, 620 to true, 600 to true, 599 to false,
            600 to true, 620 to true, 639 to true, 800 to true)) {
            resize(width)
            awaitDisplayMode(false)
            assertSame(original, webViews().single())
            assertDraft(original)
            assertEquals(1, server.requests.count { it.path == "/ua-resize" })
            assertTrue(pageData(original).getString("ua").contains("Mobile"))
            assertNull(container.siteDisplayModes.desktopMode(url))
            if (desktop) {
                compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
                compose.onNodeWithTag("sidebar").assertIsDisplayed()
            } else compose.onNodeWithTag("compact-browser-bar").assertIsDisplayed()
        }
        // Same-document anchors are not a reason to replace a draft either.
        compose.runOnIdle { original.evaluateJavascript("document.getElementById('anchor').click()", null) }
        compose.waitUntil(10_000) { var anchor = false; compose.runOnIdle { anchor = original.url?.endsWith("#anchor-target") == true }; anchor }
        assertDraft(original)
        assertEquals(1, server.requests.count { it.path == "/ua-resize" })
        compose.runOnIdle { original.evaluateJavascript("location.href='${server.origin}/ua-resize-next'", null) }
        awaitDisplayMode(true)
        assertEquals(1, server.requests.count { it.path == "/ua-resize-next" })
        assertFalse(server.requests.last { it.path == "/ua-resize-next" }.userAgent.contains("Mobile"))
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.observeHistory().first().count { it.url == url } == 1 } }
        // The global preference still allows the user to choose mobile even on a wide screen.
        runBlocking { container.settings.update { it.copy(desktopDefault = false) } }
        awaitDisplayMode(true)
        compose.onNodeWithContentDescription("Reload").performClick()
        awaitDisplayMode(false)
        assertNull(container.siteDisplayModes.desktopMode(url))
        compose.activityRule.scenario.recreate()
        awaitDisplayMode(false)
    }

    @Test fun explicitDesktopAndMobileChoicesSurviveBothLayoutsAndRecreation() {
        val desktopUrl = "${server.origin}/ua-explicit-desktop"
        val mobileUrl = "${server.origin.replace("127.0.0.1", "localhost")}/ua-explicit-mobile"
        open(desktopUrl)
        awaitDisplayMode(false)
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Use desktop site").performClick()
        awaitDisplayMode(true)
        resize(1000); awaitDisplayMode(true)
        resize(360); awaitDisplayMode(true)
        assertEquals(true, container.siteDisplayModes.desktopMode(desktopUrl))
        resize(1000)
        open(mobileUrl)
        awaitDisplayMode(true)
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Use mobile site").performClick()
        awaitDisplayMode(false)
        resize(360); awaitDisplayMode(false)
        resize(1000); awaitDisplayMode(false)
        assertEquals(false, container.siteDisplayModes.desktopMode(mobileUrl))
        compose.activityRule.scenario.recreate()
        awaitDisplayMode(false)
        open(desktopUrl)
        awaitDisplayMode(true)
        resize(360); awaitDisplayMode(true)
    }

    @Test fun tabletEngineRequestsPhoneLayoutAndClientHintsWhenItsWindowBecomesCompact() {
        // Create the engines while the device has a tablet-sized configuration, then
        // keep those same engines when narrowing the window, as real split-screen does.
        resize(800)
        compose.activityRule.scenario.recreate()
        val url = "${server.origin}/ua-tablet-layout"
        open(url)
        awaitDisplayMode(true)
        val original = webViews().single()
        enterDraft(original)
        resize(432)
        awaitDisplayMode(true)
        assertDraft(original)
        assertEquals(1, server.requests.count { it.path == "/ua-tablet-layout" })
        open(url)
        awaitDisplayMode(false)
        assertSame(original, webViews().single())
        assertTrue(server.requests.last { it.path == "/ua-tablet-layout" }.userAgent.contains("Mobile"))
        val metrics = pageData(original)
        var cssWidth = 0f
        compose.runOnIdle { cssWidth = original.width / original.resources.displayMetrics.density }
        assertEquals(cssWidth.toDouble(), metrics.getDouble("width"), 1.0)
        assertEquals(1.0, metrics.getDouble("scale"), 0.01)
        assertTrue(metrics.getString("viewport").contains("device-width"))
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
            assertTrue(metrics.getBoolean("mobile"))
            assertEquals("?1", server.requests.last { it.path == "/ua-tablet-layout" }.mobileHint)
        }
        enterDraft(original)
        resize(800)
        awaitDisplayMode(false)
        assertDraft(original)
        open(url)
        awaitDisplayMode(true)
        assertEquals(1024.0, pageData(original).getDouble("width"), 1.0)
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
            assertFalse(pageData(original).getBoolean("mobile"))
        }
        // A manual phone-site choice applies even when a tablet keeps its wide UI.
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Use mobile site").performClick()
        awaitDisplayMode(false)
        assertEquals(false, container.siteDisplayModes.desktopMode(url))
        compose.activityRule.scenario.recreate()
        awaitDisplayMode(false)
    }

    @Test fun hiddenAndSplitPagesKeepTheirDocumentsUntilTheyNavigate() {
        open("${server.origin}/ua-hidden")
        awaitDisplayMode(false)
        val first = runBlocking { container.workspace.dao.tabs(spaceId).first { it.url == "${server.origin}/ua-hidden" }.id }
        val original = webViews().single()
        enterDraft(original)
        val second = runBlocking { container.tabs.create(spaceId, "${server.origin}/ua-visible") }
        awaitDisplayMode(false)
        val other = webViews().single()
        enterDraft(other)
        resize(1000)
        awaitDisplayMode(false)
        assertDraft(other)
        assertEquals(1, server.requests.count { it.path == "/ua-hidden" })
        compose.onNodeWithTag("sidebar-tab-$first").performScrollTo().performClick()
        awaitDisplayMode(false)
        assertSame(original, webViews().single())
        assertDraft(original)
        assertEquals(1, server.requests.count { it.path == "/ua-hidden" })
        runBlocking { container.tabs.pair(first, second) }
        awaitDisplayMode(false, paneCount = 2)
        assertDraft(original)
        assertDraft(other)
        compose.runOnIdle {
            original.evaluateJavascript("location.href='${server.origin}/ua-hidden-wide'", null)
            other.evaluateJavascript("location.href='${server.origin}/ua-visible-wide'", null)
        }
        awaitDisplayMode(true, paneCount = 2)
        enterDraft(original)
        enterDraft(other)
        resize(360)
        awaitDisplayMode(true, paneCount = 2)
        assertDraft(original)
        assertDraft(other)
        compose.runOnIdle {
            original.evaluateJavascript("location.href='${server.origin}/ua-hidden-phone'", null)
            other.evaluateJavascript("location.href='${server.origin}/ua-visible-phone'", null)
        }
        awaitDisplayMode(false, paneCount = 2)
        assertTrue(server.requests.last { it.path == "/ua-hidden-phone" }.userAgent.contains("Mobile"))
        assertTrue(server.requests.last { it.path == "/ua-visible-phone" }.userAgent.contains("Mobile"))
    }

    @Test fun bottomNavigationBackgroundMatchesTheToolbarAcrossThemesAndSpaceColors() {
        val space = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId } }
        for (theme in listOf(com.takeruf.nagi.domain.model.ThemeMode.LIGHT, com.takeruf.nagi.domain.model.ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme) } }
            compose.waitUntil(10_000) {
                var light = false
                compose.runOnIdle {
                    light = androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,
                        compose.activity.window.decorView).isAppearanceLightNavigationBars
                }
                light == (theme == com.takeruf.nagi.domain.model.ThemeMode.LIGHT)
            }
            for (color in listOf(0xFF426B5A, 0xFF477F96)) {
                runBlocking { container.spaces.edit(spaceId, space.name, space.icon, color) }
                compose.mainClock.advanceTimeBy(400)
                compose.waitForIdle()
                val bounds = compose.onNodeWithTag("compact-browser-bar").fetchSemanticsNode().boundsInRoot
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                try {
                    val directory = java.io.File(compose.activity.getExternalFilesDir(null), "compact-navigation").apply { mkdirs() }
                    val capture = java.io.File(directory, "$theme-$color.png")
                    capture.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    // Keep pixel evidence after the test runner removes its installed APK/data.
                    shell("mkdir -p /data/local/tmp/nagi-compact-navigation")
                    shell("cp ${capture.absolutePath} /data/local/tmp/nagi-compact-navigation/")
                    val x = (bounds.left + 8).toInt()
                    val toolbar = bitmap.getPixel(x, bounds.center.y.toInt())
                    assertTrue("Expected a system navigation area below the toolbar", bitmap.height - bounds.bottom > 8)
                    for (y in listOf(bounds.bottom.toInt() + 4, bitmap.height - 4)) {
                        assertEquals("Navigation background differs in $theme/$color at y=$y", toolbar, bitmap.getPixel(x, y))
                    }
                } finally { bitmap.recycle() }
                compose.runOnIdle {
                    if (android.os.Build.VERSION.SDK_INT >= 29) assertFalse(compose.activity.window.isNavigationBarContrastEnforced)
                }
            }
        }
        resize(1000)
        compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
        compose.runOnIdle {
            if (android.os.Build.VERSION.SDK_INT >= 29) assertTrue(compose.activity.window.isNavigationBarContrastEnforced)
        }
    }
}
