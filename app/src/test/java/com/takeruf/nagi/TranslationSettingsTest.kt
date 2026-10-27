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
class TranslationSettingsTest {
    @Test fun lastTargetSurvivesUnrelatedUpdatesAndReopeningTheDataStore() = runBlocking {
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = java.io.File.createTempFile("translation-settings", ".preferences_pb").apply { delete() }
        fun open() = SettingsStore(RuntimeEnvironment.getApplication(),
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
        try {
            var settings = open()
            assertNull(settings.settings.first().translationTargetLanguage)
            settings.update { it.copy(translationTargetLanguage = "ja", alwaysTranslateLanguages = mapOf("en" to "ja", "fr" to "ko")) }
            settings.update { it.copy(sidebarCollapsed = true) }
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = open()
            assertEquals("ja", settings.settings.first().translationTargetLanguage)
            assertEquals(mapOf("en" to "ja", "fr" to "ko"), settings.settings.first().alwaysTranslateLanguages)
            assertTrue(settings.settings.first().sidebarCollapsed)
            settings.update { it.copy(translationTargetLanguage = "ko", alwaysTranslateLanguages = mapOf("en" to "zh")) }
            settings.update { it.copy(sidebarCollapsed = false) }
            scope.coroutineContext.job.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            settings = open()
            assertEquals("ko", settings.settings.first().translationTargetLanguage)
            assertEquals(mapOf("en" to "zh"), settings.settings.first().alwaysTranslateLanguages)
            assertFalse(settings.settings.first().sidebarCollapsed)
        } finally { scope.coroutineContext.job.cancelAndJoin(); file.delete() }
    }
}
