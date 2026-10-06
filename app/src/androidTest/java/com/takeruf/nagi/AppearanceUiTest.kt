package com.takeruf.nagi

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.WindowInsetsController
import android.view.inspector.WindowInspector
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import com.takeruf.nagi.data.room.entity
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Checks composited pixels and real screen interactions, not just theme constants. */
@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 30)
class AppearanceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var spaceId: String
    private val favoriteId = UUID.randomUUID().toString()
    private val spaceName = "Appearance QA"
    private var originalRotation: String? = null
    private var originalAutoRotation: String? = null
    private var originalNightMode: String? = null

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { String(it.readBytes()).trim() }

    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(theme = ThemeMode.LIGHT, automaticSearchRegion = false) }
            container.spaces.create(spaceName, color = 0xFF426B5A)
            spaceId = container.settings.settings.first().selectedSpaceId
            container.workspace.dao.putBookmark(Bookmark(favoriteId, "https://example.com/appearance", "Zebra QA",
                isFavorite = true, createdAt = 0).entity())
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Where would you like to go?").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (originalRotation != null) {
            if (originalAutoRotation == "1") shell("cmd window user-rotation free")
            else shell("cmd window user-rotation lock $originalRotation")
        }
        originalNightMode?.let { shell("cmd uimode night $it") }
        runBlocking {
            container.library.removeBookmark(favoriteId)
            container.spaces.delete(spaceId)
            container.settings.update { original }
        }
    }

    private fun dark(mode: ThemeMode) = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM &&
        compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun theme(mode: ThemeMode) {
        runBlocking { container.settings.update { it.copy(theme = mode) } }
        compose.waitUntil(10_000) {
            var lightBars = false
            compose.runOnUiThread { lightBars = WindowCompat.getInsetsController(compose.activity.window,
                compose.activity.window.decorView).isAppearanceLightStatusBars }
            lightBars == !dark(mode)
        }
        compose.waitForIdle()
    }

    private fun image(node: SemanticsNodeInteraction): Bitmap = node.captureToImage().asAndroidBitmap()

    private fun screenImage(node: SemanticsNodeInteraction): Bitmap {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        return Bitmap.createBitmap(screen, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
    }

    private fun contrast(node: SemanticsNodeInteraction, minimum: Double = 4.5, screen: Boolean = false) {
        node.assertIsDisplayed()
        val bitmap = if (screen) screenImage(node) else image(node)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val background = pixels.asIterable().groupingBy { it }.eachCount().maxBy { it.value }.key
        // Use several ink pixels, so a stray subpixel or one anomalous pixel cannot pass.
        val ratios = pixels.map { ColorUtils.calculateContrast(it, background) }.sortedDescending()
        val actual = ratios[minOf(9, ratios.lastIndex)]
        assertTrue("Rendered contrast $actual is below $minimum", actual >= minimum)
    }

    private fun text(value: String) = compose.onNodeWithText(value, useUnmergedTree = true)

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val directory = File(compose.activity.getExternalFilesDir(null), "appearance").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sidebarAndHome(mode: ThemeMode) {
        theme(mode)
        contrast(text("Search anything"))
        contrast(text("Keep your everyday tabs here"))
        contrast(text("Where would you like to go?"))
        contrast(text("Z"))
        contrast(compose.onNodeWithContentDescription("Settings", useUnmergedTree = true), 3.0)
        compose.onNodeWithContentDescription("Tab New tab").assertIsSelected().assertHasClickAction()
        compose.onAllNodesWithText(spaceName, useUnmergedTree = true).also { nodes ->
            assertEquals(2, nodes.fetchSemanticsNodes().size)
            contrast(nodes[0]); contrast(nodes[1])
        }
        save("${mode.name.lowercase()}-home")
    }

    @Test fun pressedNewTabAndSidebarOuterCornersStayClippedInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            val sidebar = image(compose.onNodeWithTag("sidebar"))
            val inset = with(compose.density) { 24.dp.roundToPx() }
            assertNotEquals(sidebar.getPixel(sidebar.width - 1, 1),
                sidebar.getPixel(sidebar.width - 1, inset))
            assertNotEquals(sidebar.getPixel(sidebar.width - 1, sidebar.height - 2),
                sidebar.getPixel(sidebar.width - 1, sidebar.height - inset))
            val button = compose.onNodeWithTag("new-tab-button")
            val before = image(button)
            button.performTouchInput { down(center); advanceEventTime(700) }
            // Clickable rows inside a scroll container defer their press indication.
            compose.waitUntil(3_000) {
                val current = image(button)
                current.getPixel(current.width / 2, 4) != before.getPixel(current.width / 2, 4)
            }
            val pressed = image(button)
            assertEquals(before.getPixel(1, 1), pressed.getPixel(1, 1))
            assertEquals(before.getPixel(pressed.width - 2, pressed.height - 2),
                pressed.getPixel(pressed.width - 2, pressed.height - 2))
            assertNotEquals(before.getPixel(pressed.width / 2, 4),
                pressed.getPixel(pressed.width / 2, 4))
            button.performTouchInput { cancel() }
        }
    }

    @Test fun boundaryMouseResizeCollapsesAtMinimumAndReopens() {
        val edge = compose.onNodeWithTag("sidebar-resize-handle")
        val density = compose.activity.resources.displayMetrics.density
        fun drag(deltaDp: Float) {
            val start = edge.fetchSemanticsNode().boundsInRoot.center
            compose.onRoot().performMouseInput {
                moveTo(start); press()
                moveTo(start + androidx.compose.ui.geometry.Offset(deltaDp * density / 2f, 0f), delayMillis = 100)
            }
            compose.waitForIdle()
            compose.onRoot().performMouseInput {
                moveTo(start + androidx.compose.ui.geometry.Offset(deltaDp * density, 0f), delayMillis = 100)
                release()
            }
            compose.waitForIdle()
        }
        drag(70f)
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().sidebarWidth > 300f } }
        val savedWidth = runBlocking { container.settings.settings.first().sidebarWidth }
        drag(-180f)
        compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().sidebarCollapsed } }
        compose.onNodeWithTag("sidebar").assertWidthIsEqualTo(72.dp)
        assertEquals(savedWidth, runBlocking { container.settings.settings.first().sidebarWidth })
        save("sidebar-minimum-collapsed")
        drag(210f)
        compose.waitUntil(10_000) { runBlocking { !container.settings.settings.first().sidebarCollapsed } }
        compose.onNodeWithTag("sidebar").assertWidthIsAtLeast(220.dp)
    }

    @Test fun boundarySecondaryClickAndCanceledTouchDoNotSaveWidth() {
        val edge = compose.onNodeWithTag("sidebar-resize-handle")
        edge.performMouseInput {
            moveTo(center); press(MouseButton.Secondary)
            moveTo(center + androidx.compose.ui.geometry.Offset(100f, 0f)); release(MouseButton.Secondary)
        }
        edge.performTouchInput {
            down(center); moveBy(androidx.compose.ui.geometry.Offset(-100f, 0f)); cancel()
        }
        compose.waitForIdle()
        val saved = runBlocking { container.settings.settings.first() }
        assertEquals(264f, saved.sidebarWidth)
        assertFalse(saved.sidebarCollapsed)
        compose.onNodeWithTag("sidebar").assertWidthIsEqualTo(264.dp)
    }

    private fun editSpace() {
        compose.onNodeWithContentDescription("Space actions").performClick()
        compose.onNodeWithText("Edit Space").performClick()
    }

    @Test fun spaceDraftPreviewsBehindDialogAndCancelRestoresWithoutWriting() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity,
            com.takeruf.nagi.ui.browser.BrowserViewModel.Factory(container))[
            com.takeruf.nagi.ui.browser.BrowserViewModel::class.java]
        fun persisted() = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId } }
        fun sidebarPixel(): Int {
            val bitmap = image(compose.onNodeWithTag("sidebar"))
            return bitmap.getPixel(1, bitmap.height - 2)
        }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            val originalSpace = persisted()
            val before = sidebarPixel()
            editSpace()
            compose.onNodeWithText("Space name").performTextReplacement("Live Preview QA")
            compose.onNodeWithText("Lucide").performClick()
            compose.onNodeWithContentDescription("Space icon cloud").performClick()
            compose.onNodeWithTag("space-color-${0xFF477F96}").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                vm.state.value.currentSpace?.let {
                    it.name == "Live Preview QA" && it.icon == "lucide:cloud" && it.color == 0xFF477F96
                } == true
            }
            compose.waitForIdle()
            assertNotEquals(before, sidebarPixel())
            assertEquals(originalSpace, persisted())
            save("space-live-preview-${mode.name.lowercase()}")
            text("Cancel").performClick()
            compose.waitUntil(10_000) { vm.state.value.currentSpace?.name == spaceName }
            compose.waitForIdle()
            assertEquals(before, sidebarPixel())
            assertEquals(originalSpace, persisted())

            editSpace()
            compose.onNodeWithText("Custom color", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("HEX color").performTextReplacement("#CC3333")
            compose.waitUntil(10_000) { vm.state.value.currentSpace?.color == 0xFFCC3333 }
            compose.waitForIdle()
            assertNotEquals(before, sidebarPixel())
            assertEquals(originalSpace, persisted())
            compose.onNodeWithText("HEX color").performTextReplacement("invalid")
            compose.waitForIdle()
            assertEquals(0xFFCC3333, vm.state.value.currentSpace?.color)
            compose.onAllNodesWithText("Cancel").onLast().performClick()
            compose.waitUntil(10_000) { vm.state.value.currentSpace?.color == originalSpace.color }
            compose.onNodeWithText("Save").performClick()
            compose.waitForIdle()
            assertEquals(originalSpace.color, persisted().color)
        }
    }

    @Test fun themePresetsAreSelectedAndSavedInSpaceEditor() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            for (seed in listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96, 0xFF995C77, 0xFF687081)) {
                editSpace()
                val chip = compose.onNodeWithTag("space-color-$seed")
                chip.performScrollTo().performClick().assertIsSelected()
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.color == seed } }
                compose.waitForIdle()
            }
            save("space-theme-presets-${mode.name.lowercase()}")
        }
    }

    @Test fun customThemeColorPersistsAndRemainsReadableInBothThemes() {
        editSpace()
        compose.onNodeWithText("Custom color", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("HEX color").performTextReplacement("#FEFE00")
        compose.onAllNodesWithText("Save").onLast().performClick()
        // The inner editor changes only the draft; cancelling the outer editor must discard it.
        assertEquals(0xFF426B5A, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.color })
        text("Cancel").performClick()
        editSpace()
        compose.onNodeWithText("Custom color", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("HEX color").performTextReplacement("#FEFE00")
        compose.onAllNodesWithText("Save").onLast().performClick()
        text("Save").performClick()
        compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.color == 0xFFFEFE00 } }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            contrast(text("Search anything"))
            contrast(text("Where would you like to go?"))
            editSpace()
            contrast(compose.onNodeWithText("Custom color", substring = true, useUnmergedTree = true).performScrollTo())
            save("custom-space-theme-${mode.name.lowercase()}")
            text("Cancel").performClick()
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0xFFFEFE00, runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.color })
    }

    @Test fun selectedSpaceColorsChangeRenderedBaseAndSurviveRecreation() {
        fun matches(seed: Long): Boolean {
            val bitmap = image(compose.onNodeWithTag("sidebar"))
            val pixel = bitmap.getPixel(1, bitmap.height - 2)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            return if (seed == 0xFFCC3333) r > g && r > b else b > g && b > r
        }
        val otherName = "Blue Space QA"
        runBlocking { container.spaces.create(otherName, color = 0xFF3568C0) }
        val otherId = runBlocking { container.settings.settings.first().selectedSpaceId }
        try {
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                theme(mode)
                runBlocking { container.spaces.edit(spaceId, spaceName, "◉", 0xFFCC3333) }
                for ((id, seed) in listOf(spaceId to 0xFFCC3333, otherId to 0xFF3568C0)) {
                    runBlocking { container.spaces.select(id) }
                    compose.waitUntil(10_000) { matches(seed) }
                    compose.waitForIdle()
                    contrast(text("Search anything"))
                    contrast(text("Where would you like to go?"))
                    save("space-base-${mode.name.lowercase()}-${if (seed == 0xFFCC3333) "red" else "blue"}")
                }
                compose.activityRule.scenario.recreate()
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }
                compose.waitUntil(10_000) { matches(0xFF3568C0) }
                assertEquals(otherId, runBlocking { container.settings.settings.first().selectedSpaceId })
                assertEquals(0xFF426B5A, runBlocking { container.settings.settings.first().themeColor })
            }
        } finally {
            runBlocking { container.spaces.select(spaceId); container.spaces.delete(otherId) }
        }
    }

    @Test fun lightSidebarFavoritesAndNewTabRemainReadable() = sidebarAndHome(ThemeMode.LIGHT)
    @Test fun darkSidebarFavoritesAndNewTabRemainReadable() = sidebarAndHome(ThemeMode.DARK)

    @Test fun themeChipsPersistAcrossRecreationAndSystemModeUsesSystemAppearance() {
        compose.onNodeWithContentDescription("Settings").performClick()
        for (mode in listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)) {
            val label = mode.name.lowercase().replaceFirstChar(Char::uppercase)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performClick()
            compose.waitUntil(10_000) { runBlocking { container.settings.settings.first().theme == mode } }
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label).assertIsSelected()
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("You can also drag the sidebar edge."))
            contrast(text("You can also drag the sidebar edge."))
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(mode, runBlocking { container.settings.settings.first().theme })
            theme(mode)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsSelected()
        }
        save("system-settings")
    }

    @Test fun commandSelectionKeyboardNavigationAndDialogSystemBarsFollowBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            compose.onNodeWithText("Search anything").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(">")
            compose.onNode(hasSetTextAction()).assertIsFocused()
            val selected = compose.onNode(isSelected() and hasAnyDescendant(hasText("New tab")) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
            selected.assertExists()
            contrast(compose.onNode(hasText("New tab") and hasAnyAncestor(isDialog()), useUnmergedTree = true))
            var lightStatus = false
            compose.runOnIdle {
                val focused = WindowInspector.getGlobalWindowViews().first { it.hasWindowFocus() }
                lightStatus = focused.windowInsetsController!!.systemBarsAppearance and
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS != 0
            }
            assertEquals(!dark(mode), lightStatus)
            compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
            selected.assertDoesNotExist()
            compose.onAllNodes(isSelected() and hasAnyAncestor(isDialog())).assertCountEquals(1)
            save("${mode.name.lowercase()}-commands")
            compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
            compose.onNodeWithText("Esc").assertDoesNotExist()
        }
    }

    @Test fun exactLucideCodeSelectionValidatesCancelsAndPersists() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            compose.onNodeWithContentDescription("Space actions").performClick()
            compose.onNodeWithText("Edit Space").performClick()
            compose.onNodeWithText("Lucide").performClick()
            compose.onNodeWithText("Search for more on Lucide").performScrollTo().assertHasClickAction()
            compose.onNodeWithText("Choose by Lucide code name").performScrollTo().performClick()
            for (invalid in listOf("Angle", "angle ", "ang", "lucide:angle", "no-such-icon")) {
                compose.onNodeWithText("Icon code name").performTextReplacement(invalid)
                compose.onNodeWithText("Use icon").assertIsNotEnabled()
                compose.onNodeWithText("No icon matches this code name.").assertIsDisplayed()
            }
            compose.onNodeWithText("Icon code name").performTextReplacement("angle")
            compose.onNodeWithContentDescription("Icon preview").assertIsDisplayed()
            compose.onNodeWithText("Use icon").assertIsEnabled()
            save("${mode.name.lowercase()}-lucide-code-preview")
            compose.onNodeWithText("Use icon").performClick()
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon == "lucide:angle" } }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Space actions").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("lucide:angle", runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon })
            compose.onNodeWithContentDescription("Space actions").performClick()
            compose.onNodeWithText("Edit Space").performClick()
            compose.onNodeWithText("Choose by Lucide code name").performScrollTo().performClick()
            compose.onNodeWithText("Icon code name").performTextReplacement("bird")
            compose.onNode(hasText("Cancel") and hasAnyAncestor(hasTestTag("lucide-code-dialog"))).performClick()
            compose.onNodeWithText("Icon code name").assertDoesNotExist()
            compose.onNodeWithText("Save").performClick()
            compose.waitForIdle()
            assertEquals("lucide:angle", runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon })
        }
    }

    @Test fun lucideAndEmojiPersistAcrossRecreationAndBothThemes() {
        val colors = listOf(0xFF426B5A, 0xFF6C6193, 0xFFB07D47, 0xFF477F96, 0xFF995C77, 0xFF687081)
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            colors.forEachIndexed { index, color ->
                compose.onNodeWithContentDescription("Space actions").performClick()
                compose.onNodeWithText("Edit Space").performClick()
                compose.onNodeWithText("Lucide").performClick()
                compose.onNodeWithContentDescription("Space icon cloud").performClick()
                compose.onNodeWithContentDescription("Space theme ${index + 1}").performClick()
                if (index == 0) save("${mode.name.lowercase()}-lucide-picker")
                compose.onNodeWithText("Save").performClick()
                compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.let { it.color == color && it.icon == "lucide:cloud" } } }
                assertEquals("lucide:cloud", runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon })
            }
            save("${mode.name.lowercase()}-cloud-space")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Space actions").fetchSemanticsNodes().isNotEmpty() }
            assertEquals("lucide:cloud", runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon })
            compose.onNodeWithContentDescription("Space actions").performClick()
            compose.onNodeWithText("Edit Space").performClick()
            compose.onNodeWithText("Emoji").performClick()
            compose.onNodeWithText("Custom emoji").performScrollTo().performTextReplacement("🦊")
            save("${mode.name.lowercase()}-space-icon-picker")
            compose.onNodeWithText("Save").performClick()
            compose.waitUntil(10_000) { runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.icon == "🦊" } }
        }
    }

    @Test fun collapsedTabsExposeTheirNamesAndSelectedStateInBothThemes() {
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            compose.onNodeWithContentDescription("Collapse sidebar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Expand sidebar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Expand sidebar").assertExists()
            compose.onNodeWithContentDescription("Tab New tab").assertIsSelected().assertHasClickAction().performClick()
            contrast(compose.onNodeWithContentDescription("Settings", useUnmergedTree = true), 3.0)
            save("${mode.name.lowercase()}-collapsed")
            compose.onNodeWithContentDescription("Expand sidebar").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Collapse sidebar").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test fun portraitSplitAndFindControlsRemainReachableWithWideSidebar() {
        theme(ThemeMode.DARK)
        runBlocking { container.settings.update { it.copy(sidebarWidth = 380f) } }
        originalRotation = shell("settings get system user_rotation")
        originalAutoRotation = shell("settings get system accelerometer_rotation")
        shell("cmd window user-rotation lock 1")
        compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription("Page menu").performClick()
        compose.onNodeWithText("New split view").performClick()
        compose.onNodeWithContentDescription("Resize split view").assertIsDisplayed().performTouchInput { swipeLeft() }
        compose.onNodeWithText("Choose tab").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Page menu")[0].performClick()
        compose.onNodeWithText("Find in page").performClick()
        val inputBounds = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("Find input is too narrow: $inputBounds", inputBounds.width >= 100 * density)
        assertTrue("Find input must stay one line: $inputBounds", inputBounds.height <= 64 * density)
        save("dark-portrait-find")
        compose.onNodeWithContentDescription("Close find").assertIsDisplayed().performClick()
        save("dark-portrait-split")
        compose.onAllNodesWithContentDescription("Page menu")[0].performClick()
        compose.onNodeWithText("Close split view").performClick()
        compose.onNodeWithText("Where would you like to go?").assertIsDisplayed()
    }

    @Test fun systemThemeTracksDeviceNightModeWithoutLosingTheWorkspace() {
        originalNightMode = shell("cmd uimode night").substringAfter(":").trim()
        theme(ThemeMode.SYSTEM)
        val tabId = runBlocking { container.workspace.dao.tabs(spaceId).single().id }
        for (night in listOf("yes", "no")) {
            shell("cmd uimode night $night")
            compose.waitUntil(10_000) {
                val darkNow = compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                darkNow == (night == "yes")
            }
            theme(ThemeMode.SYSTEM)
            contrast(text("Search anything"))
            contrast(text("Z"))
            assertEquals(tabId, runBlocking { container.workspace.dao.tabs(spaceId).single().id })
            save("system-${if (night == "yes") "dark" else "light"}")
        }
    }

    @Test fun connectionErrorsAndRetryTextRemainReadableInBothThemes() {
        // Use a closed local fixture port: exercise the actual WebView error path without the internet.
        val server = FixtureServer()
        val url = "${server.origin}/one"
        server.close()
        compose.runOnIdle {
            compose.activity.window.callback.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON))
        }
        compose.onNode(hasSetTextAction()).performTextReplacement(url)
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            theme(mode)
            contrast(text("Retry"))
            contrast(text("net::ERR_CONNECTION_REFUSED"))
            compose.onNodeWithText("Retry").assertHasClickAction().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
            save("${mode.name.lowercase()}-error")
        }
    }
}
