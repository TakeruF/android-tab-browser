package com.takeruf.nagi

import android.webkit.CookieManager
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ReleaseFeaturesUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var tabId: String
    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await(); container.settings.update { BrowserSettings(automaticSearchRegion = false) }
            container.spaces.create("Release QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.tabs.create(spaceId, "${server.origin}/one")
        }
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tab(tabId)?.title == "Fixture One" } }
    }
    @After fun cleanup() { runBlocking { container.spaces.delete(spaceId) }; server.close() }
    private fun screenshot(name: String, directory: File? = InstrumentationRegistry.getArguments()
        .getString("additionalTestOutputDir")?.let(::File)) {
        if (directory == null) return
        compose.activity.getExternalFilesDir(null) // Initialize the app-scoped directory on a fresh emulator.
        directory.mkdirs()
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun removedLibraryActionsAreAbsentFromMenusAndCommands() {
        compose.onNodeWithContentDescription("Bookmarks").assertDoesNotExist()
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("Save bookmark").assertDoesNotExist()
        compose.onNodeWithText("Add to favorites").assertDoesNotExist()
        compose.onNodeWithText("Find in page").assertIsDisplayed()
        screenshot("page-menu-without-library-actions")
        compose.onNodeWithText("Find in page").performClick()
        compose.onNodeWithContentDescription("Close find").performClick()
        compose.onNodeWithTag("url-drop-$tabId").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(">")
        compose.onNodeWithText("Bookmarks").assertDoesNotExist()
        compose.onNodeWithTag("command-suggestions").performScrollToNode(hasText("Open history"))
        compose.onNodeWithText("Open history").assertIsDisplayed()
        screenshot("commands-without-bookmarks")
    }

    @Test fun passwordAutofillSettingsOpenAndroidServicePicker() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.selectSettingsCategory("autofill")
        compose.onNodeWithTag("autofill-status").assertIsDisplayed()
        compose.onNodeWithTag("autofill-settings").assertIsDisplayed().performClick()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            var settingsOpened = false
            compose.waitUntil(10_000) {
                val shell = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                val activity = android.os.ParcelFileDescriptor.AutoCloseInputStream(shell).bufferedReader().use { it.readText() }
                settingsOpened = activity.lineSequence().any { "mResumedActivity" in it && "com.android.settings" in it }
                settingsOpened
            }
            assertTrue(settingsOpened)
        } finally { instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) }
    }

    @Test fun passwordAutofillActionIsAvailableOnNormalPages() {
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithTag("request-password-autofill").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun backgroundTabsCanSleepAndWakeWithoutClosingThem() {
        val next = runBlocking { container.tabs.create(spaceId, "${server.origin}/two") }
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tab(next)?.title == "Fixture Two" } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.selectSettingsCategory("tabs")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("suspend-background-tabs"))
        compose.onNodeWithTag("suspend-background-tabs").performClick()
        compose.returnToBrowserFromSettings()
        val sleepingTab = hasContentDescription("Suspended tab") and hasAnyAncestor(hasTestTag("sidebar-tab-$tabId"))
        compose.waitUntil(5_000) { compose.onAllNodes(sleepingTab).fetchSemanticsNodes().isNotEmpty() }
        screenshot("suspended-tab-settings")
        compose.onNodeWithTag("sidebar-tab-$tabId").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(sleepingTab).fetchSemanticsNodes().isEmpty() }
        assertNull(runBlocking { container.workspace.dao.tab(tabId)!!.closedAt })
        compose.onNodeWithTag("browser-pane-left-$tabId").assertExists()
        compose.onNodeWithTag("address-copy-url:$tabId").performTouchInput { click() }
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("${server.origin}/one", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithTag("url-drop-$tabId").performTouchInput { click() }
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithTag("command-close").performClick()
    }
    @Test fun linkDragToRightAddressBarNavigatesOnlyRightPane() = dragLinkToAddressBar(split = true)

    @Test fun linkDragToAddressBarInDarkMode() = dragLinkToAddressBar(split = false)

    private fun dragLinkToAddressBar(split: Boolean) {
        val prefix = if (split) "link-drag-split-light" else "link-drag-single-dark"
        val output = File(compose.activity.filesDir, "link-drag-polish")
        if (!split) runBlocking {
            container.settings.update { it.copy(theme = com.takeruf.nagi.domain.model.ThemeMode.DARK) }
        }
        runBlocking { container.workspace.dao.tabs(spaceId).filter { it.id != tabId }.forEach { container.tabs.close(it.id) } }
        val sourceId = runBlocking { container.tabs.create(spaceId, "${server.origin}/features") }
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tab(sourceId)?.title == "Fixture Features" } }
        if (split) {
            compose.onNodeWithContentDescription("Page menu").performClick()
            compose.onNodeWithText("New split view").performClick()
        }
        val destinationId = if (split) tabId else sourceId
        compose.onNodeWithTag("url-drop-$destinationId").assertExists()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val coordinates = kotlinx.coroutines.CompletableDeferred<String>()
        val bounds = android.graphics.Rect()
        compose.runOnIdle {
            val source = compose.activity.findViewById<android.webkit.WebView>(R.id.browser_web_view)
            assertEquals("${server.origin}/features", source.url)
            source.evaluateJavascript("JSON.stringify((()=>{let r=document.getElementById('drag-link').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})())") {
                coordinates.complete(it)
            }
        }
        val encoded = runBlocking { kotlinx.coroutines.withTimeout(5_000) { coordinates.await() } }
        val rect = org.json.JSONObject(org.json.JSONTokener(encoded).nextValue() as String)
        compose.runOnIdle {
            val source = compose.activity.findViewById<android.webkit.WebView>(R.id.browser_web_view)
            val position = IntArray(2); source.getLocationOnScreen(position)
            val scale = source.scale.toDouble()
            val x = position[0] + (rect.getDouble("x") * scale).toInt()
            val y = position[1] + (rect.getDouble("y") * scale).toInt()
            assertTrue(x in position[0] until position[0] + source.width)
            assertTrue(y in position[1] until position[1] + source.height)
            bounds.set(x - 1, y - 1, x + 1, y + 1)
        }
        screenshot("$prefix-before", output)
        val target = compose.onNodeWithTag("url-drop-$destinationId").fetchSemanticsNode().boundsInRoot
        val downTime = android.os.SystemClock.uptimeMillis()
        fun pointer(action: Int, x: Float, y: Float) {
            val event = android.view.MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        // Compose bounds are relative to its root; account for the edge-to-edge window's root position.
        val root = IntArray(2); compose.runOnIdle { compose.activity.findViewById<android.view.View>(android.R.id.content).getLocationOnScreen(root) }
        val endX = root[0] + target.center.x; val endY = root[1] + target.center.y
        pointer(android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY())
        Thread.sleep(900)
        for (step in 1..15) {
            pointer(android.view.MotionEvent.ACTION_MOVE, bounds.exactCenterX() + (endX - bounds.exactCenterX()) * step / 15,
                bounds.exactCenterY() + (endY - bounds.exactCenterY()) * step / 15)
            Thread.sleep(40)
        }
        screenshot("$prefix-hover", output)
        pointer(android.view.MotionEvent.ACTION_UP, endX, endY)
        compose.waitUntil(15_000) { runBlocking { container.workspace.dao.tab(destinationId)?.title == "Fixture Two" } }
        val destinationAddress = hasText("${server.origin.removePrefix("http://")}/two") and
            hasAnyAncestor(hasTestTag("browser-pane-${if (split) "right" else "left"}-$destinationId"))
        compose.waitUntil(5_000) { compose.onAllNodes(destinationAddress).fetchSemanticsNodes().isNotEmpty() }
        if (split) assertEquals("${server.origin}/features", runBlocking { container.workspace.dao.tab(sourceId)!!.url })
        screenshot("$prefix-after", output)
    }

    @Test fun clearingSiteDataRequiresConfirmationAndPreservesWorkspace() {
        var cookie: String? = null
        compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }
        assertTrue(cookie?.contains("nagi=cookie") == true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.selectSettingsCategory("privacy")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Clear cookies and site data"))
        compose.onNodeWithText("Clear cookies and site data").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }
        assertNotNull(cookie)
        compose.onNodeWithText("Clear cookies and site data").performClick()
        compose.onNodeWithText("Clear data").performClick()
        compose.waitUntil(10_000) { compose.runOnIdle { cookie = CookieManager.getInstance().getCookie(server.origin) }; cookie == null }
        assertEquals("${server.origin}/one", runBlocking { container.workspace.dao.tab(tabId)!!.url })
        compose.selectSettingsCategory("about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("app-updates"))
        compose.onNodeWithText("App updates").assertIsDisplayed()
        compose.onNodeWithText("Nagi ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Current version: ${BuildConfig.VERSION_NAME}").assertDoesNotExist()
        if (BuildConfig.APK_UPDATES_ENABLED) {
            compose.onNodeWithText("Check for updates").assertExists()
        } else {
            // The whole updates row opens Google Play.
            compose.onNodeWithText("Updates are delivered through Google Play.").assertExists()
            compose.onNodeWithTag("app-updates").assertHasClickAction()
            compose.onNodeWithText("Check for updates").assertDoesNotExist()
            val info = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            assertFalse(info.requestedPermissions.orEmpty().contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        }
        // Open-source licenses: the list opens, a bundled text is readable, and Back returns step by step.
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("open-source-licenses"))
        compose.onNodeWithTag("open-source-licenses").performClick()
        compose.onNodeWithTag("license-entry-0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("license-text").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Nagi-MIT.txt").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("licenses-list").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("settings-list").assertExists()
        // The privacy policy sits with the other documents in About, not among Privacy controls.
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("privacy-policy"))
        compose.onNodeWithText("Privacy policy").assertIsDisplayed()
        screenshot("release-updates")
        compose.selectSettingsCategory("privacy")
        compose.onNodeWithTag("privacy-policy").assertDoesNotExist()
    }
}
