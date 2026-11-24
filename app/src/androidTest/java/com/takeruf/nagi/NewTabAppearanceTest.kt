package com.takeruf.nagi

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.newtab.NewTabPhotoStore
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.NewTabBackground
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NewTabAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var space: String

    @Before fun setup() {
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            val theme = InstrumentationRegistry.getArguments().getString("backgroundTheme")?.let(ThemeMode::valueOf) ?: ThemeMode.SYSTEM
            container.settings.update { BrowserSettings(automaticSearchRegion = false, theme = theme) }
            container.spaces.create("Background QA")
            space = container.settings.settings.first().selectedSpaceId
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("customize-new-tab").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() = runBlocking {
        container.spaces.delete(space)
        container.settings.update { original }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun backgroundCanBeChangedFromNewTabAndSurvivesRecreation() {
        compose.onNodeWithTag("customize-new-tab").performClick()
        compose.onNodeWithTag("new-tab-choose-photo").assertDoesNotExist()
        compose.onNodeWithTag("new-tab-preview").assertIsDisplayed()
        compose.onNodeWithTag("new-tab-option-DEFAULT").assertIsSelected()
        screenshot("new-tab-background-options")
        compose.onNodeWithTag("new-tab-option-SOLID").performScrollTo().performClick()
        compose.onNodeWithTag("new-tab-color-4676A0").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking {
            val saved = container.settings.settings.first()
            saved.newTabBackground == NewTabBackground.SOLID && saved.newTabColor == 0xFF4676A0
        } }
        compose.onNodeWithTag("new-tab-color-4676A0").assertIsSelected()
        // The preview reflects the real color beneath its decorative search bar.
        compose.onNodeWithTag("new-tab-preview").performScrollTo()
        val preview = compose.onNodeWithTag("new-tab-preview").captureToImage().toPixelMap()
        val pixel = preview[preview.width / 2, preview.height * 4 / 5]
        assertEquals(70 / 255f, pixel.red, 0.03f)
        assertEquals(118 / 255f, pixel.green, 0.03f)
        assertEquals(160 / 255f, pixel.blue, 0.03f)
        compose.onNodeWithTag("new-tab-option-GRADIENT").performScrollTo().performClick()
        compose.onNodeWithTag("new-tab-color-4676A0").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("new-tab-custom-color").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("234567")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().newTabColor == 0xFF234567 } }
        screenshot("new-tab-background-editor")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("new-tab-background-GRADIENT").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("new-tab-background-GRADIENT").fetchSemanticsNodes().isNotEmpty() }
        screenshot("new-tab-gradient")
        compose.onNodeWithTag("customize-new-tab").performClick()
        compose.onNodeWithTag("new-tab-option-DEFAULT").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithTag("new-tab-background-DEFAULT").assertIsDisplayed()
        compose.runOnIdle { compose.activity.window.callback.dispatchKeyEvent(
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA, 0, KeyEvent.META_CTRL_ON)) }
        compose.selectSettingsCategory("appearance")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-new-tab-background"))
        compose.onNodeWithTag("settings-new-tab-background").performClick()
        compose.onNodeWithTag("new-tab-option-DEFAULT").assertIsSelected()
        compose.onNodeWithText("Done").performClick()
    }

    @Test fun importedPhotoIsBoundedOrientedAndWorksWithoutOriginal() = runBlocking {
        val source = File(compose.activity.cacheDir, "background-source.jpg")
        val bitmap = Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes()
        }
        // Supply a controlled result through the real picker contract and ViewModel pipeline.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(IntentFilter("android.provider.action.PICK_IMAGES").apply { addDataType("image/*") },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(source))), true)
        try {
            compose.onNodeWithTag("customize-new-tab").performClick()
            compose.onNodeWithTag("new-tab-choose-photo").assertDoesNotExist()
            compose.onNodeWithTag("new-tab-option-PHOTO").performClick()
            compose.waitUntil(15_000) { runBlocking { container.settings.settings.first().newTabPhoto != null } }
            assertEquals(1, monitor.hits)
            compose.onNodeWithTag("new-tab-option-PHOTO").assertIsSelected()
            compose.onNodeWithTag("new-tab-preview").performScrollTo().assertIsDisplayed()
            screenshot("new-tab-photo-editor")
            compose.onNodeWithText("Done").performClick()
        } finally { instrumentation.removeMonitor(monitor) }
        val name = container.settings.settings.first().newTabPhoto!!
        val copy = NewTabPhotoStore.file(compose.activity, name)!!
        try {
            assertTrue(source.delete())
            val decoded = BitmapFactory.decodeFile(copy.path)
            assertTrue(maxOf(decoded.width, decoded.height) <= NewTabPhotoStore.MAX_EDGE)
            assertTrue(decoded.height > decoded.width)
            assertTrue(decoded.allocationByteCount <= NewTabPhotoStore.MAX_EDGE * NewTabPhotoStore.MAX_EDGE * 4)
            val pixel = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertTrue(Color.blue(pixel) >= 250 && Color.red(pixel) <= 5 && Color.green(pixel) <= 5)
            decoded.recycle()
            container.settings.update { it.copy(newTabBackground = NewTabBackground.PHOTO, newTabPhoto = name) }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("new-tab-background-PHOTO").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) {
                val pixel = compose.onNodeWithTag("new-tab-background-PHOTO").captureToImage().toPixelMap()[8, 8]
                pixel.blue > 0.8f && pixel.red < 0.05f && pixel.green < 0.05f
            }
            screenshot("new-tab-photo")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("new-tab-background-PHOTO").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(name, container.settings.settings.first().newTabPhoto)
            compose.waitUntil(10_000) {
                val pixel = compose.onNodeWithTag("new-tab-background-PHOTO").captureToImage().toPixelMap()[8, 8]
                pixel.blue > 0.8f && pixel.red < 0.05f && pixel.green < 0.05f
            }
            compose.onNodeWithTag("customize-new-tab").performClick()
            val cancelPicker = instrumentation.addMonitor(IntentFilter("android.provider.action.PICK_IMAGES").apply { addDataType("image/*") },
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                // The selected Photo option keeps the accessible replacement action.
                val photo = compose.onNodeWithTag("new-tab-option-PHOTO").performScrollTo().assertIsSelected()
                assertEquals(compose.activity.getString(R.string.ui_new_tab_choose_photo),
                    photo.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
                photo.performClick()
                compose.waitUntil(5_000) { cancelPicker.hits == 1 }
                assertEquals(name, container.settings.settings.first().newTabPhoto)
                assertEquals(NewTabBackground.PHOTO, container.settings.settings.first().newTabBackground)
                assertTrue(copy.isFile)
                // Switching back from another style reuses the saved photo without a picker.
                compose.onNodeWithTag("new-tab-option-DEFAULT").performClick()
                compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().newTabBackground == NewTabBackground.DEFAULT } }
                compose.onNodeWithTag("new-tab-option-DEFAULT").assertIsSelected()
                compose.onNodeWithTag("new-tab-option-PHOTO").performClick()
                compose.waitUntil(5_000) { runBlocking { container.settings.settings.first().newTabBackground == NewTabBackground.PHOTO } }
                assertEquals(1, cancelPicker.hits)
                assertEquals(name, container.settings.settings.first().newTabPhoto)
                compose.onNodeWithText("Done").performClick()
            } finally { instrumentation.removeMonitor(cancelPicker) }
            val invalid = File(compose.activity.cacheDir, "invalid-background.txt").apply { writeText("invalid image") }
            try {
                try { container.newTabPhotos.import(Uri.fromFile(invalid)); fail("Invalid image must fail") }
                catch (_: IllegalArgumentException) { }
                assertTrue(copy.isFile)
                assertEquals(name, container.settings.settings.first().newTabPhoto)
            } finally { invalid.delete() }
        } finally { source.delete(); copy.delete() }
    }
}
