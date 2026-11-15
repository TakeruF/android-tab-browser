package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
class CommandBarSettingsTest {
    @Test fun defaultsAndAtomicUpdatesSurviveReopeningWithoutLosingIndividualChoices() = runBlocking {
        val file = java.io.File.createTempFile("command-bar", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            data.edit { it[booleanPreferencesKey("restore_tabs")] = false }
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertEquals(CommandBarSettings(), settings.settings.first().commandBar)
            coroutineScope {
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showSearch = false)) } }
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showTabs = false)) } }
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showHistory = false)) } }
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showSpaces = false)) } }
                launch { settings.update { it.copy(commandBar = it.commandBar.copy(showCommands = false)) } }
                launch { settings.update { it.copy(theme = ThemeMode.DARK) } }
            }
            val expected = CommandBarSettings(false, false, false, false, false)
            assertEquals(expected, settings.settings.first().commandBar)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertEquals(expected, reopened.settings.first().commandBar)
            assertEquals(ThemeMode.DARK, reopened.settings.first().theme)
            assertFalse(reopened.settings.first().restoreTabs)
            reopened.update { it.copy(commandBar = it.commandBar.copy(showCommands = true)) }
            reopened.update { it.copy(sidebarCollapsed = true) }
            assertEquals(expected.copy(showCommands = true), reopened.settings.first().commandBar)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
