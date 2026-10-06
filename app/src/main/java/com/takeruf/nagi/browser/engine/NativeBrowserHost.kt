package com.takeruf.nagi.browser.engine

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.NagiStrings

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.takeruf.nagi.browser.downloads.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NativeBrowserHost(private val activity: ComponentActivity) : BrowserHost, FullscreenHost {
    private val strings = NagiStrings(activity)
    private var fileResult: ((List<String>?) -> Unit)? = null
    private var permissionResult: ((Set<SitePermission>) -> Unit)? = null
    private var requestedPermissions = emptySet<SitePermission>()
    private var consentDialog: AlertDialog? = null
    private val fullscreenView = MutableStateFlow<View?>(null)
    val fullscreen = fullscreenView.asStateFlow()
    private var fullscreenExit: (() -> Unit)? = null
    private val downloads = DownloadService(activity.applicationContext)
    override fun showContextMenu(title: String, actions: List<PageContextAction>) {
        AlertDialog.Builder(activity).setTitle(title).setItems(actions.map { it.label }.toTypedArray()) { _, index -> actions[index].execute() }
            .setNegativeButton(strings(R.string.ui_cancel), null).show()
    }
    override fun copyLink(url: String) {
        activity.getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("URL", url))
        if (android.os.Build.VERSION.SDK_INT < 33) showMessage(strings(R.string.ui_link_copied))
    }
    override fun shareLink(url: String) {
        activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }, strings(R.string.ui_share_link)))
    }
    private val fileLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri.toString() } }
                ?: result.data?.data?.let { listOf(it.toString()) }
        } else null
        val callback = fileResult; fileResult = null; callback?.invoke(uris)
    }
    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = requestedPermissions.filter { kind -> permissionNames(kind).any { name ->
            ContextCompat.checkSelfPermission(activity, name) == PackageManager.PERMISSION_GRANTED
        } }.toSet()
        val callback = permissionResult; permissionResult = null
        requestedPermissions = emptySet(); callback?.invoke(granted)
    }
    override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) {
        fileResult?.invoke(null); fileResult = result
        val types = request.mimeTypes.flatMap { it.split(',') }.mapNotNull { value ->
            val type = value.trim().lowercase()
            when {
                type.startsWith('.') -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.drop(1))
                type.contains('/') -> type
                else -> null
            }
        }.distinct()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (types.size == 1) types.first() else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { fileLauncher.launch(intent) }.onFailure {
            fileResult = null; result(null); showMessage(strings(R.string.ui_no_file_picker_is_available))
        }
    }
    override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) {
        // A second site request cannot replace a permission dialog already on screen.
        if (permissionResult != null) { result(emptySet()); return }
        permissionResult = result; requestedPermissions = permissions
        val labels = permissions.joinToString(", ") { strings(when (it) { SitePermission.CAMERA -> R.string.ui_camera; SitePermission.MICROPHONE -> R.string.ui_microphone; SitePermission.LOCATION -> R.string.ui_location }) }
        consentDialog = AlertDialog.Builder(activity)
            .setTitle(strings(R.string.ui_allow_site_access))
            .setMessage(strings(R.string.ui_1_s_wants_to_use_your_2_s_access_lasts_for_this_request, origin, labels))
            .setPositiveButton(strings(R.string.ui_allow)) { _, _ ->
                consentDialog = null
                val needed = permissions.flatMap(::permissionNames).filter {
                    ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
                }.distinct().toTypedArray()
                if (needed.isEmpty()) finishPermission(permissions) else permissionLauncher.launch(needed)
            }
            .setNegativeButton(strings(R.string.ui_block)) { _, _ -> finishPermission(emptySet()) }
            .setOnCancelListener { finishPermission(emptySet()) }.show()
    }
    private fun permissionNames(kind: SitePermission): List<String> = when (kind) {
        SitePermission.CAMERA -> listOf(Manifest.permission.CAMERA)
        SitePermission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        SitePermission.LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private fun finishPermission(granted: Set<SitePermission>) {
        val callback = permissionResult; permissionResult = null; requestedPermissions = emptySet()
        consentDialog = null; callback?.invoke(granted)
    }
    override fun download(request: DownloadRequest) {
        AlertDialog.Builder(activity).setTitle(strings(R.string.ui_download_file))
            .setMessage("${request.suggestedName}\n\n${Uri.parse(request.url).host}")
            .setPositiveButton(strings(R.string.ui_download)) { _, _ ->
                runCatching { downloads.enqueue(request) }.onSuccess { showMessage(strings(R.string.ui_download_started)) }
                    .onFailure { showMessage(strings(R.string.ui_download_failed_1_s, it.message.orEmpty())) }
            }.setNegativeButton(strings(R.string.ui_cancel), null).show()
    }
    override fun openExternal(url: String) {
        AlertDialog.Builder(activity).setTitle(strings(R.string.ui_open_another_app)).setMessage(url)
            .setPositiveButton(strings(R.string.ui_open)) { _, _ ->
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { showMessage(strings(R.string.ui_no_app_can_open_this_link)) }
            }.setNegativeButton(strings(R.string.ui_cancel), null).show()
    }
    override fun showMessage(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    override fun showFullscreen(view: View, exit: () -> Unit) {
        hideFullscreen(); fullscreenExit = exit; fullscreenView.value = view
    }
    override fun hideFullscreen() {
        fullscreenView.value = null
        val exit = fullscreenExit; fullscreenExit = null; exit?.invoke()
    }
    fun dispose() {
        consentDialog?.dismiss(); finishPermission(emptySet())
        fileResult?.invoke(null); fileResult = null; hideFullscreen()
    }
}
