package com.takeruf.nagi

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import com.takeruf.nagi.browser.engine.FileSelectionRequest
import com.takeruf.nagi.browser.engine.NativeBrowserHost
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FilePickerContractTest {
    private fun picker(types: List<String>, multiple: Boolean, capture: Boolean = false): Intent {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        return try {
            org.robolectric.Shadows.shadowOf(activity.application).grantPermissions(android.Manifest.permission.CAMERA)
            host.chooseFiles(FileSelectionRequest(types, multiple, capture)) {}
            requireNotNull(shadowOf(activity).nextStartedActivity)
        } finally {
            host.dispose()
            controller.pause().stop().destroy()
        }
    }

    @Test fun explicitMimeTypesAndMultipleSelectionReachSystemPicker() {
        val intent = picker(listOf("image/png", "application/pdf"), true)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(arrayOf("image/png", "application/pdf"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
    }

    @Test fun pdfExtensionAcceptRestrictsPickerToPdfFiles() {
        val intent = picker(listOf(".pdf"), false)
        assertEquals("application/pdf", intent.type)
    }

    @Test fun capturePreferenceForNonImageStillUsesDocumentPicker() {
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker(listOf("application/pdf"), false, true).action)
    }

    @Test fun cameraResultReturnsFullSizePhotoAndCancellationReturnsNull() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        shadowOf(activity.application).grantPermissions(android.Manifest.permission.CAMERA)
        try {
            var completed = false
            var result: List<String>? = null
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false, true)) { completed = true; result = it }
            val launched = requireNotNull(shadowOf(activity).nextStartedActivityForResult)
            val uri = launched.intent.getParcelableExtra<android.net.Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!
            assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, launched.intent.action)
            assertEquals("content", uri.scheme)
            assertTrue(uri.authority!!.endsWith(".uploads"))
            assertTrue(uri.path!!.startsWith("/camera_uploads/"))
            activity.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            activity.activityResultRegistry.dispatchResult(launched.requestCode, android.app.Activity.RESULT_OK, null)
            assertTrue(completed)
            assertEquals(listOf(uri.toString()), result)
            assertArrayEquals(byteArrayOf(1, 2, 3), activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
            completed = false
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false, true)) { completed = true; result = it }
            val cancelled = requireNotNull(shadowOf(activity).nextStartedActivityForResult)
            activity.activityResultRegistry.dispatchResult(cancelled.requestCode, android.app.Activity.RESULT_CANCELED, null)
            assertTrue(completed); assertNull(result)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }
}
