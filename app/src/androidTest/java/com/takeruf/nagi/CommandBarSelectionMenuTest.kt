package com.takeruf.nagi

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.search.SuggestionAction
import com.takeruf.nagi.domain.model.*
import com.takeruf.nagi.ui.commandbar.CommandBar
import com.takeruf.nagi.ui.theme.NagiTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Real secondary-click input and native toolbar actions in the Command Bar's Dialog. */
@OptIn(ExperimentalTestApi::class)
@SdkSuppress(minSdkVersion = 29)
class CommandBarSelectionMenuTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val modes = CopyOnWriteArrayList<ActionMode>()
    private val actions = mutableListOf<SuggestionAction>()
    private lateinit var originalSize: String
    private var accessibilityFlags = 0
    private val input get() = compose.onNodeWithTag("command-input")
    private val text = "https://example.com/path?query=日本語"

    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Before fun setup() {
        originalSize = shell("wm size")
        val service = instrumentation.uiAutomation.serviceInfo
        accessibilityFlags = service.flags
        service.flags = service.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = service
    }

    @After fun cleanup() {
        compose.runOnIdle { modes.forEach(ActionMode::finish) }
        val override = Regex("Override size: (\\d+x\\d+)").find(originalSize)?.groupValues?.get(1)
        shell(if (override == null) "wm size reset" else "wm size $override")
        val service = instrumentation.uiAutomation.serviceInfo
        service.flags = accessibilityFlags
        instrumentation.uiAutomation.serviceInfo = service
    }

    private fun dialog(view: View): Window? = when (view) {
        is DialogWindowProvider -> view.window
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { dialog(view.getChildAt(it)) }
        else -> null
    }

    private fun show(widthDp: Int, theme: ThemeMode, layout: CommandBarLayout) {
        val dpi = Regex("(?:Override|Physical) density: (\\d+)").findAll(shell("wm density"))
            .last().groupValues[1].toInt()
        shell("wm size ${widthDp * dpi / 160}x${820 * dpi / 160}")
        compose.setContent {
            NagiTheme(theme) {
                CommandBar(text, WorkspaceSnapshot(), BrowserSettings(commandBarLayout = layout),
                    onDismiss = {}, onExecute = { actions += it }, animateEntrance = false)
            }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("command-input") and isFocused()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.runOnIdle {
            val window = WindowInspector.getGlobalWindowViews().filter { it.hasWindowFocus() }.firstNotNullOfOrNull(::dialog)
                ?: error("Command Bar dialog not found")
            val original = window.callback
            window.callback = object : Window.Callback by original {
                override fun onActionModeStarted(mode: ActionMode) {
                    modes += mode
                    original.onActionModeStarted(mode)
                }
                override fun onActionModeFinished(mode: ActionMode) {
                    modes.remove(mode)
                    original.onActionModeFinished(mode)
                }
            }
        }
    }

    private fun nativeAction(id: Int): AccessibilityNodeInfo? {
        val label = instrumentation.targetContext.getString(id)
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
            return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let(::find) }
        }
        return instrumentation.uiAutomation.windows.firstNotNullOfOrNull { it.root?.let(::find) }
    }

    private fun awaitToolbar(action: Int) {
        compose.waitUntil(5_000) { modes.isNotEmpty() && nativeAction(action) != null }
        assertEquals(ActionMode.TYPE_FLOATING, modes.last().type)
    }

    private fun clickAction(action: Int) {
        // Native ActionMode rebuilds its separate accessibility window when
        // classification/clipboard data changes. Resolve a fresh button each time.
        compose.waitUntil(5_000) {
            var node = nativeAction(action)
            while (node != null && !node.isClickable) node = node.parent
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        compose.waitForIdle()
    }

    private fun secondaryClick() {
        // Finish any toolbar created by the semantics selection first, so the
        // assertion establishes that the mouse gesture itself opens a new one.
        compose.runOnIdle { modes.forEach(ActionMode::finish); modes.clear() }
        input.performMouseInput { click(button = MouseButton.Secondary) }
    }

    private fun clipboard(): String {
        var value = ""
        compose.runOnIdle {
            value = instrumentation.targetContext.getSystemService(ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.coerceToText(instrumentation.targetContext).toString()
        }
        return value
    }

    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(150)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "command-selection").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun exercise(width: Int, theme: ThemeMode, layout: CommandBarLayout) {
        show(width, theme, layout)
        secondaryClick()
        awaitToolbar(android.R.string.copy)
        assertEquals(TextRange(0, text.length), input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        capture("$width-$theme-$layout-secondary")
        clickAction(android.R.string.copy)
        compose.waitUntil(5_000) { clipboard() == text }

        val selected = TextRange(8, 19)
        input.performTextInputSelection(selected)
        secondaryClick()
        awaitToolbar(android.R.string.cut)
        assertEquals(selected, input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        clickAction(android.R.string.cut)
        input.assertTextEquals(text.removeRange(selected.start, selected.end))
        compose.waitUntil(5_000) { clipboard() == text.substring(selected.start, selected.end) }

        val pasted = "貼り付け 中文 한국어"
        compose.runOnIdle {
            instrumentation.targetContext.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Fixture", pasted))
        }
        input.performTextInputSelection(TextRange(0, text.length - selected.length))
        secondaryClick()
        awaitToolbar(android.R.string.paste)
        clickAction(android.R.string.paste)
        input.assertTextEquals(pasted).assertIsFocused()

        input.performTextReplacement("Alpha beta gamma")
        compose.runOnIdle { modes.forEach(ActionMode::finish); modes.clear() }
        input.performTouchInput { longClick(centerLeft + androidx.compose.ui.geometry.Offset(12f, 0f)) }
        awaitToolbar(android.R.string.copy)
        capture("$width-$theme-$layout-touch")
        clickAction(android.R.string.copy)
        compose.waitUntil(5_000) { clipboard() == "Alpha" }
        compose.runOnIdle { assertTrue("Editing must not execute a suggestion", actions.isEmpty()) }
    }

    @Test fun phoneLightUsesFloatingToolbarForMouseAndTouch() = exercise(411, ThemeMode.LIGHT, CommandBarLayout.ANCHORED)
    @Test fun tabletDarkUsesFloatingToolbarForMouseAndTouch() = exercise(1000, ThemeMode.DARK, CommandBarLayout.ANCHORED)
    @Test fun centeredLightUsesFloatingToolbarForMouseAndTouch() = exercise(1000, ThemeMode.LIGHT, CommandBarLayout.CENTERED)

    private fun exerciseAutomaticSelection(width: Int, theme: ThemeMode, layout: CommandBarLayout) {
        show(width, theme, layout)
        input.performTextReplacement("Alpha beta gamma")
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        input.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val end = layouts.single().getCursorRect(10).left
        input.performMouseInput {
            moveTo(centerLeft + androidx.compose.ui.geometry.Offset(1f, 0f))
            press()
            moveTo(centerLeft + androidx.compose.ui.geometry.Offset(end, 0f), delayMillis = 80)
            release()
        }
        awaitToolbar(android.R.string.copy)
        assertEquals(TextRange(0, 10), input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        capture("$width-$theme-$layout-drag-release")
        clickAction(android.R.string.copy)
        compose.waitUntil(5_000) { clipboard() == "Alpha beta" }
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun mouseDragOpensToolbarOnReleaseWithoutChangingTheSelectedRange() =
        exerciseAutomaticSelection(1000, ThemeMode.LIGHT, CommandBarLayout.ANCHORED)
    @Test fun phoneDarkDragOpensToolbarOnRelease() =
        exerciseAutomaticSelection(411, ThemeMode.DARK, CommandBarLayout.ANCHORED)
    @Test fun centeredDarkDragOpensToolbarOnRelease() =
        exerciseAutomaticSelection(1000, ThemeMode.DARK, CommandBarLayout.CENTERED)
}
