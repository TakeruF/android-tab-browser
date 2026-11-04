package com.takeruf.nagi

import android.app.ActivityManager
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.takeruf.nagi.testing.InputConnectionHarnessIme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PrivateBrowsingLifetimeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun privateActivity(): PrivateActivity {
        var activity: PrivateActivity? = null
        compose.waitUntil(15_000) {
            main { activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<PrivateActivity>().firstOrNull() }
            activity != null
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("close-private-mode")
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        return activity!!
    }
    @Test fun taskOwnsPrivateTabsAndKeyboardUntilExplicitCloseOrTaskRemoval() {
        val server = FixtureServer()
        val normal = instrumentation.startActivitySync(android.content.Intent(instrumentation.targetContext,
            MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val app = normal.application as NagiApplication
        main { normal.openPrivateBrowsing() }
        try {
            var activity = privateActivity()
            lateinit var original: AppContainer
            main { original = app.privateWorkspaces.acquire(activity.taskId) }
            runBlocking { original.workspace.ready.await(); original.tabs.create("personal", server.origin + "/privacy?private-retain") }
            val profile = original.privateProfileName
            val before = runBlocking { original.workspace.snapshot.first().tabs.map { it.id }.toSet() }

            // Home/background does not end the session.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("input keyevent KEYCODE_HOME")).use { it.readBytes() }
            compose.waitUntil(10_000) {
                var stopped = false
                main { stopped = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.STOPPED).contains(activity) }
                stopped
            }
            // Wait for a real launcher return instead of racing the Home animation.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("am start -W -n com.takeruf.nagi/.MainActivity")).use { it.readBytes() }
            compose.waitUntil(10_000) {
                var resumed = false
                main { resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).contains(normal) }
                resumed
            }
            main { normal.openPrivateBrowsing() }
            activity = privateActivity()
            main { assertSame(original, app.privateWorkspaces.acquire(activity.taskId)) }

            val previousActivity = activity
            main { activity.recreate() }
            compose.waitUntil(15_000) { previousActivity.isDestroyed }
            activity = privateActivity()
            main { assertSame(original, app.privateWorkspaces.acquire(activity.taskId)) }

            // Finishing only the Activity (e.g. Back or launcher singleTask delivery) must retain tabs.
            main { activity.finish() }
            compose.waitForIdle()
            main { normal.openPrivateBrowsing() }
            activity = privateActivity()
            main { assertSame(original, app.privateWorkspaces.acquire(activity.taskId)) }
            assertEquals(before, runBlocking { original.workspace.snapshot.first().tabs.map { it.id }.toSet() })

            // Check the EditorInfo delivered through Android's real Compose input connection.
            TestIme().use { ime ->
                main {
                    activity.window.callback.dispatchKeyEvent(android.view.KeyEvent(0, 0, android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_L, 0, android.view.KeyEvent.META_CTRL_ON))
                }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("command-input").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("command-input").performClick()
                ime.connection(action = EditorInfo.IME_ACTION_GO)
                assertTrue(InputConnectionHarnessIme.editor!!.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            }
            // Removing the task exercises the same platform callback as swiping its Recents card.
            val oldTaskId = activity.taskId
            main {
                // Android can stop background services before the user removes the task.
                app.stopService(android.content.Intent(app, com.takeruf.nagi.browser.privacy.PrivateTaskService::class.java))
                app.getSystemService(ActivityManager::class.java).appTasks
                .first { it.taskInfo.id == oldTaskId }.finishAndRemoveTask() }
            compose.waitUntil(15_000) { !original.database.isOpen }
            ActivityScenario.launch(MainActivity::class.java).use { freshScenario ->
                freshScenario.onActivity { it.openPrivateBrowsing() }
                activity = privateActivity()
                lateinit var fresh: AppContainer
                main { fresh = app.privateWorkspaces.acquire(activity.taskId) }
                assertNotEquals(profile, fresh.privateProfileName)
                runBlocking { fresh.workspace.ready.await() }
                assertFalse(runBlocking { fresh.workspace.snapshot.first().tabs.any { it.url.contains("private-retain") } })
                compose.onNodeWithTag("close-private-mode").performClick()
                compose.waitUntil(15_000) { !fresh.database.isOpen }
                freshScenario.onActivity { it.openPrivateBrowsing() }
                activity = privateActivity()
                main {
                    assertNotEquals(fresh.privateProfileName, app.privateWorkspaces.acquire(activity.taskId).privateProfileName)
                    activity.closePrivateBrowsing()
                }
            }
        } finally {
            main { app.privateWorkspaces.close() }
            main { if (!normal.isDestroyed) normal.finish() }
            server.close()
        }
    }
}
