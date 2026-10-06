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
    private fun picker(types: List<String>, multiple: Boolean): Intent {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        return try {
            host.chooseFiles(FileSelectionRequest(types, multiple)) {}
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
}
