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
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.*
import com.takeruf.nagi.browser.downloads.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BlockedExternalAppRequest(val url: String, internal val launch: () -> Unit)

class NativeBrowserHost(private val activity: ComponentActivity) : BrowserHost, FullscreenHost {
    var blockExternalApps: () -> Boolean = { false }
    private val blockedExternalApp = MutableStateFlow<BlockedExternalAppRequest?>(null)
    val blockedExternalApps = blockedExternalApp.asStateFlow()

    fun dismissBlockedExternalApp(request: BlockedExternalAppRequest) {
        if (blockedExternalApp.value === request) blockedExternalApp.value = null
    }
    fun allowBlockedExternalApp(request: BlockedExternalAppRequest) {
        if (disposed || blockedExternalApp.value !== request) return
        blockedExternalApp.value = null
        request.launch()
    }
    private val strings = NagiStrings(activity)
    private var fileResult: ((List<String>?) -> Unit)? = null
    private var permissionResult: ((Set<SitePermission>) -> Unit)? = null
    private var requestedPermissions = emptySet<SitePermission>()
    private var consentDialog: AlertDialog? = null
    val video = VideoPresentationController(activity)
    private val downloads = DownloadService(activity.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var fileDialog: AlertDialog? = null
    private var captureFile: File? = null
    private var captureUri: Uri? = null
    private val capturedFiles = mutableListOf<File>()
    private val generatedDialogs = mutableMapOf<AlertDialog, (Boolean) -> Unit>()
    private var disposed = false
    private val cameraLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = captureFile
        val uri = captureUri
        captureFile = null; captureUri = null
        if (success && file != null && file.length() > 0 && uri != null) {
            capturedFiles.add(file)
            finishFiles(listOf(uri.toString()))
        } else {
            file?.delete(); finishFiles(null)
        }
    }
    private val cameraPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && fileResult != null && !disposed) launchCamera() else finishFiles(null)
    }
    private fun finishFiles(uris: List<String>?) {
        val callback = fileResult; fileResult = null
        fileDialog = null; callback?.invoke(uris)
    }
    private fun launchCamera() {
        try {
            val directory = File(activity.cacheDir, "camera-uploads").apply { mkdirs() }
            captureFile = File.createTempFile("photo-", ".jpg", directory)
            captureUri = FileProvider.getUriForFile(activity, "${activity.packageName}.uploads", captureFile!!)
            cameraLauncher.launch(captureUri!!)
        } catch (_: Exception) {
            captureFile?.delete(); captureFile = null; captureUri = null
            finishFiles(null); showMessage(strings(R.string.ui_camera_unavailable))
        }
    }
    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
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
        // Picker replies must not expose private app files through our own providers.
        finishFiles(uris?.filter { value ->
            val uri = Uri.parse(value)
            uri.scheme == "content" && uri.authority?.startsWith(activity.packageName) != true
        }?.takeIf { it.isNotEmpty() })
    }
    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val granted = requestedPermissions.filter { kind -> permissionNames(kind).any { name ->
            ContextCompat.checkSelfPermission(activity, name) == PackageManager.PERMISSION_GRANTED
        } }.toSet()
        val callback = permissionResult; permissionResult = null
        requestedPermissions = emptySet(); callback?.invoke(granted)
    }
    override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) {
        if (fileResult != null || disposed) { result(null); return }
        fileResult = result
        if (FileSelectionPolicy.offersPhoto(request)) {
            if (request.capture) takePhoto()
            else fileDialog = AlertDialog.Builder(activity).setTitle(strings(R.string.ui_upload_file))
                .setItems(arrayOf(strings(R.string.ui_take_photo), strings(R.string.ui_choose_files))) { _, index ->
                    fileDialog = null
                    if (index == 0) takePhoto() else openDocuments(request)
                }.setNegativeButton(strings(R.string.ui_cancel)) { _, _ -> finishFiles(null) }
                .setOnCancelListener { finishFiles(null) }.show()
        } else openDocuments(request)
    }
    private fun openDocuments(request: FileSelectionRequest) {
        val types = FileSelectionPolicy.types(request)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (types.size == 1) types.first() else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, request.multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { fileLauncher.launch(intent) }.onFailure {
            finishFiles(null); showMessage(strings(R.string.ui_no_file_picker_is_available))
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
    override fun confirmGeneratedDownload(request: GeneratedDownload, result: (Boolean) -> Unit) {
        if (disposed) { result(false); return }
        lateinit var dialog: AlertDialog
        fun finish(accepted: Boolean) { generatedDialogs.remove(dialog)?.invoke(accepted) }
        dialog = AlertDialog.Builder(activity).setTitle(strings(R.string.ui_download_file))
            .setMessage("${request.name}\n${request.size / 1024} KB\n\n${request.origin}")
            .setPositiveButton(strings(R.string.ui_download)) { _, _ -> finish(true) }
            .setNegativeButton(strings(R.string.ui_cancel)) { _, _ -> finish(false) }
            .setOnCancelListener { finish(false) }.create()
        generatedDialogs[dialog] = result
        dialog.show()
    }
    override fun saveGeneratedDownload(request: GeneratedDownload, file: File) {
        if (disposed) { file.delete(); return }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { downloads.saveGenerated(request, file) }
                showMessage(strings(R.string.ui_download_saved))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                showMessage(strings(R.string.ui_download_failed_1_s, error.message.orEmpty()))
            } finally { file.delete() }
        }
    }
    override fun openExternal(url: String) {
        openExternalLink(url) { showMessage(strings(R.string.ui_no_app_can_open_this_link)) }
    }
    override fun openExternalLink(url: String, fallback: (String) -> Unit): Boolean {
        val link = ExternalAppLinks.parse(url) ?: return false
        val intent = link.intent
        if (intent.`package` == activity.packageName) {
            if (link.isWebLink) return false
            link.webFallback?.let(fallback) ?: showMessage(strings(R.string.ui_no_app_can_open_this_link))
            return true
        }
        if (link.isWebLink && intent.`package` == null) {
            // Do not hand ordinary pages to another browser (or back to Nagi).
            val targets = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
                .filter { (it.filter?.countDataAuthorities() ?: 0) > 0 && it.activityInfo.exported && it.activityInfo.packageName != activity.packageName }
                .distinctBy { it.activityInfo.packageName }
            if (targets.isEmpty()) return false
            intent.setPackage(targets.first().activityInfo.packageName)
        }
        fun webFallbackOrMessage() {
            val target = link.webFallback ?: if (link.isWebLink) intent.dataString else null
            target?.let(fallback) ?: showMessage(strings(R.string.ui_no_app_can_open_this_link))
        }
        fun stayInBrowser() {
            val target = if (link.isWebLink) intent.dataString else link.webFallback
            target?.let(fallback)
        }
        fun launch() {
            if (!disposed) runCatching { activity.startActivity(intent) }.onFailure { webFallbackOrMessage() }
        }
        if (blockExternalApps()) {
            blockedExternalApp.value = BlockedExternalAppRequest(intent.dataString.orEmpty(), ::launch)
            stayInBrowser()
        } else {
            blockedExternalApp.value = null
            launch()
        }
        return true
    }
    override fun showMessage(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    override fun showFullscreen(view: View, exit: () -> Unit) {
        video.show(view, null, exit = exit)
    }
    override fun hideFullscreen() {
        video.close()
    }
    override fun showVideo(view: View, tabId: String, playback: VideoPlayback, popup: Boolean,
        control: (String) -> Unit, exit: () -> Unit) = video.show(view, tabId, playback, popup, control, exit)
    override fun updateVideo(tabId: String, playback: VideoPlayback) = video.update(tabId, playback)
    override fun minimizeVideo(tabId: String) = video.minimize(tabId)
    override fun hideVideo(tabId: String) = video.close(tabId)
    fun dispose() {
        disposed = true
        blockedExternalApp.value = null
        consentDialog?.dismiss(); finishPermission(emptySet())
        fileDialog?.dismiss(); fileDialog = null; finishFiles(null)
        captureFile?.delete(); captureFile = null; captureUri = null
        capturedFiles.forEach { it.delete() }; capturedFiles.clear()
        generatedDialogs.toMap().forEach { (dialog, callback) -> dialog.dismiss(); callback(false) }
        generatedDialogs.clear()
        scope.cancel(); video.dispose()
    }
}
