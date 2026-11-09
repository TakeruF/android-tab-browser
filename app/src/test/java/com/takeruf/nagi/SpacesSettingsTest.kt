package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
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
class SpacesSettingsTest {
    @Test fun spacesDefaultOnAndDisabledChoiceSurvivesOtherUpdatesAndReopening() = runBlocking {
        val file = java.io.File.createTempFile("spaces-enabled", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            // An installation with other saved settings predating the new preference.
            data.edit { it[booleanPreferencesKey("restore_tabs")] = false }
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertTrue(settings.settings.first().spacesEnabled)
            settings.update { it.copy(spacesEnabled = false) }
            settings.update { it.copy(sidebarCollapsed = true) }
            assertFalse(settings.settings.first().spacesEnabled)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertFalse(reopened.settings.first().spacesEnabled)
            assertTrue(reopened.settings.first().sidebarCollapsed)
            assertFalse(reopened.settings.first().restoreTabs)
            reopened.update { it.copy(spacesEnabled = true) }
            assertTrue(reopened.settings.first().spacesEnabled)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
