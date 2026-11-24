package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.*
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.domain.model.*
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
class CommandBarLayoutSettingsTest {
    @Test fun upgradesAndUnknownLayoutsUseTheNewDesign() = runBlocking {
        val data = com.takeruf.nagi.browser.privacy.MemoryPreferences()
        data.edit { it[booleanPreferencesKey("command_bar_history")] = false }
        val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
        assertEquals(CommandBarLayout.ANCHORED, settings.settings.first().commandBarLayout)
        assertFalse(settings.settings.first().commandBarLayoutNoticeDismissed)
        data.edit { it[stringPreferencesKey("command_bar_layout")] = "UNKNOWN" }
        assertEquals(CommandBarLayout.ANCHORED, settings.settings.first().commandBarLayout)
        settings.update { it.copy(bottomSearchBar = true) }
        assertFalse(settings.settings.first().commandBar.showHistory)
    }

    @Test fun explicitChoiceSurvivesConcurrentUpdatesAndDiskReopening() = runBlocking {
        val file = java.io.File.createTempFile("command-layout", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            settings.update { it.copy(commandBarLayout = CommandBarLayout.CENTERED, commandBarLayoutNoticeDismissed = true) }
            coroutineScope {
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showHistory = false)) } }
                launch { settings.update { it.copy(compactUiMode = CompactUiMode.ON) } }
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertEquals(CommandBarLayout.CENTERED, reopened.settings.first().commandBarLayout)
            assertTrue(reopened.settings.first().commandBarLayoutNoticeDismissed)
            assertFalse(reopened.settings.first().commandBar.showHistory)
            assertEquals(CompactUiMode.ON, reopened.settings.first().compactUiMode)
            reopened.update { it.copy(commandBarLayout = CommandBarLayout.ANCHORED) }
            assertEquals(CommandBarLayout.ANCHORED, reopened.settings.first().commandBarLayout)
            assertTrue(reopened.settings.first().commandBarLayoutNoticeDismissed)
            assertFalse(reopened.settings.first().commandBar.showHistory)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
