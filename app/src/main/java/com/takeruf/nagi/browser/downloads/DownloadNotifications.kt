package com.takeruf.nagi.browser.downloads

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.DownloadProgress
import com.takeruf.nagi.browser.engine.GeneratedDownload
import com.takeruf.nagi.ui.localization.NagiStrings
import java.text.NumberFormat
import java.util.UUID

/** HTTP transfers use DownloadManager notifications; this covers page-generated files. */
class DownloadNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val strings = NagiStrings(context)

    fun start(request: GeneratedDownload): DownloadProgress {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, strings(R.string.ui_downloads), NotificationManager.IMPORTANCE_LOW))
        return Transfer(request).also { it.update(0) }
    }

    private inner class Transfer(private val request: GeneratedDownload) : DownloadProgress {
        private val tag = "page-download-${UUID.randomUUID()}"
        private var finished = false
        private var lastUpdate: Long? = null
        private val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(safeDownloadName(request.name))
            .setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(DownloadManager.ACTION_VIEW_DOWNLOADS), PendingIntent.FLAG_IMMUTABLE))

        override fun update(bytes: Long) {
            if (finished) return
            val now = SystemClock.elapsedRealtime()
            if (bytes < request.size && lastUpdate?.let { now - it < 400 } == true) return
            lastUpdate = now
            val current = bytes.coerceIn(0, request.size)
            val fraction = if (request.size == 0L) 1.0 else current.toDouble() / request.size
            val text = if (current == request.size) strings(R.string.ui_download_saving) else strings(
                R.string.ui_download_progress, NumberFormat.getPercentInstance().format(fraction),
                Formatter.formatShortFileSize(context, current), Formatter.formatShortFileSize(context, request.size))
            post(builder.setContentText(text).setOngoing(true).setProgress(100, (fraction * 100).toInt(), false))
        }

        override fun finish(saved: Boolean) {
            if (finished) return
            finished = true
            post(builder.setSmallIcon(if (saved) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                .setContentText(strings(if (saved) R.string.ui_download_saved else R.string.ui_generated_download_failed))
                .setOngoing(false).setAutoCancel(true).setProgress(0, 0, false))
        }

        override fun cancel() {
            if (finished) return
            finished = true
            manager.cancel(tag, 0)
        }

        private fun post(notification: NotificationCompat.Builder) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
            // Changing permission in Settings during a transfer must not fail the file save.
            try { manager.notify(tag, 0, notification.build()) } catch (_: SecurityException) { }
        }
    }

    /**
     * Result of an HTTP download moved into a chosen folder. [folder] is the folder's name,
     * "" when it fell back to Downloads, and null with a null [uri] when saving failed.
     */
    fun folderResult(name: String, uri: android.net.Uri?, folder: String?) {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, strings(R.string.ui_downloads), NotificationManager.IMPORTANCE_LOW))
        val open = if (uri != null) Intent(Intent.ACTION_VIEW).setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            else Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val text = when {
            uri == null -> strings(R.string.ui_download_failed_1_s, name)
            folder.isNullOrEmpty() -> strings(R.string.ui_download_saved)
            else -> strings(R.string.ui_download_saved_to_1_s, folder)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (uri != null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(safeDownloadName(name)).setContentText(text).setAutoCancel(true).setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, name.hashCode(), open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try { manager.notify("folder-download-${UUID.randomUUID()}", 0, builder.build()) } catch (_: SecurityException) { }
    }

    private companion object { const val CHANNEL = "page-downloads" }
}
