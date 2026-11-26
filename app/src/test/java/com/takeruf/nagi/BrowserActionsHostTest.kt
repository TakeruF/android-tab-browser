package com.takeruf.nagi

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import com.takeruf.nagi.browser.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BrowserActionsHostTest {
    @Test fun contextActionsExecuteOnceAndStalePromptsCannotRun() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val host = NativeBrowserHost(controller.get())
        controller.start().resume()
        try {
            var selected = 0
            host.showContextMenu("https://example.com/link", listOf(PageContextAction("Copy link") { selected++ }))
            val stale = host.browserActionPrompts.value!!
            host.showContextMenu("https://example.com/image", listOf(PageContextAction("Save image") { selected += 10 }))
            val current = host.browserActionPrompts.value!!
            host.chooseBrowserAction(stale, 0)
            host.dismissBrowserActions(stale)
            assertSame(current, host.browserActionPrompts.value)
            host.chooseBrowserAction(current, 0)
            host.chooseBrowserAction(current, 0)
            assertEquals(10, selected)
            assertNull(host.browserActionPrompts.value)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun uploadChoiceUsesDocumentPickerAndPreservesItsImageFilter() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false)) {}
            val prompt = host.browserActionPrompts.value!!
            assertEquals(2, prompt.actions.size)
            host.chooseBrowserAction(prompt, 1)
            val intent = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
            assertEquals("image/*", intent.type)
            assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            assertNull(host.browserActionPrompts.value)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun photoPermissionDenialAndChooserDismissalFinishTheFileRequestOnce() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        shadowOf(activity.application).denyPermissions(Manifest.permission.CAMERA)
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        var disposed = false
        try {
            val replies = mutableListOf<List<String>?>()
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false), replies::add)
            host.chooseBrowserAction(host.browserActionPrompts.value!!, 0)
            val permission = shadowOf(activity).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.CAMERA), permission.requestedPermissions)
            activity.onRequestPermissionsResult(permission.requestCode, permission.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
            assertEquals(listOf<List<String>?>(null), replies)
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false), replies::add)
            val prompt = host.browserActionPrompts.value!!
            host.dismissBrowserActions(prompt)
            host.dismissBrowserActions(prompt)
            assertEquals(listOf<List<String>?>(null, null), replies)
            host.chooseFiles(FileSelectionRequest(listOf("image/*"), false), replies::add)
            host.dispose()
            disposed = true
            assertEquals(listOf<List<String>?>(null, null, null), replies)
        } finally { if (!disposed) host.dispose(); controller.pause().stop().destroy() }
    }

    private val download = DownloadRequest("https://example.com/file.pdf", "Nagi QA", null, "application/pdf", null, "file.pdf")

    @Test fun chosenFolderHttpDownloadRequestsNotificationsButDenialStillDownloads() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        shadowOf(activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val tree = Uri.parse("content://com.example.documents/tree/qa")
        activity.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val host = NativeBrowserHost(activity)
        host.downloadFolder = tree.toString()
        controller.start().resume()
        try {
            var accepted: Boolean? = null
            host.download(download) { accepted = it }
            host.confirmDownload(host.downloadConfirmations.value.single())
            val permission = shadowOf(activity).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), permission.requestedPermissions)
            assertNull(accepted)
            activity.onRequestPermissionsResult(permission.requestCode, permission.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
            assertEquals(true, accepted)
            assertEquals(1, shadowOf(activity.getSystemService(DownloadManager::class.java)).requestCount)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }

    @Test fun ordinaryHttpDownloadsKeepDownloadManagerNotificationsWithoutAnAppPrompt() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        shadowOf(activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            var accepted: Boolean? = null
            host.download(download) { accepted = it }
            host.confirmDownload(host.downloadConfirmations.value.single())
            assertEquals(true, accepted)
            assertNull(shadowOf(activity).lastRequestedPermission)
            assertEquals(1, shadowOf(activity.getSystemService(DownloadManager::class.java)).requestCount)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }
}
