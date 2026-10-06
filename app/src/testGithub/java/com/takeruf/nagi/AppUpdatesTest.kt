package com.takeruf.nagi

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import com.takeruf.nagi.updates.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class AppUpdatesTest {
    private val installedCode = BuildConfig.VERSION_CODE
    private val candidateCode = installedCode + 1
    private val candidateName = "0.1.3"
    private val bytes = "test-apk-bytes".toByteArray()
    private fun digest(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private fun info(code: Int, signer: String = "0123456789abcdef", name: String = "com.takeruf.nagi") = PackageInfo().apply {
        packageName = name; versionCode = code; versionName = if (code == installedCode) BuildConfig.VERSION_NAME else candidateName
        signatures = arrayOf(Signature(signer)); applicationInfo = ApplicationInfo().apply { packageName = name; minSdkVersion = 26 }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            signingInfo = android.content.pm.SigningInfo().also { shadowOf(it).setSignatures(signatures) }
        }
    }
    private fun metadata(hash: String = digest(bytes), code: Int = candidateCode) = JSONObject().apply {
        put("applicationId", BuildConfig.APPLICATION_ID); put("versionCode", code); put("versionName", candidateName)
        put("apkUrl", "${UpdateManifest.REPOSITORY}/releases/download/v$candidateName/nagi-$candidateName.apk")
        put("sha256", hash); put("size", bytes.size); put("minSdk", 26)
    }.toString().toByteArray()
    private fun response(url: String, data: ByteArray) = object : HttpURLConnection(URL(url)) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getInputStream() = data.inputStream()
    }
    private fun prepare(candidate: PackageInfo): Application {
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(context.packageManager)
        manager.installPackage(info(installedCode))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        directory.listFiles()?.forEach { it.delete() }
        manager.setPackageArchiveInfo(File(directory, "nagi-$candidateCode.part").absolutePath, candidate)
        manager.setPackageArchiveInfo(File(directory, "nagi-$candidateCode.apk").absolutePath, candidate)
        return context
    }
    @Test fun checkDownloadAndReverifyAcceptOnlyMatchingSignedUpgrade() = runTest {
        val context = prepare(info(candidateCode))
        val updates = AppUpdates(context, this) { response(it, if (it.endsWith("update.json")) metadata() else bytes) }
        updates.check(); advanceUntilIdle(); assertEquals(UpdateStatus.AVAILABLE, updates.state.value.status)
        updates.download(); advanceUntilIdle(); assertEquals(UpdateStatus.READY, updates.state.value.status)
        assertArrayEquals(bytes, updates.verifiedApk().readBytes())
        updates.state.value.apk!!.appendText("tampered")
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { updates.verifiedApk() } }
    }
    @Test fun checksumFailureDeletesPartialFileAndNeverBecomesInstallable() = runTest {
        val context = prepare(info(candidateCode))
        val updates = AppUpdates(context, this) { response(it, if (it.endsWith("update.json")) metadata("b".repeat(64)) else bytes) }
        updates.check(); advanceUntilIdle(); updates.download(); advanceUntilIdle()
        assertEquals(UpdateStatus.ERROR, updates.state.value.status); assertNull(updates.state.value.apk)
        assertFalse(File(context.cacheDir, "updates/nagi-$candidateCode.part").exists())
    }
    @Test fun mismatchedSignerPackageAndVersionCannotReachInstaller() = runTest {
        for (candidate in listOf(info(candidateCode, "fedcba9876543210"), info(candidateCode, name = "another.app"), info(installedCode))) {
            val context = prepare(candidate)
            val updates = AppUpdates(context, this) { response(it, if (it.endsWith("update.json")) metadata() else bytes) }
            updates.check(); advanceUntilIdle(); updates.download(); advanceUntilIdle()
            assertEquals(UpdateStatus.ERROR, updates.state.value.status); assertNull(updates.state.value.apk)
        }
    }
    @Test fun installedVersionIsCurrentAndDoesNotOfferDowngrade() = runTest {
        val context = prepare(info(candidateCode))
        val updates = AppUpdates(context, this) { response(it, metadata(code = 1)) }
        updates.check(); advanceUntilIdle()
        assertEquals(UpdateStatus.CURRENT, updates.state.value.status); assertNull(updates.state.value.release)
        updates.download(); advanceUntilIdle(); assertNull(updates.state.value.apk)
    }
}
