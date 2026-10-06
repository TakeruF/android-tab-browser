package com.takeruf.nagi.browser.downloads

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.NagiStrings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.takeruf.nagi.browser.engine.DownloadRequest
import com.takeruf.nagi.browser.engine.GeneratedDownload
import android.content.ContentValues
import android.provider.MediaStore
import java.io.File

class DownloadService(private val context: Context) {
    private val strings = NagiStrings(context)
    fun enqueue(download: DownloadRequest): Long {
        val filename = download.suggestedName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(160).ifBlank { "download" }
        val request = DownloadManager.Request(Uri.parse(download.url))
            .setTitle(filename).setDescription(strings(R.string.ui_downloaded_with_nagi))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", download.userAgent)
        download.cookies?.let { request.addRequestHeader("Cookie", it) }
        download.mimeType?.let(request::setMimeType)
        if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        return (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
    /** Called on an IO dispatcher after the user accepts a page-generated download. */
    fun saveGenerated(download: GeneratedDownload, source: File): Uri {
        val filename = safeDownloadName(download.name)
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, download.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            try {
                requireNotNull(resolver.openOutputStream(uri)).use { output -> source.inputStream().use { it.copyTo(output) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
        }
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        var target = File(directory, filename)
        var counter = 1
        while (!target.createNewFile()) target = File(directory, "${counter++}-$filename")
        try { source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } } }
        catch (error: Exception) { target.delete(); throw error }
        return Uri.fromFile(target)
    }
}

fun safeDownloadName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim().take(160).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download"
