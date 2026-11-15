package com.takeruf.nagi

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.takeruf.nagi.browser.downloads.PendingFolderDownloads
import com.takeruf.nagi.data.datastore.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadFolderSettingsTest {
    private val folder = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FNagi"

    @Test fun defaultsToDownloadsAndKeepsAChosenFolderAcrossReopening() = runBlocking {
        val file = java.io.File.createTempFile("download-folder", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var settings = SettingsStore(RuntimeEnvironment.getApplication(),
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            // Existing users keep the system Downloads folder.
            assertNull(settings.settings.first().downloadFolder)
            settings.update { it.copy(downloadFolder = folder) }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = SettingsStore(RuntimeEnvironment.getApplication(),
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
            assertEquals(folder, settings.settings.first().downloadFolder)
            // Unrelated updates leave the folder alone; resetting removes it.
            settings.update { it.copy(restoreTabs = false) }
            assertEquals(folder, settings.settings.first().downloadFolder)
            settings.update { it.copy(downloadFolder = null) }
            assertNull(settings.settings.first().downloadFolder)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun aPendingFolderDownloadIsHandedOutOnlyOnce() {
        val context = RuntimeEnvironment.getApplication()
        val job = PendingFolderDownloads.Job(Uri.parse(folder), "report.pdf", "application/pdf")
        PendingFolderDownloads.put(context, 42, job)
        assertEquals(job, PendingFolderDownloads.take(context, 42))
        // A repeated DOWNLOAD_COMPLETE broadcast must not move the file a second time.
        assertNull(PendingFolderDownloads.take(context, 42))
        PendingFolderDownloads.put(context, 43, job.copy(mimeType = null))
        assertNull(PendingFolderDownloads.take(context, 43)!!.mimeType)
    }
}
