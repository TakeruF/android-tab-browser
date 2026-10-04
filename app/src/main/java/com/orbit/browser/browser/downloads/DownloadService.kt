package com.orbit.browser.browser.downloads

import com.orbit.browser.R
import com.orbit.browser.ui.localization.OrbitStrings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.orbit.browser.browser.engine.DownloadRequest

class DownloadService(private val context: Context) {
    private val strings = OrbitStrings(context)
    fun enqueue(download: DownloadRequest): Long {
        val filename = download.suggestedName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(160).ifBlank { "download" }
        val request = DownloadManager.Request(Uri.parse(download.url))
            .setTitle(filename).setDescription(strings(R.string.ui_downloaded_with_orbit))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", download.userAgent)
        download.cookies?.let { request.addRequestHeader("Cookie", it) }
        download.mimeType?.let(request::setMimeType)
        if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
