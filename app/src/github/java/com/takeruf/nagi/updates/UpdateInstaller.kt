package com.takeruf.nagi.updates

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.takeruf.nagi.R
import kotlinx.coroutines.launch

class UpdateInstaller(private val activity: ComponentActivity, private val updates: AppUpdates) {
    private val permission = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (activity.packageManager.canRequestPackageInstalls()) install()
    }
    fun install() {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            runCatching { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                .onFailure { error() }
            return
        }
        activity.lifecycleScope.launch {
            try {
                val apk = updates.verifiedApk()
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
                activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error() }
        }
    }
    private fun error() { Toast.makeText(activity, R.string.ui_update_install_failed, Toast.LENGTH_LONG).show() }
}
