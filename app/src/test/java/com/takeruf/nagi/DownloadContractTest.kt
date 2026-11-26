package com.takeruf.nagi

import android.app.Application
import android.app.DownloadManager
import android.os.Looper
import androidx.activity.ComponentActivity
import com.takeruf.nagi.browser.downloads.DownloadService
import com.takeruf.nagi.browser.engine.DownloadResult
import com.takeruf.nagi.browser.engine.DownloadRequest
import com.takeruf.nagi.browser.engine.NativeBrowserHost
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35], application = Application::class)
class DownloadContractTest {
    private val request = DownloadRequest("https://example.com/export", "Nagi QA", null, "text/csv", "session=fixture", "export.csv")

    @Test fun httpDownloadKeepsSystemProgressAndAuthentication() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = DownloadService(context).enqueue(request)
        val queued = shadowOf(shadowOf(manager).getRequest(id))
        assertEquals(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED, queued.notificationVisibility)
        assertTrue(queued.description.isNullOrEmpty())
        assertEquals("export.csv", queued.title)
        assertTrue(queued.requestHeaders.any { it.first == "Cookie" && it.second == "session=fixture" })
        assertTrue(queued.requestHeaders.any { it.first == "User-Agent" && it.second == "Nagi QA" })
    }

    @Test @Config(sdk = [35]) fun acceptedCallbackWaitsForConfirmationAndCancellationDoesNotEnqueue() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        val results = mutableListOf<DownloadResult>()
        var disposed = false
        try {
            host.download(request) { results.add(it) }
            assertTrue(results.isEmpty())
            val cancelled = host.downloadConfirmations.value.single()
            assertEquals("export.csv", cancelled.name)
            assertEquals("example.com", cancelled.source)
            host.dismissDownload(cancelled)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(DownloadResult.CANCELLED), results)
            val manager = shadowOf(activity.getSystemService(DownloadManager::class.java))
            assertEquals(0, manager.requestCount)
            host.download(request) { results.add(it) }
            val accepted = host.downloadConfirmations.value.single()
            host.confirmDownload(accepted)
            host.confirmDownload(accepted)
            host.dismissDownload(accepted)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(listOf(DownloadResult.CANCELLED, DownloadResult.ACCEPTED), results)
            assertEquals(1, manager.requestCount)
            assertTrue(host.downloadConfirmations.value.isEmpty())
            host.download(request) { results.add(it) }
            host.dispose()
            disposed = true
            assertEquals(listOf(DownloadResult.CANCELLED, DownloadResult.ACCEPTED, DownloadResult.FAILED), results)
            assertTrue(host.downloadConfirmations.value.isEmpty())
        } finally { if (!disposed) host.dispose(); controller.pause().stop().destroy() }
    }

    @Test @Config(sdk = [35]) fun enqueueFailureIsDistinctFromUserCancellation() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val activity = controller.get()
        val host = NativeBrowserHost(activity)
        controller.start().resume()
        try {
            val results = mutableListOf<DownloadResult>()
            host.download(request.copy(url = "ftp://example.com/export")) { results.add(it) }
            host.confirmDownload(host.downloadConfirmations.value.single())
            assertEquals(listOf(DownloadResult.FAILED), results)
            assertEquals(0, shadowOf(activity.getSystemService(DownloadManager::class.java)).requestCount)
        } finally { host.dispose(); controller.pause().stop().destroy() }
    }
}
