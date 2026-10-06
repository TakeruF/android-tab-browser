package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.components.*
import com.takeruf.nagi.ui.theme.NagiTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class OverflowMenuUiTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var mode by mutableStateOf(ThemeMode.LIGHT)
    private var fontScale by mutableFloatStateOf(1f)
    private var clicks = 0

    private fun content() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NagiTheme(mode) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopEnd) {
                            Box {
                                IconButton(onClick = { expanded = true }) { Icon(NagiIcons.Ellipsis, "Open overflow") }
                                NagiOverflowMenu(expanded, { expanded = false }) {
                                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                                        NagiOverflowMenuItem(text = { Text("Pin tab") }, leadingIcon = { Icon(NagiIcons.Pin, null) },
                                            onClick = { clicks++; expanded = false })
                                        NagiOverflowMenuItem(text = { Text("Unavailable action") }, leadingIcon = { Icon(NagiIcons.Bookmark, null) },
                                            enabled = false, onClick = { clicks++ })
                                        NagiOverflowMenuDivider()
                                        NagiOverflowMenuItem(text = { Text("Delete space") }, leadingIcon = { Icon(NagiIcons.Trash2, null) },
                                            destructive = true, onClick = { clicks++; expanded = false })
                                        NagiOverflowMenuItem(text = { Text("Move to a space with a long accessible name") },
                                            leadingIcon = { Icon(NagiIcons.ArrowForward, null) }, onClick = { clicks++ })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun open() {
        compose.onNodeWithContentDescription("Open overflow").performClick()
        compose.onNode(isPopup()).assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "overflow-validation").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun disabledActionsAndBackDismissalRemainAccessible() {
        content()
        open()
        compose.onNodeWithText("Unavailable action").assertIsNotEnabled().performClick()
        compose.onNode(isPopup()).assertIsDisplayed()
        assertEquals(0, clicks)
        compose.onNodeWithText("Pin tab").assertHasClickAction().performClick()
        compose.onNode(isPopup()).assertDoesNotExist()
        assertEquals(1, clicks)
        open()
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 4")).use { it.readBytes() }
        compose.waitUntil { !expanded }
        compose.onNode(isPopup()).assertDoesNotExist()
    }

    @Test fun hoverIsInsetAndRoundedInBothThemes() {
        content()
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.runOnIdle { mode = theme }
            open()
            val row = compose.onNodeWithText("Pin tab")
            row.assertHeightIsEqualTo(48.dp)
            val before = row.captureToImage().asAndroidBitmap()
            row.performMouseInput { enter(center); moveTo(center) }
            val hovered = row.captureToImage().asAndroidBitmap()
            // Corner pixels retain the card color; the center-left inset gains a state layer.
            assertEquals(before.getPixel(1, 1), hovered.getPixel(1, 1))
            assertNotEquals(before.getPixel(hovered.width / 2, 4), hovered.getPixel(hovered.width / 2, 4))
            screenshot("${theme.name.lowercase()}-hover")
            row.performMouseInput { exit() }
            compose.runOnIdle { expanded = false }
        }
    }

    @Test fun largeFontsGrowRowsAndKeepAllActionsReachable() {
        content()
        open()
        val regularHeight = compose.onNodeWithText("Pin tab").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { fontScale = 2f; mode = ThemeMode.DARK }
        compose.waitForIdle()
        assertTrue(compose.onNodeWithText("Pin tab").fetchSemanticsNode().boundsInRoot.height > regularHeight)
        compose.onNodeWithText("Pin tab").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Move to a space with a long accessible name").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        screenshot("dark-large-font")
        compose.onNodeWithText("Delete space").performScrollTo().performClick()
        assertEquals(1, clicks)
        compose.onNode(isPopup()).assertDoesNotExist()
    }
}
