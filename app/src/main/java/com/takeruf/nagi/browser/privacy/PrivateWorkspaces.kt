package com.takeruf.nagi.browser.privacy

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.takeruf.nagi.AppContainer
import com.takeruf.nagi.NagiApplication

/** Task-owned memory, independent of PrivateActivity's ViewModel/Back lifetime. Main thread only. */
class PrivateWorkspaces(private val application: NagiApplication) {
    private var workspace: AppContainer? = null
    private var ownerTaskId: Int? = null
    private val activities = java.util.WeakHashMap<com.takeruf.nagi.PrivateActivity, Unit>()

    private val retiring = mutableMapOf<AppContainer, MutableSet<com.takeruf.nagi.PrivateActivity>>()

    fun attach(activity: com.takeruf.nagi.PrivateActivity) { activities[activity] = Unit }
    fun detach(activity: com.takeruf.nagi.PrivateActivity) {
        activities.remove(activity)
        retiring.toMap().forEach { (container, owners) ->
            owners.remove(activity)
            if (owners.isEmpty()) {
                retiring.remove(container)
                dispose(container)
            }
        }
    }

    private fun dispose(container: AppContainer) {
        container.closePrivateWorkspace()
        PrivateProfiles.clear(container.privateProfileName!!)
    }

    fun acquire(taskId: Int): AppContainer {
        // Also handle a missed service callback (for example background-service restrictions).
        if (ownerTaskId != null && (ownerTaskId != taskId || application
                .getSystemService(ActivityManager::class.java).appTasks.none { it.taskInfo.id == ownerTaskId })) close()
        workspace?.let {
            application.startService(Intent(application, PrivateTaskService::class.java))
            return it
        }
        val normal = application.container
        return AppContainer(application, PrivateProfiles.create(), normal.settings, normal.adBlocker, normal.workspace).also {
            workspace = it
            ownerTaskId = taskId
            application.startService(Intent(application, PrivateTaskService::class.java))
        }
    }

    fun close() {
        val old = workspace ?: return
        workspace = null
        ownerTaskId = null
        val owners = activities.keys.toMutableSet()
        if (owners.isNotEmpty()) retiring[old] = owners
        owners.toList().forEach {
            it.disposeSessions?.invoke()
            it.disposeSessions = null
            it.finish()
        }
        activities.clear()
        application.stopService(Intent(application, PrivateTaskService::class.java))
        // Active observers must be gone before Room closes; WebViews are disposed above.
        if (owners.isEmpty()) dispose(old)
    }
}

/** No foreground notification or process keepalive; observes removal from Recents. */
class PrivateTaskService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as NagiApplication).privateWorkspaces.close()
        stopSelf()
    }
}
