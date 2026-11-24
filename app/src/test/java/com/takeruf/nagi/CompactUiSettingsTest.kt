package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.domain.model.CompactUiMode
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
class CompactUiSettingsTest {
    @Test fun modesOverrideKeyboardAvailabilityOnlyWhenExplicitlySelected() {
        for (keyboard in listOf(false, true)) {
            assertFalse(CompactUiMode.OFF.enabled(keyboard))
            assertEquals(keyboard, CompactUiMode.AUTO.enabled(keyboard))
            assertTrue(CompactUiMode.ON.enabled(keyboard))
        }
    }

    @Test fun existingAndUnknownPreferencesDefaultToAutomatic() = runBlocking {
        val data = com.takeruf.nagi.browser.privacy.MemoryPreferences()
        data.edit { it[booleanPreferencesKey("restore_tabs")] = false }
        val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
        assertEquals(CompactUiMode.AUTO, settings.settings.first().compactUiMode)
        data.edit { it[stringPreferencesKey("compact_ui_mode")] = "UNRECOGNIZED" }
        assertEquals(CompactUiMode.AUTO, settings.settings.first().compactUiMode)
        settings.update { it.copy(sidebarCollapsed = true) }
        assertEquals(CompactUiMode.AUTO, settings.settings.first().compactUiMode)
        assertFalse(settings.settings.first().restoreTabs)
    }

    @Test fun selectionSurvivesConcurrentChangesAndReopening() = runBlocking {
        val file = java.io.File.createTempFile("compact-ui", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            for (mode in CompactUiMode.entries) {
                coroutineScope {
                    awaitAll(async { settings.update { it.copy(compactUiMode = mode) } },
                        async { settings.update { it.copy(bottomSearchBar = true, sidebarWidth = 300f) } })
                }
                assertEquals(mode, settings.settings.first().compactUiMode)
                assertTrue(settings.settings.first().bottomSearchBar)
                assertEquals(300f, settings.settings.first().sidebarWidth)
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data).settings.first()
            assertEquals(CompactUiMode.ON, reopened.compactUiMode)
            assertTrue(reopened.bottomSearchBar)
            assertEquals(300f, reopened.sidebarWidth)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
