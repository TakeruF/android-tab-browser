package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.domain.model.NewTabBackground
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
class NewTabSettingsTest {
    @Test fun backgroundAndPhotoSurviveUnrelatedUpdatesAndReopening() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = java.io.File.createTempFile("new-tab-settings", ".preferences_pb").apply { delete() }
        val data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertEquals(NewTabBackground.DEFAULT, settings.settings.first().newTabBackground)
            assertNull(settings.settings.first().newTabPhoto)
            settings.update { it.copy(newTabBackground = NewTabBackground.PHOTO,
                newTabPhoto = "12345678-1234-1234-1234-123456789012.jpg", newTabColor = 0xFF123456) }
            settings.update { it.copy(sidebarCollapsed = true, newTabBackground = NewTabBackground.GRADIENT) }
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data).settings.first()
            assertEquals(NewTabBackground.GRADIENT, reopened.newTabBackground)
            assertEquals(0xFF123456, reopened.newTabColor)
            assertEquals("12345678-1234-1234-1234-123456789012.jpg", reopened.newTabPhoto)
            assertTrue(reopened.sidebarCollapsed)
        } finally { scope.cancel(); file.delete() }
    }
}
