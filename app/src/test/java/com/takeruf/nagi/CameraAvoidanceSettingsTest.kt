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
class CameraAvoidanceSettingsTest {
    @Test fun cameraAvoidanceDefaultsOffAndSurvivesUpdatesAndReopening() = runBlocking {
        val file = java.io.File.createTempFile("camera-avoidance", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            // An installation with other saved settings predating the new preference.
            data.edit { it[booleanPreferencesKey("restore_tabs")] = false }
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertFalse(settings.settings.first().avoidCameraOverlap)
            assertEquals(32, settings.settings.first().cameraSideMargin)
            assertNull(settings.settings.first().cameraLeftRotation)
            settings.update { it.copy(avoidCameraOverlap = true, cameraSideMargin = 48, cameraLeftRotation = 2) }
            settings.update { it.copy(sidebarCollapsed = true) }
            assertTrue(settings.settings.first().avoidCameraOverlap)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertTrue(reopened.settings.first().avoidCameraOverlap)
            assertEquals(48, reopened.settings.first().cameraSideMargin)
            assertEquals(2, reopened.settings.first().cameraLeftRotation)
            assertTrue(reopened.settings.first().sidebarCollapsed)
            assertFalse(reopened.settings.first().restoreTabs)
            reopened.update { it.copy(avoidCameraOverlap = false) }
            assertFalse(reopened.settings.first().avoidCameraOverlap)
            assertEquals(2, reopened.settings.first().cameraLeftRotation)
            reopened.update { it.copy(cameraSideMargin = 999) }
            assertEquals(64, reopened.settings.first().cameraSideMargin)
            reopened.update { it.copy(cameraLeftRotation = 99) }
            assertNull(reopened.settings.first().cameraLeftRotation)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
