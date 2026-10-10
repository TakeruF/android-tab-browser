package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.takeruf.nagi.data.datastore.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoSettingsTest {
    @Test fun oldInstallDefaultsOnAndIndependentChoicesSurviveOtherUpdatesAndReopening() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = java.io.File.createTempFile("video-settings", ".preferences_pb").apply { delete() }
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertTrue(settings.settings.first().autoVideoPip)
            assertTrue(settings.settings.first().videoPopups)
            settings.update { it.copy(autoVideoPip = false) }
            assertTrue(settings.settings.first().videoPopups)
            settings.update { it.copy(videoPopups = false, sidebarCollapsed = true) }
            settings.update { it.copy(autoVideoPip = true) }
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data).settings.first()
            assertTrue(reopened.autoVideoPip)
            assertFalse(reopened.videoPopups)
            assertTrue(reopened.sidebarCollapsed)
        } finally { scope.cancel(); file.delete() }
    }
}
