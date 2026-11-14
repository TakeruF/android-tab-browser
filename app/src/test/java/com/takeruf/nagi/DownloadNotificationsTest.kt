package com.takeruf.nagi

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.takeruf.nagi.browser.downloads.DownloadNotifications
import com.takeruf.nagi.browser.engine.GeneratedDownload
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class DownloadNotificationsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val request = GeneratedDownload("table.csv", "text/csv", 120000, "https://example.com")

    @Test fun progressBecomesCompletionOnlyAfterFileIsSaved() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val transfer = DownloadNotifications(context).start(request)
        var notification = manager.activeNotifications.single().notification
        assertEquals("table.csv", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(100, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        transfer.update(request.size)
        notification = manager.activeNotifications.single().notification
        assertEquals(100, notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        transfer.finish(true)
        notification = manager.activeNotifications.single().notification
        assertEquals(context.getString(R.string.ui_download_saved), notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(0, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(0, notification.flags and Notification.FLAG_ONGOING_EVENT)
        transfer.cancel() // Page cleanup must preserve the saved notification.
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test fun failureAndCancellationDoNotAffectOtherTransfers() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val notifications = DownloadNotifications(context)
        val failed = notifications.start(request)
        val cancelled = notifications.start(request)
        assertEquals(2, manager.activeNotifications.size)
        cancelled.cancel()
        failed.finish(false)
        assertEquals(context.getString(R.string.ui_generated_download_failed),
            manager.activeNotifications.single().notification.extras.getString(Notification.EXTRA_TEXT))
        cancelled.update(50) // Late progress cannot revive a cancelled notification.
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test @Config(sdk = [35]) fun deniedPermissionDoesNotPreventTransferLifecycle() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val progress = DownloadNotifications(context).start(request)
        progress.update(request.size)
        progress.finish(true)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
