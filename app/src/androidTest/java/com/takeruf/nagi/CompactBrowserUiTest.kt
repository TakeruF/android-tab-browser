package com.takeruf.nagi

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Intent
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
import java.util.concurrent.atomic.AtomicReference

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

    private fun resize(widthDp: Int, heightDp: Int = 820) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(widthDp * density).toInt()}x${(heightDp * density).toInt()}")
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

    private fun capturePanel(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.getExternalFilesDir(null), "compact-panel-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun tabSheetWrapsVisibleContentAndCapsLargeCollections() {
        fun height() = compose.onNodeWithTag("compact-tab-sheet").fetchSemanticsNode().boundsInRoot.height
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("compact-tabs-button").performClick()
        val oneTab = height()
        assertTrue("Small collections should stay within thumb reach", oneTab < 540 * density)
        capturePanel("one-tab-light")
        val pinnedToggle = compose.activity.getString(R.string.ui_toggle_pinned_tabs)
        compose.onNodeWithContentDescription(pinnedToggle).performClick()
        assertTrue("Collapsing a section shrinks the sheet", height() < oneTab)
        compose.onNodeWithContentDescription(pinnedToggle).performClick()
        assertEquals(oneTab, height(), 2f)
        val favorites = compose.activity.getString(R.string.ui_favorites_section)
        compose.onNodeWithText(favorites).performClick()
        assertTrue("Expanding Favorites grows the sheet", height() > oneTab)
        compose.onNodeWithText(favorites).performClick()
        assertEquals(oneTab, height(), 2f)
        val second = runBlocking { container.tabs.create(spaceId, "about:blank") }
        compose.waitForIdle()
        assertTrue("Adding a tab grows the sheet", height() > oneTab + 40 * density)
        capturePanel("two-tabs-light")
        runBlocking { container.tabs.close(second) }
        compose.waitForIdle()
        assertEquals("Closing a tab shrinks the sheet", oneTab, height(), 2f)
        val extra = runBlocking { (1..24).map { container.tabs.create(spaceId, "about:blank") } }
        compose.waitForIdle()
        val capped = height()
        assertTrue("Large collections grow beyond the small sheet", capped > oneTab)
        assertTrue("Sheet retains its available-height cap", capped <= 820 * density * 0.85f)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_sidebar_tabs))
            .performScrollToNode(hasTestTag("sidebar-tab-${extra.last()}"))
        compose.onNodeWithTag("sidebar-tab-${extra.last()}").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_settings)).assertIsDisplayed()
        capturePanel("many-tabs-light")
        runBlocking { extra.forEach { container.tabs.close(it) } }
        compose.waitForIdle()
        assertEquals("Shrinking a scrolled list also shrinks its sheet", oneTab, height(), 2f)
        runBlocking { container.settings.update { it.copy(theme = com.takeruf.nagi.domain.model.ThemeMode.DARK, spacesEnabled = false) } }
        compose.waitForIdle()
        assertTrue("Hiding the Space switcher reduces sheet height", height() < oneTab)
        capturePanel("one-tab-dark-no-spaces")
    }

    @Test fun commandKeyboardKeepsWideBrowserChromeBehindIt() {
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        fun awaitKeyboardHidden() {
            compose.waitUntil(10_000) {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                compose.activity.hasWindowFocus() && insets != null &&
                    !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) &&
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom == 0
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        for ((width, height) in listOf(1000 to 820, 820 to 360)) {
            resize(width, height)
            runBlocking { container.settings.update { it.copy(
                theme = if (height < 600) com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            compose.waitForIdle()
            awaitKeyboardHidden()
            val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
            val sidebar = bounds("sidebar")
            val page = bounds("new-tab-page")
            val palette = bounds("customize-new-tab")
            val history = compose.onNodeWithContentDescription("History").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("url-drop-$active").performClick()
            compose.waitUntil(10_000) {
                val ime = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ime?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true &&
                    ime.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom > 100 * density
            }
            compose.onNodeWithTag("command-input").assertIsDisplayed().assertIsFocused()
            capturePanel("wide-command-keyboard-$width")
            val afterHistory = compose.onNodeWithContentDescription("History").fetchSemanticsNode().boundsInRoot
            java.io.File(compose.activity.getExternalFilesDir(null), "wide-keyboard-bounds-$width.txt").writeText(
                "sidebar=$sidebar -> ${bounds("sidebar")}\npage=$page -> ${bounds("new-tab-page")}\n" +
                    "palette=$palette -> ${bounds("customize-new-tab")}\nhistory=$history -> $afterHistory\n")
            assertEquals("Sidebar height stays fixed behind the command keyboard", sidebar.height, bounds("sidebar").height, 1f)
            assertEquals("Page height stays fixed behind the command keyboard", page.height, bounds("new-tab-page").height, 1f)
            assertEquals("Palette is not raised above the keyboard", palette.top, bounds("customize-new-tab").top, 1f)
            assertEquals("History is not raised above the keyboard", history.top, afterHistory.top, 1f)
            compose.onNodeWithTag("command-close").performClick()
            awaitKeyboardHidden()
        }
    }

    @Test fun pageKeyboardResizesPanesWithoutRaisingWideSidebar() {
        resize(1000)
        open("${server.origin}/ua-wide-keyboard-left")
        awaitDisplayMode(true)
        val first = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        fun keyboardVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        for (split in listOf(false, true)) {
            if (split) {
                val second = runBlocking { container.tabs.create(spaceId, "${server.origin}/ua-wide-keyboard-right") }
                awaitDisplayMode(true)
                runBlocking {
                    container.tabs.pair(first, second)
                    container.tabs.select(second)
                    container.settings.update { it.copy(theme = com.takeruf.nagi.domain.model.ThemeMode.DARK) }
                }
                awaitDisplayMode(true, 2)
            }
            val views = webViews()
            views.forEach(::enterDraft)
            val heights = views.associateWith { it.height }
            val sidebarHeight = compose.onNodeWithTag("sidebar").fetchSemanticsNode().boundsInRoot.height
            val editor = views.last()
            compose.runOnIdle {
                editor.requestFocus()
                editor.evaluateJavascript("document.getElementById('draft').focus()", null)
                compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(editor, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            compose.waitUntil(10_000) { keyboardVisible() && views.all { it.height < heights.getValue(it) } }
            compose.waitForIdle()
            assertEquals("The wide sidebar stays at full height during page input", sidebarHeight,
                compose.onNodeWithTag("sidebar").fetchSemanticsNode().boundsInRoot.height, 1f)
            assertEquals(views.toSet(), webViews().toSet())
            views.forEach(::assertDraft)
            capturePanel("wide-page-keyboard-${if (split) "split-dark" else "single-light"}")
            shell("input keyevent 4")
            compose.waitUntil(10_000) { !keyboardVisible() && views.all { it.height == heights.getValue(it) } }
            views.forEach(::assertDraft)
        }
    }

    @Test fun keyboardHidesCompactFooterAndBottomAddressWithoutReloadingPage() {
        open("${server.origin}/ua-keyboard"); awaitDisplayMode(false)
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val view = webViews().single()
        enterDraft(view)
        fun keyboardVisible() = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        fun awaitFooter() {
            // IME visibility switches before its hide animation finishes applying insets.
            compose.waitUntil(10_000) { !keyboardVisible() && compose.activity.hasWindowFocus() &&
                androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom == 0 && runCatching {
                compose.onNodeWithTag("compact-browser-bar").assertIsDisplayed()
                compose.onNodeWithTag("browser-address-bar-$active").assertIsDisplayed()
            }.isSuccess }
        }
        for (bottom in listOf(false, true)) {
            runBlocking { container.settings.update { it.copy(bottomSearchBar = bottom,
                theme = if (bottom) com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            compose.waitForIdle()
            // Address navigation may have closed the editor before the native IME finished hiding.
            awaitFooter()
            val fullHeight = view.height
            compose.runOnIdle {
                view.requestFocus()
                view.evaluateJavascript("document.getElementById('draft').focus()", null)
                compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                    .showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            compose.waitUntil(10_000) { keyboardVisible() }
            // The platform visibility callback precedes Compose's inset-state update.
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("compact-browser-bar").fetchSemanticsNodes().isEmpty() }
            compose.waitForIdle()
            compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
            if (bottom) compose.onNodeWithTag("browser-address-bar-$active").assertDoesNotExist()
            else compose.onNodeWithTag("browser-address-bar-$active").assertIsDisplayed()
            // Native View layout can finish after Compose has removed the footer.
            compose.waitUntil(5_000) { view.height < fullHeight }
            assertTrue("Page still resizes above the keyboard: before=$fullHeight after=${view.height}", view.height < fullHeight)
            assertSame(view, webViews().single())
            assertDraft(view)
            capturePanel("page-keyboard-${if (bottom) "bottom-dark" else "top-light"}")
            shell("input keyevent 4")
            awaitFooter()
            compose.onNodeWithTag("url-drop-$active").performClick()
            compose.waitUntil(10_000) { keyboardVisible() }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("compact-browser-bar").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("command-input").assertIsDisplayed().assertIsFocused()
            compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
            if (bottom) compose.onNodeWithTag("browser-address-bar-$active").assertDoesNotExist()
            capturePanel("command-keyboard-${if (bottom) "bottom-dark" else "top-light"}")
            compose.onNodeWithTag("command-close").performClick()
            awaitFooter()
            assertSame(view, webViews().single())
            assertDraft(view)
        }
    }

    @Test fun bottomChromeToolbarUsesOneRowAndKeepsNavigationAndPageActions() {
        val url = "${server.origin}/ua-chrome-toolbar"
        open(url); awaitDisplayMode(false)
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val view = webViews().single()
        enterDraft(view)
        runBlocking { container.settings.update { it.copy(bottomSearchBar = true) } }
        fun awaitKeyboardHidden() {
            compose.waitUntil(10_000) {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                compose.activity.hasWindowFocus() && insets != null &&
                    !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) &&
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom == 0
            }
        }
        for (width in listOf(320, 360, 411)) {
            resize(width)
            runBlocking { container.settings.update { it.copy(theme = if (width == 360)
                com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            compose.waitForIdle()
            assertEquals(1, compose.onAllNodesWithTag("compact-browser-bar").fetchSemanticsNodes().size)
            compose.onNodeWithTag("compact-browser-bar").assertHeightIsEqualTo(56.dp)
            val address = compose.onNodeWithTag("url-drop-$active").fetchSemanticsNode().boundsInRoot
            val home = compose.onNodeWithTag("compact-home-button").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            val plus = compose.onNodeWithTag("compact-new-tab-button").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            val tabs = compose.onNodeWithTag("compact-tabs-button").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithTag("page-menu-$active").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).fetchSemanticsNode().boundsInRoot
            assertTrue(home.right <= address.left + 1f && address.right <= plus.left + 1f && plus.right <= tabs.left + 1f && tabs.right <= menu.left + 1f)
            assertTrue("URL remains usable at the narrowest width", address.width >= 100 * compose.activity.resources.displayMetrics.density)
            assertEquals(address.center.y, plus.center.y, 1f)
            assertEquals(plus.center.y, tabs.center.y, 1f)
            assertEquals(tabs.center.y, menu.center.y, 1f)
            val page = compose.onNodeWithTag("browser-content-$active").fetchSemanticsNode().boundsInRoot
            val row = compose.onNodeWithTag("browser-address-bar-$active").fetchSemanticsNode().boundsInRoot
            assertTrue("Single toolbar follows the page", row.top >= page.bottom - 1f)
            compose.onNodeWithText(server.origin.removePrefix("http://")).assertIsDisplayed()
            for (label in listOf(R.string.ui_back, R.string.ui_forward)) {
                compose.onAllNodes(hasContentDescription(compose.activity.getString(label)) and
                    hasAnyAncestor(hasTestTag("compact-browser-bar"))).assertCountEquals(0)
            }
            compose.onNodeWithTag("address-copy-url:$active").assertDoesNotExist()
            compose.onNodeWithTag("address-share-url:$active").assertDoesNotExist()
            assertSame(view, webViews().single())
            assertDraft(view)
            capturePanel("chrome-bottom-$width")
        }
        assertEquals(1, server.requests.count { it.path == "/ua-chrome-toolbar" })
        compose.onNodeWithTag("url-drop-$active").performClick()
        compose.onNodeWithTag("command-input").assertTextEquals(url)
        compose.onNodeWithTag("command-close").performClick()
        awaitKeyboardHidden()
        compose.onNodeWithTag("page-menu-$active").performClick()
        compose.onNodeWithTag("menu-share-url:$active").assertIsDisplayed()
        compose.onNodeWithTag("menu-copy-url:$active").assertIsDisplayed()
        compose.onNodeWithTag("site-ad-blocking").assertIsDisplayed()
        compose.onNodeWithTag("menu-forward").assertIsNotEnabled()
        shell("input keyevent 4")
        open("${server.origin}/two"); awaitTitle("Fixture Two")
        awaitKeyboardHidden()
        shell("input keyevent 4")
        awaitDisplayMode(false)
        compose.onNodeWithTag("page-menu-$active").performClick()
        compose.onNodeWithTag("menu-forward").assertIsEnabled().performClick()
        awaitTitle("Fixture Two")
        assertSame(view, webViews().single())
        compose.onNodeWithTag("compact-new-tab-button").performClick()
        compose.onNodeWithTag("command-input").assertIsFocused()
        compose.onNodeWithTag("command-close").performClick()
        awaitKeyboardHidden()
        assertEquals("2", compose.onNodeWithTag("compact-tabs-button").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription])
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("sidebar-tab-$active").performClick()
        compose.onNodeWithTag("compact-tab-sheet").assertDoesNotExist()
        awaitUrl("${server.origin}/two")
        assertSame(view, webViews().single())
    }

    @Test fun phoneHomeAndMenuKeepTabIdentityAndSimplifyTabRows() {
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val other = runBlocking { container.tabs.create(spaceId, "${server.origin}/two", select = false) }
        val ids = runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }.map { it.id }.toSet() }
        fun awaitNoKeyboard() {
            compose.waitUntil(10_000) {
                val insets = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                compose.activity.hasWindowFocus() && insets != null &&
                    !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) &&
                    insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom == 0
            }
            compose.waitForIdle()
        }
        for (bottom in listOf(false, true)) {
            runBlocking { container.settings.update { it.copy(bottomSearchBar = bottom,
                theme = if (bottom) com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            open("${server.origin}/one"); awaitTitle("Fixture One"); awaitNoKeyboard()
            val view = webViews().single()
            compose.onNodeWithTag("page-menu-$active").performClick()
            compose.onNodeWithTag("compact-page-menu").assertIsDisplayed()
            compose.onNodeWithTag("menu-reload").assertIsEnabled()
            compose.onNodeWithTag("menu-pin").assertDoesNotExist()
            compose.onNodeWithTag("menu-copy-url:$active").assertIsEnabled().performClick()
            awaitNoKeyboard()
            compose.runOnIdle {
                assertEquals("${server.origin}/one", compose.activity.getSystemService(ClipboardManager::class.java)
                    .primaryClip?.getItemAt(0)?.text?.toString())
            }
            assertSame(view, webViews().single())
            val sharedIntent = AtomicReference<Intent?>()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_CHOOSER) return null
                    sharedIntent.set(intent)
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                compose.onNodeWithTag("page-menu-$active").performClick()
                compose.onNodeWithTag("menu-share-url:$active").assertIsEnabled().performClick()
                compose.waitUntil(5_000) { sharedIntent.get() != null }
                @Suppress("DEPRECATION")
                val send = sharedIntent.get()!!.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("text/plain", send.type)
                assertEquals("${server.origin}/one", send.getStringExtra(Intent.EXTRA_TEXT))
                assertEquals("Fixture One", send.getStringExtra(Intent.EXTRA_SUBJECT))
            } finally { instrumentation.removeMonitor(monitor) }
            awaitNoKeyboard()
            assertSame(view, webViews().single())
            compose.onNodeWithTag("page-menu-$active").performClick()
            capturePanel("phone-menu-${if (bottom) "bottom-dark" else "top-light"}")
            compose.onNodeWithText(compose.activity.getString(R.string.ui_settings)).performScrollTo().performClick()
            compose.returnToBrowserFromSettings()
            assertSame(view, webViews().single())
            compose.onNodeWithTag("page-menu-$active").performClick()
            compose.onNodeWithText(compose.activity.getString(R.string.ui_history)).performScrollTo().performClick()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back_to_browser)).performClick()
            awaitUrl("${server.origin}/one")
            assertSame(view, webViews().single())
            compose.onNodeWithTag("compact-tabs-button").performClick()
            val otherTitle = runBlocking { container.workspace.dao.tab(other)!!.title }
            for ((id, title) in listOf(active to "Fixture One", other to otherTitle)) {
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_actions_for_1_s, title)).assertDoesNotExist()
                compose.onNodeWithTag("sidebar-tab-$id").performScrollTo()
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_close_1_s, title))
                    .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            }
            capturePanel("phone-tabs-${if (bottom) "bottom-dark" else "top-light"}")
            compose.onNodeWithTag("sidebar-tab-$active").performClick()
            compose.onNodeWithTag("compact-home-button").assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.waitUntil(10_000) { runBlocking { container.workspace.dao.tab(active)?.url == "about:blank" } }
            compose.onNodeWithTag("new-tab-page").assertIsDisplayed()
            assertEquals(ids, runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }.map { it.id }.toSet() })
            assertEquals(active, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
            capturePanel("phone-home-${if (bottom) "bottom-dark" else "top-light"}")
            compose.onNodeWithTag("page-menu-$active").performClick()
            compose.onNodeWithTag("menu-copy-url:$active").assertIsNotEnabled()
            compose.onNodeWithTag("menu-share-url:$active").assertIsNotEnabled()
            shell("input keyevent 4")
            awaitNoKeyboard()
            shell("input keyevent 4")
            awaitUrl("${server.origin}/one")
            assertSame(view, webViews().single())
        }
    }

    @Test fun phoneMenuKeepsChromeWidthAndFiveFixedBottomActions() {
        open("${server.origin}/ua-chrome-popup"); awaitDisplayMode(false)
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val view = webViews().single()
        enterDraft(view)
        for ((width, height, bottom) in listOf(Triple(320, 820, false), Triple(411, 820, true), Triple(360, 540, true))) {
            resize(width, height)
            runBlocking { container.settings.update { it.copy(bottomSearchBar = bottom,
                theme = if (bottom) com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            compose.waitForIdle()
            val bar = compose.onNodeWithTag("compact-browser-bar").fetchSemanticsNode().boundsInRoot
            val trigger = compose.onNodeWithTag("page-menu-$active").assertIsDisplayed()
            assertTrue(trigger.fetchSemanticsNode().boundsInRoot.top >= bar.top - 1f)
            trigger.performClick()
            compose.waitUntil(5_000) { !compose.activity.hasWindowFocus() }
            val menu = compose.onNodeWithTag("compact-page-menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val density = compose.activity.resources.displayMetrics.density
            val menuCoordinates = compose.onNodeWithTag("compact-page-menu").fetchSemanticsNode().layoutInfo.coordinates
            val barCoordinates = compose.onNodeWithTag("compact-browser-bar").fetchSemanticsNode().layoutInfo.coordinates
            val menuBottom = menuCoordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero).y + menuCoordinates.size.height
            val barTop = barCoordinates.localToScreen(androidx.compose.ui.geometry.Offset.Zero).y
            assertEquals("Popup overlaps the actual bar by 12dp", 12 * density, menuBottom - barTop, 2f)
            assertTrue("Chrome-style menu leaves a visible strip of the page on the left", menu.width <= width * density * 0.74f)
            assertTrue("Menu retains room for readable rows", menu.width >= width * density * 0.65f)
            for (tag in listOf("compact-page-navigation", "compact-page-menu-close", "compact-page-menu-handle"))
                compose.onNodeWithTag(tag).assertDoesNotExist()
            val footer = compose.onNodeWithTag("compact-page-actions").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Common actions stay next to the popup bottom", menu.bottom - footer.bottom <= 16 * density + 1f)
            compose.onNodeWithTag("menu-pin").assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.ui_copy_link)).assertDoesNotExist()
            compose.onNodeWithText(compose.activity.getString(R.string.ui_share_link)).assertDoesNotExist()
            val actionTags = listOf("menu-back", "menu-forward", "menu-copy-url:$active", "menu-share-url:$active", "menu-reload")
            val buttonSize = minOf(48.dp, (footer.width / density / 5).dp)
            val buttons = actionTags.map { tag ->
                compose.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsEqualTo(buttonSize).assertHeightIsEqualTo(buttonSize)
                    .fetchSemanticsNode().boundsInRoot
            }
            assertTrue("Five buttons fit in one horizontal row", buttons.zipWithNext().all { (left, right) ->
                left.right <= right.left + 1f && kotlin.math.abs(left.top - right.top) <= 1f
            })
            capturePanel("chrome-popup-$width-${if (bottom) "bottom-dark" else "top-light"}")
            compose.onNodeWithText(compose.activity.getString(R.string.ui_settings)).performScrollTo().assertIsDisplayed()
            assertEquals(footer, compose.onNodeWithTag("compact-page-actions").fetchSemanticsNode().boundsInRoot)
            for (tag in actionTags)
                compose.onNodeWithTag(tag).assertIsDisplayed()
            capturePanel("chrome-popup-scrolled-$width")
            shell("input keyevent 4")
            compose.waitUntil(5_000) { compose.activity.hasWindowFocus() &&
                compose.onAllNodesWithTag("compact-page-menu").fetchSemanticsNodes().isEmpty() }
            assertSame(view, webViews().single()); assertDraft(view)
        }
    }

    @Test fun phonePopupClosesFromTriggerOutsideTapAndBack() {
        open("${server.origin}/ua-close-popup"); awaitDisplayMode(false)
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val view = webViews().single()
        enterDraft(view)
        val ids = runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }.map { it.id } }
        fun awaitClosed() {
            compose.waitUntil(5_000) { compose.activity.hasWindowFocus() &&
                compose.onAllNodesWithTag("compact-page-menu").fetchSemanticsNodes().isEmpty() }
            assertSame(view, webViews().single()); assertDraft(view)
            assertEquals(active, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId })
            assertEquals(ids, runBlocking { container.workspace.dao.tabs(spaceId).filter { it.closedAt == null }.map { it.id } })
        }
        fun openPopup() {
            compose.onNodeWithTag("page-menu-$active").performClick()
            compose.waitUntil(5_000) { !compose.activity.hasWindowFocus() &&
                compose.onAllNodesWithTag("compact-page-menu").fetchSemanticsNodes().isNotEmpty() }
        }
        for ((width, bottom) in listOf(320 to false, 411 to true)) {
            resize(width)
            runBlocking { container.settings.update { it.copy(bottomSearchBar = bottom,
                theme = if (bottom) com.takeruf.nagi.domain.model.ThemeMode.DARK else com.takeruf.nagi.domain.model.ThemeMode.LIGHT) } }
            compose.waitForIdle()
            openPopup()
            val trigger = compose.onNodeWithTag("page-menu-$active").fetchSemanticsNode().boundsInRoot.center
            shell("input tap ${trigger.x.toInt()} ${trigger.y.toInt()}")
            awaitClosed()
            openPopup()
            // Route outside taps through WindowManager, rather than directly to the page's Compose root.
            val pageBounds = compose.onNodeWithTag("browser-content-$active").fetchSemanticsNode().boundsInRoot
            shell("input tap ${(pageBounds.left + 8).toInt()} ${(pageBounds.top + 50).toInt()}")
            awaitClosed()
            openPopup()
            shell("input keyevent 4")
            awaitClosed()
        }
        assertEquals(1, server.requests.count { it.path == "/ua-close-popup" })
    }

    @Test fun bottomSearchSettingMovesAddressAndInputAndPreservesPageAcrossWidths() {
        open("${server.origin}/scroll"); awaitTitle("Fixture Scroll")
        val active = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val view = webViews().single()
        fun assertAddressPosition(bottom: Boolean) {
            val address = compose.onNodeWithTag("browser-address-bar-$active").fetchSemanticsNode().boundsInRoot
            val content = compose.onNodeWithTag("browser-content-$active").fetchSemanticsNode().boundsInRoot
            if (bottom) assertTrue("Address bar below page", address.top >= content.bottom - 1f)
            else assertTrue("Address bar above page", address.bottom <= content.top + 1f)
        }
        assertAddressPosition(false)
        compose.onNodeWithTag("compact-search-button").assertIsDisplayed()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_settings)).performClick()
        val label = compose.activity.getString(R.string.ui_bottom_search_bar)
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().bottomSearchBar } }
        compose.returnToBrowserFromSettings()
        compose.waitForIdle()
        assertAddressPosition(true)
        compose.onNodeWithTag("compact-search-button").assertDoesNotExist()
        assertSame(view, webViews().single())
        compose.runOnIdle { view.scrollTo(0, 420) }
        compose.waitUntil(5_000) { view.scrollY > 0 }
        var scroll = 0
        compose.runOnIdle { scroll = view.scrollY }
        for (width in listOf(1000, 360)) {
            resize(width)
            assertAddressPosition(width < 600)
            assertSame(view, webViews().single())
            assertEquals(scroll, view.scrollY)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("browser-address-bar-$active").fetchSemanticsNodes().isNotEmpty() }
        assertAddressPosition(true)
        compose.onNodeWithTag("url-drop-$active").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("command-input").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onNodeWithTag("command-phone-screen").assertIsDisplayed()
        val input = compose.onNodeWithTag("command-input").fetchSemanticsNode().boundsInRoot
        val suggestions = compose.onNodeWithTag("command-suggestions").fetchSemanticsNode().boundsInRoot
        assertTrue("Suggestions follow the top input in the phone search screen", suggestions.top >= input.bottom)
        compose.onNodeWithTag("command-input").performTextReplacement("${server.origin}/two")
        resize(1000)
        compose.onNodeWithTag("command-input").assertTextEquals("${server.origin}/two")
        resize(360)
        compose.onNodeWithTag("command-input").assertTextEquals("${server.origin}/two")
        compose.onNodeWithTag("command-input").performKeyInput { pressKey(Key.Enter) }
        awaitTitle("Fixture Two")
        assertAddressPosition(true)
        runBlocking { container.settings.update { it.copy(bottomSearchBar = false) } }
        compose.waitForIdle()
        assertAddressPosition(false)
        compose.onNodeWithTag("compact-search-button").assertIsDisplayed()
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
            compose.onNodeWithContentDescription("Page menu").performClick()
            if (width < 600) {
                compose.onNodeWithTag("menu-forward").assertExists()
                compose.onNodeWithTag("menu-reload").assertIsDisplayed()
            } else compose.onNodeWithText("Forward").assertDoesNotExist()
            shell("input keyevent 4"); compose.waitForIdle()
            compose.waitUntil(10_000) {
                var restored = false
                compose.runOnIdle { restored = view.progress == 100 && view.scrollY == originalScroll }
                restored
            }
        }
        open("${server.origin}/two"); awaitTitle("Fixture Two")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(10_000) { var matches = false; compose.runOnIdle { matches = view.url == "${server.origin}/scroll" }; matches }
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithTag("menu-forward").performClick()
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

    @Test fun savedSplitUsesOneSelectedPageInCompactAndRestoresBothLivePagesWhenWide() {
        open("${server.origin}/ua-compact-left"); awaitDisplayMode(false)
        val first = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val left = webViews().single()
        enterDraft(left)
        val second = runBlocking { container.tabs.create(spaceId, "${server.origin}/ua-compact-right") }
        awaitUrl("${server.origin}/ua-compact-right")
        awaitDisplayMode(false)
        val right = webViews().single()
        enterDraft(right)
        runBlocking { container.tabs.pair(first, second); container.tabs.select(second) }
        compose.waitForIdle()
        assertSame(right, webViews().single())
        fun assertPairSaved() {
            assertEquals(second, runBlocking { container.workspace.dao.tab(first)!!.splitRightTabId })
            assertTrue(runBlocking { container.workspace.dao.tabs(spaceId).filter { it.id in setOf(first, second) }.all { it.closedAt == null } })
        }
        fun screenshot(name: String) {
            compose.waitForIdle()
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(compose.activity.getExternalFilesDir(null), "compact-single-$name.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("New split view").assertDoesNotExist()
        compose.onNodeWithText("Swap panes").assertDoesNotExist()
        compose.onNodeWithText("Close split view").assertDoesNotExist()
        compose.onNodeWithTag("menu-forward").assertExists()
        shell("input keyevent 4"); compose.waitForIdle()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("sidebar-split-group").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-tab-$first").assertExists()
        compose.onNodeWithTag("sidebar-tab-$second").onChild().assertIsSelected()
        screenshot("tabs-light")
        val source = compose.onNodeWithTag("sidebar-tab-$first")
        val from = source.fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithTag("sidebar-tab-$second").fetchSemanticsNode().boundsInRoot.center - from.topLeft
        source.performTouchInput { down(center); advanceEventTime(650); moveTo(end, delayMillis = 100) }
        compose.onNodeWithTag("sidebar-split-drop-$second").assertDoesNotExist()
        compose.onNodeWithTag("sidebar-replace-drop-$second").assertDoesNotExist()
        source.performTouchInput { cancel() }
        assertPairSaved()
        // A compact tab menu cannot create a pair, including via accessibility actions.
        val actions = compose.onNodeWithTag("sidebar-tab-$first").onChild().fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle {
            assertTrue(actions.first { it.label == compose.activity.getString(R.string.ui_show_actions) }.action())
        }
        compose.onNodeWithText("Open in right pane").assertDoesNotExist()
        shell("input keyevent 4"); compose.waitForIdle()
        shell("input keyevent 4")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("compact-tab-sheet").fetchSemanticsNodes().isEmpty() }
        assertPairSaved()
        for ((width, theme) in listOf(1000 to com.takeruf.nagi.domain.model.ThemeMode.LIGHT,
            599 to com.takeruf.nagi.domain.model.ThemeMode.DARK, 600 to com.takeruf.nagi.domain.model.ThemeMode.DARK,
            360 to com.takeruf.nagi.domain.model.ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme) } }
            resize(width)
            compose.waitUntil(10_000) { webViews().size == if (width < 600) 1 else 2 }
            if (width < 600) {
                assertSame(right, webViews().single())
                compose.onNodeWithContentDescription("Resize split view").assertDoesNotExist()
            } else {
                assertEquals(setOf(left, right), webViews().toSet())
                compose.onNodeWithContentDescription("Resize split view").assertIsDisplayed()
                compose.onNodeWithTag("split-title-$second").assertIsSelected()
                compose.onNode(hasContentDescription("Page menu") and
                    hasAnyAncestor(hasTestTag("browser-pane-right-$second"))).performClick()
                compose.onNodeWithText("Forward").performScrollTo().assertIsDisplayed()
                shell("input keyevent 4"); compose.waitForIdle()
            }
            assertPairSaved()
            assertDraft(left); assertDraft(right)
            screenshot("${width}-${theme.name.lowercase()}")
        }
        // Each saved member is independently selectable without detaching the pair.
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("sidebar-tab-$first").performClick()
        compose.waitUntil(10_000) { webViews().singleOrNull() === left }
        assertDraft(left); assertPairSaved()
        // Keyboard command suggestions use the same compact policy as the menus.
        compose.onNodeWithTag("compact-search-button").performClick()
        compose.onNodeWithTag("command-input").performTextReplacement(">")
        compose.onNodeWithTag("suggestion:command:SPLIT").assertDoesNotExist()
        compose.onNodeWithTag("suggestion:command:CLOSE_SPLIT").assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_close_1_s,
            compose.activity.getString(R.string.ui_search))).performClick()
        compose.onNodeWithTag("command-input").assertDoesNotExist()
        resize(1000)
        compose.waitUntil(10_000) { webViews().size == 2 }
        compose.onNodeWithTag("split-title-$first").assertIsSelected()
        assertDraft(left); assertDraft(right)
        assertEquals(1, server.requests.count { it.path == "/ua-compact-left" })
        assertEquals(1, server.requests.count { it.path == "/ua-compact-right" })
        resize(360)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { webViews().size == 1 }
        assertPairSaved()
        resize(1000)
        compose.waitUntil(15_000) { webViews().size == 2 }
        assertPairSaved()
    }

    private fun awaitUrl(url: String) {
        compose.waitUntil(15_000) {
            val views = webViews()
            var loaded = false
            compose.runOnIdle { loaded = views.singleOrNull()?.let { it.url == url && it.progress == 100 } == true }
            loaded
        }
    }

    private fun awaitDisplayMode(desktop: Boolean, paneCount: Int = 1) {
        compose.waitUntil(15_000) {
            val views = webViews()
            var loaded = false
            compose.runOnIdle { loaded = views.size == paneCount && views.all { it.title == "Fixture ${if (desktop) "Desktop" else "Mobile"}" && it.progress == 100 } }
            loaded
        }
    }

    @Test fun phoneLandscapeKeepsMobileSitesAndExplicitDesktopChoice() {
        val url = "${server.origin}/ua-phone-landscape"
        open(url)
        awaitDisplayMode(false)
        val original = webViews().single()
        enterDraft(original)
        resize(820, 360)
        assertTrue(compose.activity.resources.configuration.smallestScreenWidthDp < 600)
        // Browser chrome may expand, independently of the site's default identity.
        compose.onNodeWithTag("compact-browser-bar").assertDoesNotExist()
        awaitDisplayMode(false)
        assertSame(original, webViews().single())
        assertDraft(original)
        assertEquals(1, server.requests.count { it.path == "/ua-phone-landscape" })
        open(url)
        awaitDisplayMode(false)
        assertSame(original, webViews().single())
        assertTrue(server.requests.last { it.path == "/ua-phone-landscape" }.userAgent.contains("Mobile"))
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.USER_AGENT_METADATA)) {
            assertTrue(pageData(original).getBoolean("mobile"))
            assertEquals("?1", server.requests.last { it.path == "/ua-phone-landscape" }.mobileHint)
        }
        capturePanel("phone-landscape-mobile")
        compose.activityRule.scenario.recreate()
        awaitDisplayMode(false)
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Use desktop site").performClick()
        awaitDisplayMode(true)
        assertEquals(true, container.siteDisplayModes.desktopMode(url))
        resize(360)
        awaitDisplayMode(true)
        resize(820, 360)
        awaitDisplayMode(true)
        // Choosing desktop for one host must not affect other hosts on the phone.
        val other = "${server.origin.replace("127.0.0.1", "localhost")}/ua-phone-other"
        open(other)
        awaitDisplayMode(false)
        assertNull(container.siteDisplayModes.desktopMode(other))
        compose.activityRule.scenario.recreate()
        awaitDisplayMode(false)
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
        val first = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        val original = webViews().single()
        enterDraft(original)
        val second = runBlocking { container.tabs.create(spaceId, "${server.origin}/ua-visible") }
        awaitUrl("${server.origin}/ua-visible")
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
        awaitDisplayMode(true)
        assertDraft(original)
        assertDraft(other)
        compose.runOnIdle {
            original.evaluateJavascript("location.href='${server.origin}/ua-hidden-phone'", null)
            other.evaluateJavascript("location.href='${server.origin}/ua-visible-phone'", null)
        }
        awaitUrl("${server.origin}/ua-hidden-phone")
        awaitDisplayMode(false)
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithTag("sidebar-tab-$second").performClick()
        awaitUrl("${server.origin}/ua-visible-phone")
        awaitDisplayMode(false)
        assertSame(other, webViews().single())
        assertTrue(server.requests.last { it.path == "/ua-hidden-phone" }.userAgent.contains("Mobile"))
        assertTrue(server.requests.last { it.path == "/ua-visible-phone" }.userAgent.contains("Mobile"))
    }

    @Test fun bottomNavigationBackgroundMatchesTheToolbarAcrossThemesAndSpaceColors() {
        val space = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId } }
        for (bottom in listOf(false, true)) for (theme in listOf(com.takeruf.nagi.domain.model.ThemeMode.LIGHT, com.takeruf.nagi.domain.model.ThemeMode.DARK)) {
            runBlocking { container.settings.update { it.copy(theme = theme, bottomSearchBar = bottom) } }
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
                    val capture = java.io.File(directory, "$theme-$bottom-$color.png")
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
