package com.takeruf.nagi.updates

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.takeruf.nagi.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppRelease(val versionCode: Long, val versionName: String, val apkUrl: String,
    val sha256: String, val size: Long, val notes: String, val minSdk: Int)

object UpdateManifest {
    const val REPOSITORY = "https://github.com/TakeruF/android-tab-browser"
    const val URL = "$REPOSITORY/releases/latest/download/update.json"
    fun parse(json: String): AppRelease {
        val value = JSONObject(json)
        require(value.getString("applicationId") == BuildConfig.APPLICATION_ID) { "Wrong update package" }
        val name = value.getString("versionName")
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(name)) { "Invalid update version" }
        val code = value.getLong("versionCode")
        val url = value.getString("apkUrl")
        require(url == "$REPOSITORY/releases/download/v$name/nagi-$name.apk") { "Untrusted update URL" }
        val digest = value.getString("sha256").lowercase()
        require(Regex("[0-9a-f]{64}").matches(digest)) { "Invalid update checksum" }
        val size = value.getLong("size")
        require(code > 0 && size in 1..150_000_000) { "Invalid update size or version" }
        val minSdk = value.getInt("minSdk")
        require(minSdk >= 26) { "Invalid minimum Android version" }
        return AppRelease(code, name, url, digest, size, value.optString("notes").take(10_000), minSdk)
    }
}

enum class UpdateStatus { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR, UNSUPPORTED }
data class UpdateState(val status: UpdateStatus = UpdateStatus.IDLE, val release: AppRelease? = null,
    val progress: Int = 0, val apk: File? = null)

/** Downloads are private to the app; only a verified upgrade can reach the installer. */
class AppUpdates(private val context: Context, private val scope: CoroutineScope,
    private val connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) {
    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun check() {
        if (job?.isCompleted == false) return
        job = scope.launch {
            mutableState.value = UpdateState(UpdateStatus.CHECKING)
            try {
                val text = connection(UpdateManifest.URL).let { connection ->
                    try { connection.inputStream.use { input ->
                        val bytes = input.readNBytesCompat(64 * 1024 + 1)
                        require(bytes.size <= 64 * 1024) { "Update manifest is too large" }
                        bytes.toString(Charsets.UTF_8)
                    } } finally { connection.disconnect() }
                }
                val release = UpdateManifest.parse(text)
                val status = when {
                    release.versionCode <= BuildConfig.VERSION_CODE -> UpdateStatus.CURRENT
                    release.minSdk > Build.VERSION.SDK_INT -> UpdateStatus.UNSUPPORTED
                    else -> UpdateStatus.AVAILABLE
                }
                mutableState.value = UpdateState(status, release.takeIf { status != UpdateStatus.CURRENT })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = UpdateState(UpdateStatus.ERROR) }
        }
    }

    fun download() {
        if (job?.isCompleted == false) return
        val release = state.value.release ?: return
        if (release.versionCode <= BuildConfig.VERSION_CODE || release.minSdk > Build.VERSION.SDK_INT) return
        job = scope.launch {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val part = File(directory, "nagi-${release.versionCode}.part")
            val apk = File(directory, "nagi-${release.versionCode}.apk")
            mutableState.value = UpdateState(UpdateStatus.DOWNLOADING, release)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var length = 0L
                val connection = connection(release.apkUrl)
                try { connection.inputStream.use { input -> part.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        length += count
                        require(length <= release.size) { "Update exceeds expected size" }
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                        val progress = (length * 100 / release.size).toInt()
                        if (progress != state.value.progress) mutableState.value = UpdateState(UpdateStatus.DOWNLOADING, release, progress)
                    }
                } } } finally { connection.disconnect() }
                require(length == release.size && digest.digest().hex() == release.sha256) { "Update verification failed" }
                verifyArchive(part, release)
                apk.delete()
                check(part.renameTo(apk)) { "Could not save update" }
                mutableState.value = UpdateState(UpdateStatus.READY, release, 100, apk)
            } catch (e: CancellationException) {
                mutableState.value = UpdateState(UpdateStatus.AVAILABLE, release); throw e
            } catch (_: Exception) { mutableState.value = UpdateState(UpdateStatus.ERROR, release) }
            finally { part.delete() }
        }
    }

    fun cancel() { job?.cancel() }

    suspend fun verifiedApk(): File = withContext(Dispatchers.IO) {
        val current = state.value
        val release = requireNotNull(current.release)
        val apk = requireNotNull(current.apk)
        require(current.status == UpdateStatus.READY && apk.length() == release.size)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(32 * 1024)
            while (true) { currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
        }
        require(digest.digest().hex() == release.sha256)
        verifyArchive(apk, release)
        apk
    }

    @Suppress("DEPRECATION")
    internal fun verifyArchive(file: File, release: AppRelease) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val archive = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) { "Invalid APK" }
        val installed = manager.getPackageInfo(context.packageName, flags)
        fun code(info: android.content.pm.PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun signers(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        }
        val expected = signers(installed)
        require(archive.packageName == context.packageName && code(archive) == release.versionCode && code(archive) > code(installed)) { "APK is not an upgrade" }
        require(archive.versionName == release.versionName && archive.applicationInfo?.minSdkVersion == release.minSdk) { "APK metadata mismatch" }
        require(expected.isNotEmpty() && signers(archive) == expected) { "APK signature mismatch" }
    }

    private fun connection(url: String): HttpURLConnection {
        require(url.startsWith("https://"))
        return connectionFactory(url).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Nagi/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Cache-Control", "no-cache")
            try { require(responseCode == 200 && this.url.protocol == "https") { "Could not fetch update" } }
            catch (e: Exception) { disconnect(); throw e }
        }
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
