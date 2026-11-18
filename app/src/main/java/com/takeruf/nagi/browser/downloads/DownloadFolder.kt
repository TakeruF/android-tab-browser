package com.takeruf.nagi.browser.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.takeruf.nagi.data.datastore.SettingsStore
import java.io.File
import java.io.InputStream

/**
 * A user-chosen download folder (a Storage Access Framework tree). Files are written with
 * DocumentsContract so no extra library is needed; the persisted grant is taken in Settings.
 */
object DownloadFolder {
    /** Keep the old grant until saving succeeds, and while normal browsing still uses it. */
    suspend fun rememberSelection(settings: SettingsStore, folder: String?,
        releasePrevious: (String) -> Unit, retainedFolder: suspend () -> String? = { null }) {
        var previous: String? = null
        settings.update {
            previous = it.downloadFolder
            it.copy(downloadFolder = folder)
        }
        previous?.takeIf { it != folder && it != retainedFolder() }?.let(releasePrevious)
    }

    /** True while Nagi still holds a persisted write grant for [tree]. */
    fun writable(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    fun displayName(context: Context, tree: Uri): String? = runCatching {
        val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** Writes [input] as a new document; the provider resolves name clashes. Returns its URI. */
    fun write(context: Context, tree: Uri, name: String, mimeType: String?, input: InputStream): Uri {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = requireNotNull(DocumentsContract.createDocument(resolver, parent,
            mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream", name)) { "The download folder is unavailable" }
        try {
            requireNotNull(resolver.openOutputStream(document)) { "The download folder is unavailable" }.use { input.copyTo(it) }
            return document
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw error
        }
    }

    /** The system Downloads folder, as before a folder was chosen. */
    fun writeToDownloads(context: Context, name: String, mimeType: String?, input: InputStream): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                mimeType?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            try {
                requireNotNull(resolver.openOutputStream(uri)).use { input.copyTo(it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri
            } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
        }
        val directory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS))
        var target = File(directory, name)
        var counter = 1
        while (!target.createNewFile()) target = File(directory, "${counter++}-$name")
        try { target.outputStream().use { input.copyTo(it) } } catch (error: Exception) { target.delete(); throw error }
        return Uri.fromFile(target)
    }
}

/**
 * HTTP downloads bound for a chosen folder: DownloadManager writes them to app storage first
 * (it cannot write to SAF trees), and [FolderDownloadReceiver] moves them on completion.
 * This is transient job bookkeeping, not a user setting, so it lives apart from DataStore.
 */
internal object PendingFolderDownloads {
    data class Job(val folder: Uri, val name: String, val mimeType: String?)

    private fun prefs(context: Context) = context.getSharedPreferences("pending-folder-downloads", Context.MODE_PRIVATE)

    fun put(context: Context, id: Long, job: Job) {
        prefs(context).edit().putString(id.toString(),
            listOf(job.folder.toString(), job.name, job.mimeType.orEmpty()).joinToString("\n")).commit()
    }

    /** Removes and returns the job, so a repeated broadcast cannot move the file twice. */
    @Synchronized fun take(context: Context, id: Long): Job? {
        val stored = prefs(context).getString(id.toString(), null) ?: return null
        prefs(context).edit().remove(id.toString()).commit()
        val parts = stored.split("\n")
        if (parts.size < 3) return null
        return Job(Uri.parse(parts[0]), parts[1], parts[2].ifBlank { null })
    }
}

class FolderDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val app = context.applicationContext
        val job = PendingFolderDownloads.take(app, id) ?: return
        val pending = goAsync()
        Thread {
            try { finish(app, id, job) } finally { pending.finish() }
        }.start()
    }

    private fun finish(context: Context, id: Long, job: PendingFolderDownloads.Job) {
        val manager = context.getSystemService(DownloadManager::class.java)
        val notifications = DownloadNotifications(context)
        val (successful, local) = manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) false to null
            else (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) to
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))?.let(Uri::parse)
        }
        if (!successful || local == null) {
            manager.remove(id)
            notifications.folderResult(job.name, null, null)
            return
        }
        val saved = runCatching {
            context.contentResolver.openInputStream(local)!!.use { input ->
                if (DownloadFolder.writable(context, job.folder)) {
                    DownloadFolder.write(context, job.folder, job.name, job.mimeType, input) to DownloadFolder.displayName(context, job.folder)
                } else DownloadFolder.writeToDownloads(context, job.name, job.mimeType, input) to null
            }
        }.recoverCatching {
            // The chosen folder failed mid-copy (removed or revoked): keep the file in Downloads.
            context.contentResolver.openInputStream(local)!!.use { input ->
                DownloadFolder.writeToDownloads(context, job.name, job.mimeType, input) to null
            }
        }.getOrNull()
        // Removing the DownloadManager entry also deletes the temporary app-storage copy,
        // so keep it if nothing could be written.
        if (saved != null) manager.remove(id)
        notifications.folderResult(job.name, saved?.first, saved?.let { it.second ?: "" })
    }
}
