package com.takeruf.nagi

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.domain.model.ThemeMode
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
class PageDarkeningSettingsTest {
    @Test fun optInAndSiteExceptionsSurviveOtherUpdatesReopeningAndDisabling() = runBlocking {
        val file = java.io.File.createTempFile("page-darkening", ".preferences_pb").apply { delete() }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            data.edit { it[booleanPreferencesKey("restore_tabs")] = false }
            val settings = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertFalse(settings.settings.first().adaptiveDarkMode)
            assertTrue(settings.settings.first().darkModeExcludedHosts.isEmpty())
            settings.update { it.copy(adaptiveDarkMode = true) }
            coroutineScope {
                launch { settings.update { it.copy(darkModeExcludedHosts = it.darkModeExcludedHosts + "example.com") } }
                launch { settings.update { it.copy(theme = ThemeMode.DARK) } }
            }
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            data = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            val reopened = SettingsStore(RuntimeEnvironment.getApplication(), data)
            assertTrue(reopened.settings.first().adaptiveDarkMode)
            assertEquals(setOf("example.com"), reopened.settings.first().darkModeExcludedHosts)
            assertEquals(ThemeMode.DARK, reopened.settings.first().theme)
            assertFalse(reopened.settings.first().restoreTabs)
            reopened.update { it.copy(adaptiveDarkMode = false) }
            assertFalse(reopened.settings.first().adaptiveDarkMode)
            assertEquals(setOf("example.com"), reopened.settings.first().darkModeExcludedHosts)
            reopened.update { it.copy(darkModeExcludedHosts = it.darkModeExcludedHosts - "example.com") }
            assertTrue(reopened.settings.first().darkModeExcludedHosts.isEmpty())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }
}
