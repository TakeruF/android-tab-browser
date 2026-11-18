package com.takeruf.nagi

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.takeruf.nagi.browser.downloads.DownloadFolder
import com.takeruf.nagi.browser.downloads.PendingFolderDownloads
import com.takeruf.nagi.browser.privacy.MemoryPreferences
import com.takeruf.nagi.data.datastore.SettingsStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun replacingOrResettingAFolderReleasesOnlyAfterSavingAndReselectingKeepsItsGrant() = runBlocking {
        val settings = SettingsStore(RuntimeEnvironment.getApplication(), MemoryPreferences())
        settings.update { it.copy(downloadFolder = folder, bottomSearchBar = true) }
        val other = "$folder-other"
        val released = mutableListOf<String>()
        DownloadFolder.rememberSelection(settings, other, releasePrevious = { previous ->
            assertEquals(other, runBlocking { settings.settings.first().downloadFolder })
            released += previous
        })
        assertEquals(listOf(folder), released)
        DownloadFolder.rememberSelection(settings, other, releasePrevious = { released += it })
        assertEquals(listOf(folder), released)
        DownloadFolder.rememberSelection(settings, null, releasePrevious = { previous ->
            assertNull(runBlocking { settings.settings.first().downloadFolder })
            released += previous
        })
        assertEquals(listOf(folder, other), released)
        assertTrue(settings.settings.first().bottomSearchBar)
    }

    @Test fun failedFolderSaveLeavesThePreviousChoiceAndGrantUsable() = runBlocking {
        val data = MemoryPreferences()
        val context = RuntimeEnvironment.getApplication()
        val original = SettingsStore(context, data)
        original.update { it.copy(downloadFolder = folder) }
        val failing = object : DataStore<Preferences> by data {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw IOException("Simulated settings write failure")
        }
        val released = mutableListOf<String>()
        val error = runCatching {
            DownloadFolder.rememberSelection(SettingsStore(context, failing), "$folder-other", releasePrevious = { released += it })
        }.exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals(folder, original.settings.first().downloadFolder)
        assertTrue(released.isEmpty())
    }

    @Test fun privateFolderChangesDoNotReleaseTheNormalWorkspacesGrant() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val normal = SettingsStore(context, MemoryPreferences())
        normal.update { it.copy(downloadFolder = folder) }
        val private = SettingsStore(context, MemoryPreferences())
        val initial = normal.settings.first()
        private.update { initial }
        val released = mutableListOf<String>()
        suspend fun choose(value: String?) = DownloadFolder.rememberSelection(private, value,
            releasePrevious = { released += it }, retainedFolder = { normal.settings.first().downloadFolder })
        choose("$folder-private")
        assertTrue(released.isEmpty())
        choose(null)
        assertNull(private.settings.first().downloadFolder)
        assertEquals(folder, normal.settings.first().downloadFolder)
        assertEquals(listOf("$folder-private"), released)
    }
}
