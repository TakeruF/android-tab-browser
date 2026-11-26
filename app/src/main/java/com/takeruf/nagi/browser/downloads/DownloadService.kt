package com.takeruf.nagi.browser.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.takeruf.nagi.browser.engine.DownloadRequest
import com.takeruf.nagi.browser.engine.GeneratedDownload
import java.io.File

class DownloadService(private val context: Context, private val folder: () -> Uri? = { null }) {
    /** The chosen folder while Nagi can still write to it; otherwise null (system Downloads). */
    private fun usableFolder(): Uri? = folder()?.takeIf { DownloadFolder.writable(context, it) }

    /** These downloads need Nagi's notification after the staged file is moved. */
    fun usesChosenFolder(): Boolean = usableFolder() != null

    fun enqueue(download: DownloadRequest): Long {
        val filename = download.suggestedName.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(160).ifBlank { "download" }
        val request = DownloadManager.Request(Uri.parse(download.url))
            // A fixed description masks DownloadManager's remaining-time text.
            .setTitle(filename)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", download.userAgent)
        download.cookies?.let { request.addRequestHeader("Cookie", it) }
        download.mimeType?.let(request::setMimeType)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val chosen = usableFolder()
        if (chosen != null) {
            // DownloadManager cannot write into a SAF tree: stage the file in app storage and
            // let FolderDownloadReceiver move it, which also posts the completion notice.
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            request.setDestinationInExternalFilesDir(context, PENDING_DIRECTORY, "${System.nanoTime()}-$filename")
            return manager.enqueue(request).also { id ->
                PendingFolderDownloads.put(context, id, PendingFolderDownloads.Job(chosen, filename, download.mimeType))
            }
        }
        if (Build.VERSION.SDK_INT >= 29) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        else request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        return manager.enqueue(request)
    }

    /** Name of the folder that receives downloads, or null for the system Downloads folder. */
    fun destinationName(): String? = usableFolder()?.let { DownloadFolder.displayName(context, it) }

    /** Called on an IO dispatcher after the user accepts a page-generated download. */
    fun saveGenerated(download: GeneratedDownload, source: File): Uri {
        val filename = safeDownloadName(download.name)
        usableFolder()?.let { chosen ->
            runCatching { return source.inputStream().use { DownloadFolder.write(context, chosen, filename, download.mimeType, it) } }
        }
        return source.inputStream().use { DownloadFolder.writeToDownloads(context, filename, download.mimeType, it) }
    }

    private companion object { const val PENDING_DIRECTORY = "pending-downloads" }
}

fun safeDownloadName(name: String): String = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim().take(160).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download"
