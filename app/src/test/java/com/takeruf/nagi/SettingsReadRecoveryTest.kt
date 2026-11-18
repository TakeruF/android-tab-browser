package com.takeruf.nagi

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.takeruf.nagi.data.datastore.SettingsStore
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.CommandBarSettings
import com.takeruf.nagi.domain.model.ThemeMode
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class SettingsReadRecoveryTest {
    private val hidden = CommandBarSettings(false, false, false, false, false)
    private fun saved(theme: String = "DARK") = preferencesOf(
        booleanPreferencesKey("command_bar_search") to false,
        booleanPreferencesKey("command_bar_tabs") to false,
        booleanPreferencesKey("command_bar_history") to false,
        booleanPreferencesKey("command_bar_spaces") to false,
        booleanPreferencesKey("command_bar_commands") to false,
        stringPreferencesKey("theme") to theme,
        booleanPreferencesKey("ai_engines_seeded_v1") to true,
        booleanPreferencesKey("additional_engines_seeded_v1") to true,
        booleanPreferencesKey("regional_engine_seeded") to true,
    )

    private fun settings(source: Flow<Preferences>) = SettingsStore(RuntimeEnvironment.getApplication(),
        object : DataStore<Preferences> {
            override val data = source
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("Read recovery must not write or reset preferences")
        })

    @Test fun transientStartupReadFailureRecoversSavedChoicesWithoutEmittingDefaults() = runTest {
        var attempts = 0
        val settings = settings(flow {
            if (++attempts == 1) throw IOException("Temporary storage read failure")
            emit(saved())
        })
        val restored = settings.settings.first()
        assertEquals(hidden, restored.commandBar)
        assertEquals(ThemeMode.DARK, restored.theme)
        assertEquals(2, attempts)
    }

    @Test fun readFailureAfterSavedValuesKeepsTheSubscriptionAliveForLaterUpdates() = runTest {
        var attempts = 0
        val settings = settings(flow {
            if (++attempts == 1) {
                emit(saved())
                throw IOException("Temporary storage read failure")
            }
            emit(saved("LIGHT"))
            awaitCancellation()
        })
        val observed = settings.settings.take(2).toList()
        assertEquals(listOf(hidden, hidden), observed.map { it.commandBar })
        assertEquals(listOf(ThemeMode.DARK, ThemeMode.LIGHT), observed.map { it.theme })
        assertEquals(2, attempts)
    }

    @Test fun startupSeedingReadsAlsoRecoverWithoutTreatingAnExistingStoreAsNew() = runTest {
        var attempts = 0
        val settings = settings(flow {
            if (++attempts == 1) throw IOException("Temporary storage read failure")
            emit(saved())
        })
        assertTrue(settings.aiEnginesSeeded())
        assertTrue(settings.additionalEnginesSeeded())
        assertTrue(settings.regionalEngineSeeded())
    }

    @Test fun persistentReadFailureDoesNotEmitDefaultsAndRetriesStopWithTheCollector() = runTest {
        var attempts = 0
        val settings = settings(flow {
            attempts++
            throw IOException("Storage unavailable")
        })
        val observed = mutableListOf<BrowserSettings>()
        val collector = launch { settings.settings.toList(observed) }
        runCurrent()
        advanceTimeBy(5_000)
        assertTrue(attempts > 1)
        assertTrue(observed.isEmpty())
        collector.cancelAndJoin()
        val stoppedAt = attempts
        advanceTimeBy(5_000)
        assertEquals(stoppedAt, attempts)
    }

    @Test fun nonIoReadErrorsAreNotHiddenOrRetried() = runTest {
        var attempts = 0
        val failure = IllegalStateException("Invalid preference type")
        val settings = settings(flow {
            attempts++
            throw failure
        })
        val actual = runCatching { settings.settings.first() }.exceptionOrNull()
        assertSame(failure, actual)
        assertEquals(1, attempts)
    }

    @Test fun corruptPreferencesAreReportedInsteadOfResetOrRetriedForever() = runTest {
        var attempts = 0
        val failure = CorruptionException("Invalid preference data")
        val settings = settings(flow {
            attempts++
            throw failure
        })
        val actual = runCatching { settings.settings.first() }.exceptionOrNull()
        assertSame(failure, actual)
        assertEquals(1, attempts)
    }
}
