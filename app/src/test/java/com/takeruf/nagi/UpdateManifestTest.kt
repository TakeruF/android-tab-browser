package com.takeruf.nagi

import android.app.Application
import com.takeruf.nagi.updates.UpdateManifest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateManifestTest {
    private fun manifest() = JSONObject().apply {
        put("applicationId", "com.takeruf.nagi"); put("versionName", "0.1.2"); put("versionCode", 3)
        put("apkUrl", "${UpdateManifest.REPOSITORY}/releases/download/v0.1.2/nagi-0.1.2.apk")
        put("sha256", "a".repeat(64)); put("size", 1000); put("notes", "Fixes"); put("minSdk", 26)
    }
    @Test fun validReleasePreservesVersionDigestAndNotes() {
        val release = UpdateManifest.parse(manifest().toString())
        assertEquals(3L, release.versionCode); assertEquals("a".repeat(64), release.sha256)
        assertEquals("Fixes", release.notes); assertEquals(26, release.minSdk)
    }
    @Test fun rejectsForeignPackagesAndUntrustedOrMismatchedAssetUrls() {
        for ((field, value) in listOf("applicationId" to "another.app", "apkUrl" to "http://github.com/update.apk",
            "apkUrl" to "https://example.com/update.apk", "apkUrl" to "${UpdateManifest.REPOSITORY}/releases/download/v0.1.1/nagi-0.1.1.apk")) {
            assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(manifest().put(field, value).toString()) }
        }
    }
    @Test fun rejectsMalformedChecksumsVersionsAndUnboundedFiles() {
        for ((field, value) in listOf("sha256" to "broken", "versionName" to "../test", "versionCode" to 0, "size" to 150_000_001, "size" to 0, "minSdk" to 1)) {
            assertThrows(IllegalArgumentException::class.java) { UpdateManifest.parse(manifest().put(field, value).toString()) }
        }
    }
}
