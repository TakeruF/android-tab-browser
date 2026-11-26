package com.takeruf.nagi.browser.engine

import com.takeruf.nagi.R
import com.takeruf.nagi.ui.localization.NagiStrings

import android.Manifest
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

class DownloadConfirmation(val name: String, val source: String, val size: Long?, internal val accept: () -> Unit)

class SitePermissionPrompt(val origin: String, val permissions: Set<SitePermission>)

class BrowserActionPrompt(val title: String, val actions: List<PageContextAction>, internal val dismissed: () -> Unit)

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
    private val sitePermissionPrompt = MutableStateFlow<SitePermissionPrompt?>(null)
    val sitePermissionPrompts = sitePermissionPrompt.asStateFlow()
    private var permissionLauncherPending = false
    val video = VideoPresentationController(activity)
    /** Chosen download folder (SAF tree URI) from Settings; null keeps the system Downloads folder. */
    @Volatile var downloadFolder: String? = null
    private val downloads = DownloadService(activity.applicationContext) { downloadFolder?.let(Uri::parse) }
    private val downloadNotifications = com.takeruf.nagi.browser.downloads.DownloadNotifications(activity.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var filePrompt: BrowserActionPrompt? = null
    private val browserActionPrompt = MutableStateFlow<BrowserActionPrompt?>(null)
    val browserActionPrompts = browserActionPrompt.asStateFlow()
    private var captureFile: File? = null
    private var captureUri: Uri? = null
    private val capturedFiles = mutableListOf<File>()
    private val pendingDownloadResults = mutableMapOf<DownloadConfirmation, (DownloadResult) -> Unit>()
    private val pendingDownloadConfirmations = MutableStateFlow<List<DownloadConfirmation>>(emptyList())
    val downloadConfirmations = pendingDownloadConfirmations.asStateFlow()
    private var disposed = false
    private val notificationPermissionResults = mutableListOf<() -> Unit>()
    private val notificationPermissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val callbacks = notificationPermissionResults.toList()
        notificationPermissionResults.clear()
        if (!disposed) callbacks.forEach { it() }
    }
    private fun withDownloadNotifications(action: () -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) { action(); return }
        notificationPermissionResults.add(action)
        if (notificationPermissionResults.size == 1) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
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
        if (browserActionPrompt.value === filePrompt) browserActionPrompt.value = null
        filePrompt = null; callback?.invoke(uris)
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
        if (!disposed) showBrowserActions(title, actions)
    }
    private fun showBrowserActions(title: String, actions: List<PageContextAction>, dismissed: () -> Unit = {}): BrowserActionPrompt {
        browserActionPrompt.value?.let(::dismissBrowserActions)
        return BrowserActionPrompt(title, actions, dismissed).also { browserActionPrompt.value = it }
    }
    fun chooseBrowserAction(prompt: BrowserActionPrompt, index: Int) {
        if (disposed || browserActionPrompt.value !== prompt) return
        val action = prompt.actions.getOrNull(index) ?: return
        browserActionPrompt.value = null
        if (filePrompt === prompt) filePrompt = null
        action.execute()
    }
    fun dismissBrowserActions(prompt: BrowserActionPrompt) {
        if (browserActionPrompt.value !== prompt) return
        browserActionPrompt.value = null
        if (filePrompt === prompt) filePrompt = null
        prompt.dismissed()
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
        permissionLauncherPending = false
        val granted = requestedPermissions.filter { kind -> permissionNames(kind).any { name ->
            ContextCompat.checkSelfPermission(activity, name) == PackageManager.PERMISSION_GRANTED
        } }.toSet()
        finishPermission(granted)
    }
    override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) {
        if (fileResult != null || disposed) { result(null); return }
        fileResult = result
        if (FileSelectionPolicy.offersPhoto(request)) {
            if (request.capture) takePhoto()
            else filePrompt = showBrowserActions(strings(R.string.ui_upload_file), listOf(
                PageContextAction(strings(R.string.ui_take_photo)) { takePhoto() },
                PageContextAction(strings(R.string.ui_choose_files)) { openDocuments(request) },
            )) { finishFiles(null) }
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
        // A cancelled Android runtime dialog may still return later. Do not attach
        // that result to a different site's request in the meantime.
        if (disposed || permissionResult != null || permissionLauncherPending || permissions.isEmpty()) { result(emptySet()); return }
        permissionResult = result; requestedPermissions = permissions
        sitePermissionPrompt.value = SitePermissionPrompt(origin, permissions)
    }
    fun allowSitePermission(prompt: SitePermissionPrompt) {
        if (disposed || sitePermissionPrompt.value !== prompt) return
        sitePermissionPrompt.value = null
        val needed = prompt.permissions.flatMap(::permissionNames).filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }.distinct().toTypedArray()
        if (needed.isEmpty()) finishPermission(prompt.permissions)
        else {
            permissionLauncherPending = true
            runCatching { permissionLauncher.launch(needed) }.onFailure {
                permissionLauncherPending = false
                finishPermission(emptySet())
            }
        }
    }
    fun dismissSitePermission(prompt: SitePermissionPrompt) {
        if (sitePermissionPrompt.value === prompt) finishPermission(emptySet())
    }
    override fun cancelPermission(result: (Set<SitePermission>) -> Unit) {
        if (permissionResult === result) finishPermission(emptySet())
    }
    private fun permissionNames(kind: SitePermission): List<String> = when (kind) {
        SitePermission.CAMERA -> listOf(Manifest.permission.CAMERA)
        SitePermission.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        SitePermission.LOCATION -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }
    private fun finishPermission(granted: Set<SitePermission>) {
        val callback = permissionResult; permissionResult = null; requestedPermissions = emptySet()
        sitePermissionPrompt.value = null; callback?.invoke(granted)
    }
    override fun download(request: DownloadRequest) = download(request) {}
    override fun download(request: DownloadRequest, result: (DownloadResult) -> Unit) {
        showDownloadConfirmation(request.suggestedName, Uri.parse(request.url).host.orEmpty(), null, result) { finish ->
            fun enqueue() {
                runCatching { downloads.enqueue(request) }.onSuccess {
                    showMessage(strings(R.string.ui_download_started))
                    finish(DownloadResult.ACCEPTED)
                }.onFailure {
                    showMessage(strings(R.string.ui_download_failed_1_s, it.message.orEmpty()))
                    finish(DownloadResult.FAILED)
                }
            }
            // Ordinary HTTP notifications belong to DownloadManager. A chosen
            // SAF folder instead uses Nagi's own completion/failure notification.
            if (downloads.usesChosenFolder()) withDownloadNotifications(::enqueue) else enqueue()
        }
    }
    override fun confirmGeneratedDownload(request: GeneratedDownload, result: (Boolean) -> Unit) {
        showDownloadConfirmation(request.name, request.origin, request.size,
            { result(it == DownloadResult.ACCEPTED) }) { finish ->
            withDownloadNotifications { finish(DownloadResult.ACCEPTED) }
        }
    }
    private fun showDownloadConfirmation(name: String, source: String, size: Long?, result: (DownloadResult) -> Unit,
        accept: ((DownloadResult) -> Unit) -> Unit) {
        if (disposed) { result(DownloadResult.FAILED); return }
        lateinit var confirmation: DownloadConfirmation
        fun finish(outcome: DownloadResult) { pendingDownloadResults.remove(confirmation)?.invoke(outcome) }
        confirmation = DownloadConfirmation(name, source, size) { accept(::finish) }
        pendingDownloadResults[confirmation] = result
        pendingDownloadConfirmations.value += confirmation
    }
    fun confirmDownload(confirmation: DownloadConfirmation) {
        if (disposed || confirmation !in pendingDownloadConfirmations.value) return
        pendingDownloadConfirmations.value -= confirmation
        confirmation.accept()
    }
    fun dismissDownload(confirmation: DownloadConfirmation) {
        if (confirmation !in pendingDownloadConfirmations.value) return
        pendingDownloadConfirmations.value -= confirmation
        pendingDownloadResults.remove(confirmation)?.invoke(DownloadResult.CANCELLED)
    }
    override fun trackGeneratedDownload(request: GeneratedDownload): DownloadProgress = downloadNotifications.start(request)
    override fun saveGeneratedDownload(request: GeneratedDownload, file: File) = saveGeneratedDownload(request, file) {}
    override fun saveGeneratedDownload(request: GeneratedDownload, file: File, result: (Boolean) -> Unit) {
        if (disposed) { file.delete(); result(false); return }
        scope.launch {
            try {
                val folder = withContext(Dispatchers.IO) {
                    downloads.saveGenerated(request, file)
                    downloads.destinationName()
                }
                showMessage(folder?.let { strings(R.string.ui_download_saved_to_1_s, it) } ?: strings(R.string.ui_download_saved))
                result(true)
            } catch (error: Exception) {
                result(false)
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
    override fun updateInlineVideo(view: View, tabId: String, playback: VideoPlayback,
        prepare: () -> Unit, restore: () -> Unit, control: (String) -> Unit) =
        video.updateInline(view, tabId, playback, prepare, restore, control)
    override fun removeInlineVideo(tabId: String) = video.removeInline(tabId)
    fun dispose() {
        disposed = true
        notificationPermissionResults.clear()
        blockedExternalApp.value = null
        finishPermission(emptySet())
        browserActionPrompt.value?.let(::dismissBrowserActions)
        finishFiles(null)
        captureFile?.delete(); captureFile = null; captureUri = null
        capturedFiles.forEach { it.delete() }; capturedFiles.clear()
        val downloadCallbacks = pendingDownloadResults.values.toList()
        pendingDownloadResults.clear()
        pendingDownloadConfirmations.value = emptyList()
        downloadCallbacks.forEach { it(DownloadResult.FAILED) }
        scope.cancel(); video.dispose()
    }
}
