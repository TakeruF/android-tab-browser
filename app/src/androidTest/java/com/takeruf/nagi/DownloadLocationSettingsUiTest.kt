package com.takeruf.nagi

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.CommandBarSettings
import com.takeruf.nagi.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadLocationSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (compose.activity.application as NagiApplication).container
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private val folder = "content://example.documents/tree/retention-qa"
    private val hidden = CommandBarSettings(false, false, false, false, false)

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }

    private fun prefs() = runBlocking { container.settings.settings.first() }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        runBlocking {
            container.workspace.ready.await()
            original = prefs()
        }
    }

    @After fun cleanup() {
        if (::original.isInitialized) runBlocking { container.settings.update { original } }
        if (::originalSize.isInitialized) shell("wm size $originalSize")
    }

    @Test fun phoneLightCancelAndResetKeepOtherSettingsAcrossRecreation() = exercise(411, ThemeMode.LIGHT)
    @Test fun tabletDarkCancelAndResetKeepOtherSettingsAcrossRecreation() = exercise(1000, ThemeMode.DARK)

    private fun exercise(width: Int, theme: ThemeMode) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 10 }
        runBlocking { container.settings.update { it.copy(theme = theme, automaticSearchRegion = false,
            downloadFolder = folder, commandBar = hidden, bottomSearchBar = true) } }
        compose.waitUntil(10_000) { compose.activity.hasWindowFocus() }
        val now = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            instrumentation.sendKeySync(KeyEvent(now, now, action, KeyEvent.KEYCODE_COMMA, 0,
                KeyEvent.META_CTRL_ON, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-screen").fetchSemanticsNodes().isNotEmpty() }
        compose.selectSettingsCategory("general")
        fun openLocation() {
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("download-location"))
            compose.onNodeWithTag("download-location").performClick()
        }
        openLocation()
        compose.onNodeWithTag("download-location-default").assertIsNotSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.ui_cancel)).performClick()
        assertEquals(folder, prefs().downloadFolder)
        openLocation()
        compose.onNodeWithTag("download-location-default").performClick()
        compose.waitUntil(5_000) { prefs().downloadFolder == null }
        assertEquals(hidden, prefs().commandBar)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("download-location"))
        compose.onNodeWithText(compose.activity.getString(R.string.ui_download_location_default)).assertIsDisplayed()
        assertEquals(hidden, prefs().commandBar)
        assertEquals(theme, prefs().theme)
        assertEquals(true, prefs().bottomSearchBar)
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "download-location-$width-$theme.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
