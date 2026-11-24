package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
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
import org.junit.*
import org.junit.Assert.*
import java.io.File

class SettingsNavigationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var leftId: String
    private lateinit var rightId: String
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        compose.waitForIdle()
    }

    private fun visibleWebViews(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown && it.width > 0 } }
        return result
    }

    private fun js(view: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }

    private fun openSettings() {
        if (compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("compact-tabs-button").performClick()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "settings-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, theme = ThemeMode.LIGHT) }
            container.spaces.create("Settings QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            leftId = container.tabs.create(spaceId, "${server.origin}/ua-settings-left")
            rightId = container.tabs.create(spaceId, "${server.origin}/ua-settings-right", select = false)
            container.tabs.pair(leftId, rightId)
        }
        resize(1000)
        compose.waitUntil(20_000) {
            val views = visibleWebViews()
            views.size == 2 && views.all { js(it, "document.readyState") == "\"complete\"" }
        }
    }

    @After fun cleanup() {
        runBlocking {
            if (::spaceId.isInitialized) container.spaces.delete(spaceId)
            if (::original.isInitialized) container.settings.update { original }
        }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    @Test fun adaptiveDarkModeSettingAndSiteMenuUpdateLiveSplitAndSurviveRecreation() {
        Assume.assumeTrue(com.takeruf.nagi.browser.engine.PageDarkening.supported)
        fun menu() { compose.onAllNodesWithContentDescription("Page menu").onFirst().performClick() }
        fun closeMenu() { shell("input keyevent 4"); compose.waitForIdle() }
        fun awaitCanvas(view: WebView, dark: Boolean) {
            val point = IntArray(2)
            compose.runOnIdle {
                view.getLocationOnScreen(point)
                point[0] += view.width * 3 / 4; point[1] += view.height / 2
            }
            compose.waitUntil(7_000) {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                val pixel = try { bitmap.getPixel(point[0], point[1]) } finally { bitmap.recycle() }
                if (dark) android.graphics.Color.red(pixel) < 100 else pixel == android.graphics.Color.WHITE
            }
        }
        val before = visibleWebViews()
        before.forEach { js(it, "document.documentElement.style.background='#fff';document.body.style.background='#fff';window.darkModeToken='live';document.getElementById('draft').value='unfinished 日本語';true") }
        menu()
        compose.onNodeWithTag("site-dark-mode").assertDoesNotExist()
        closeMenu()
        openSettings()
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-adaptive-dark-mode"))
        compose.onNodeWithTag("settings-adaptive-dark-mode").assertIsOff().performClick()
        compose.onNodeWithTag("settings-adaptive-dark-mode").assertIsOn()
        capture("adaptive-setting-light")
        compose.returnToBrowserFromSettings()
        before.forEach { awaitCanvas(it, false) }
        menu()
        compose.onNodeWithTag("site-dark-mode").assertDoesNotExist() // Enabled globally, light app theme.
        closeMenu()
        openSettings()
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-theme-select"))
        compose.onNodeWithTag("settings-theme-select").performClick()
        compose.onNodeWithTag("settings-theme-dark").performClick()
        capture("adaptive-setting-dark")
        compose.returnToBrowserFromSettings()
        before.forEach { awaitCanvas(it, true) }
        // Different host in the second pane proves that the exception is site-specific.
        val right = before.last()
        val otherUrl = server.origin.replace("127.0.0.1", "localhost") + "/ua-settings-right"
        compose.runOnIdle { right.loadUrl(otherUrl) }
        compose.waitUntil(15_000) { js(right, "location.href==='${otherUrl}' && document.readyState==='complete'") == "true" }
        js(right, "document.documentElement.style.background='#fff';document.body.style.background='#fff';window.darkModeToken='right';document.getElementById('draft').value='right draft';true")
        awaitCanvas(right, true)
        menu()
        compose.onNodeWithTag("site-dark-mode").assertTextContains("Turn off adaptive dark mode for this site")
        capture("adaptive-site-menu-tablet")
        compose.onNodeWithTag("site-dark-mode").performClick()
        compose.waitUntil(5_000) { runBlocking { "127.0.0.1" in container.settings.settings.first().darkModeExcludedHosts } }
        awaitCanvas(before.first(), false)
        awaitCanvas(right, true)
        assertTrue(before.all { old -> visibleWebViews().any { it === old } })
        assertEquals("\"live\"", js(before.first(), "darkModeToken"))
        assertEquals("\"unfinished 日本語\"", js(before.first(), "document.getElementById('draft').value"))
        assertEquals("\"right\"", js(right, "darkModeToken"))
        assertEquals(1, server.requests.count { it.path == "/ua-settings-left" })
        capture("adaptive-split-site-off")
        resize(411)
        menu()
        compose.onNodeWithTag("site-dark-mode").assertTextContains("Turn on adaptive dark mode for this site")
        capture("adaptive-site-menu-phone")
        compose.onNodeWithTag("site-dark-mode").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().darkModeExcludedHosts.isEmpty() } }
        awaitCanvas(visibleWebViews().first(), true)
        openSettings()
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-adaptive-dark-mode"))
        compose.onNodeWithTag("settings-adaptive-dark-mode").assertIsOn()
        capture("adaptive-setting-phone-dark")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-adaptive-dark-mode"))
        compose.onNodeWithTag("settings-adaptive-dark-mode").assertIsOn().performClick()
        compose.returnToBrowserFromSettings()
        menu()
        compose.onNodeWithTag("site-dark-mode").assertDoesNotExist()
        closeMenu()
    }

    @Test fun expandedCategoriesPreserveSettingsAndLiveSplitOnReturn() {
        val before = visibleWebViews()
        before.forEach { js(it, "window.settingsToken='live';document.getElementById('draft').value='unfinished 日本語';true") }
        val sidebar = runBlocking { container.settings.settings.first().sidebarWidth }
        openSettings()
        compose.onNodeWithTag("sidebar").assertDoesNotExist()
        compose.onNodeWithTag("settings-categories").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-general").assertIsSelected()
        compose.onNodeWithText("Use Spaces").performClick()
        compose.onNodeWithTag("spaces-confirmation").assertIsDisplayed()
        assertTrue(runBlocking { container.settings.settings.first().spacesEnabled })
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("spaces-confirmation").assertDoesNotExist()
        capture("wide-general-light")
        compose.selectSettingsCategory("search")
        compose.onNodeWithText("Default search engine").assertIsDisplayed()
        compose.onNodeWithTag("customize-search-engines").performClick()
        compose.onNodeWithTag("search-engines-list").assertIsDisplayed()
        compose.onNodeWithTag("settings-categories").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-search").assertIsSelected()
        compose.onNodeWithTag("search-engines-breadcrumb").assertIsDisplayed()
        val categories = compose.onNodeWithTag("settings-categories").fetchSemanticsNode().boundsInRoot
        val engines = compose.onNodeWithTag("search-engines-list").fetchSemanticsNode().boundsInRoot
        assertTrue(engines.left >= categories.right)
        capture("wide-search-customization-light")
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.onNodeWithTag("settings-category-search").assertIsSelected()
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-theme-select"))
        compose.onNodeWithTag("settings-theme-select").performClick()
        compose.onNodeWithTag("settings-theme-dark").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().theme == ThemeMode.DARK } }
        capture("wide-appearance-dark")
        compose.selectSettingsCategory("privacy")
        compose.onNodeWithText("Clear cookies and site data").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("settings-category-autofill").assertIsDisplayed()
        compose.selectSettingsCategory("about")
        compose.onNodeWithText("Nagi ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.returnToBrowserFromSettings()
        compose.onNodeWithTag("sidebar").assertIsDisplayed()
        compose.waitUntil(10_000) { visibleWebViews().size == 2 }
        val after = visibleWebViews()
        assertTrue(before.all { old -> after.any { it === old } })
        after.forEach {
            assertEquals("\"live\"", js(it, "window.settingsToken"))
            assertEquals("\"unfinished 日本語\"", js(it, "document.getElementById('draft').value"))
        }
        assertEquals(sidebar, runBlocking { container.settings.settings.first().sidebarWidth })
        assertEquals(rightId, runBlocking { container.workspace.dao.tab(leftId)!!.splitRightTabId })
    }

    @Test fun compactCategoryHierarchySurvivesRecreationAndWidthChanges() {
        resize(360)
        runBlocking { container.settings.update { it.copy(theme = ThemeMode.DARK) } }
        openSettings()
        compose.onNodeWithTag("settings-categories").assertIsDisplayed()
        compose.onNodeWithTag("settings-list").assertDoesNotExist()
        capture("compact-categories-dark")
        compose.selectSettingsCategory("search")
        compose.onNodeWithTag("customize-search-engines").performClick()
        compose.onNodeWithTag("search-engines-list").assertIsDisplayed()
        compose.onNodeWithTag("settings-categories").assertDoesNotExist()
        compose.onNodeWithTag("search-engines-breadcrumb").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Back to settings").assertCountEquals(1)
        val rowHeight = compose.onNodeWithTag("search-engine-row:google").fetchSemanticsNode().boundsInRoot.height
        assertTrue(rowHeight <= 140f * compose.activity.resources.displayMetrics.density)
        capture("compact-search-customization-dark")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("search-engines-list").fetchSemanticsNodes().isNotEmpty() }
        resize(1000)
        compose.onNodeWithTag("settings-categories").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-search").assertIsSelected()
        compose.onNodeWithTag("search-engines-breadcrumb").assertIsDisplayed()
        capture("wide-search-customization-dark")
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("search-engines-list").assertDoesNotExist()
        compose.selectSettingsCategory("search")
        compose.onNodeWithTag("search-engines-list").assertDoesNotExist()
        compose.onNodeWithTag("customize-search-engines").assertIsDisplayed()
        compose.onNodeWithTag("customize-search-engines").performClick()
        // System Back returns to Search settings before leaving the category.
        compose.onNodeWithTag("search-engines-breadcrumb").assertIsDisplayed()
        shell("input keyevent 4")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("search-engines-list").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("settings-category-search").assertIsSelected()
        compose.selectSettingsCategory("appearance")
        resize(360)
        compose.onNodeWithTag("settings-categories").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to settings").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_bottom_search_bar)).performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().bottomSearchBar } }
        capture("compact-appearance-dark")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.ui_bottom_search_bar)).assertIsDisplayed()
        assertTrue(runBlocking { container.settings.settings.first().bottomSearchBar })
        resize(768) // 760 dp of settings content after the wide frame's 8 dp end inset.
        compose.onNodeWithTag("settings-categories").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-appearance").assertIsSelected()
        capture("boundary-appearance-dark")
        resize(700)
        compose.onNodeWithTag("settings-categories").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to settings").performClick()
        compose.selectSettingsCategory("privacy")
        capture("medium-privacy-dark")
        resize(1000)
        compose.onNodeWithTag("settings-category-privacy").assertIsSelected()
        compose.onNodeWithTag("settings-list").assertIsDisplayed()
        compose.onNodeWithTag("settings-category-autofill").assertIsDisplayed()
        capture("wide-privacy-dark")
        compose.returnToBrowserFromSettings()
        assertEquals(rightId, runBlocking { container.workspace.dao.tab(leftId)!!.splitRightTabId })
    }
}
