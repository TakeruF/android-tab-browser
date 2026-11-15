package com.takeruf.nagi

import android.app.DownloadManager
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real WebView -> confirmation -> system download -> Room selection/lifetime. */
class DownloadUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var container: AppContainer
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var parentId: String
    private lateinit var web: WebView
    private val manager get() = instrumentation.targetContext.getSystemService(DownloadManager::class.java)
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun waitFor(predicate: () -> Boolean) = compose.waitUntil(25_000, predicate)
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun js(script: String): String {
        val answer = CompletableDeferred<String>()
        main { web.evaluateJavascript(script) { answer.complete(it) } }
        return runBlocking { withTimeout(5_000) { answer.await() } }
    }
    private fun tapLink() {
        compose.waitForIdle()
        js("document.querySelector('a').scrollIntoView({block:'center',inline:'center'});true")
        val ready = CompletableDeferred<Unit>()
        main {
            web.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { ready.complete(Unit) }
            })
            web.invalidate()
        }
        waitFor { ready.isCompleted }
        val rect = JSONObject(JSONTokener(js("""(()=>{const r=document.querySelector('a').getBoundingClientRect(),v=visualViewport;return JSON.stringify({x:r.x+r.width/2-(v?.offsetLeft||0),y:r.y+r.height/2-(v?.offsetTop||0),w:v?.width||innerWidth})})()""")).nextValue() as String)
        main {
            val scale = web.width / rect.getDouble("w")
            val time = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, (rect.getDouble("x") * scale).toFloat(), (rect.getDouble("y") * scale).toFloat(), 0)
            val up = MotionEvent.obtain(time, time + 80, MotionEvent.ACTION_UP, down.x, down.y, 0)
            web.dispatchTouchEvent(down); web.dispatchTouchEvent(up); down.recycle(); up.recycle()
        }
    }
    private fun tab(id: String) = runBlocking { container.workspace.dao.tab(id)!! }
    private fun active() = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
    private fun confirmationVisible() = compose.onAllNodesWithTag("download-confirm").fetchSemanticsNodes().isNotEmpty()
    private fun confirm(accept: Boolean) {
        waitFor { confirmationVisible() }
        assertNoDuplicateDismissControl()
        compose.onNodeWithTag(if (accept) "download-confirm" else "download-cancel").performClick()
    }
    private fun assertNoDuplicateDismissControl() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_cancel)).assertDoesNotExist()
        compose.onNodeWithTag("download-cancel").assertIsDisplayed()
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun captureConfirmation(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(400) // Compose idleness does not include the dialog window's enter animation.
        capture(name)
    }
    private fun captureNotification(name: String) {
        val automation = instrumentation.uiAutomation
        val info = automation.serviceInfo
        info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
        shell("cmd statusbar expand-notifications")
        SystemClock.sleep(800)
        automation.windows.mapNotNull { it.root }.forEach { root ->
            root.findAccessibilityNodeInfosByViewId("android:id/expand_button").forEach { expand ->
                if (expand.contentDescription?.toString()?.contains("Expand", ignoreCase = true) == true) {
                    expand.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }
        SystemClock.sleep(800)
        capture(name)
        shell("cmd statusbar collapse")
    }
    private fun downloadRows(): List<Pair<Long, Int>> = manager.query(DownloadManager.Query()).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)).startsWith(server.origin)) {
                    add(cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)) to
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)))
                }
            }
        }
    }

    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/export-source" to """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Download source</title>
            <body style="font:20px sans-serif;padding:20px"><h1>Download source</h1>
            <a target="_blank" href="/attachment-redirect" style="display:block;padding:20px">Download in new tab</a>
            <textarea id="draft">Keep my draft</textarea><script>window.sourceToken='live-opener';</script></body>
        """.trimIndent()), redirects = mapOf("/attachment-redirect" to "/download"))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            val dark = InstrumentationRegistry.getArguments().getString("downloadTheme") == "dark"
            container.settings.update { BrowserSettings(automaticSearchRegion = false, theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
            container.spaces.create("Download QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            parentId = container.tabs.create(spaceId, "${server.origin}/export-source")
        }
        waitFor {
            main { views(compose.activity.window.decorView).filterIsInstance<WebView>().firstOrNull { it.url == "${server.origin}/export-source" }?.let { web = it } }
            ::web.isInitialized && js("document.readyState") == "\"complete\""
        }
        js("document.getElementById('draft').value='Unsent 日本語 draft'; true")
    }
    @After fun cleanup() {
        shell("cmd statusbar collapse")
        val notifications = instrumentation.targetContext.getSystemService(android.app.NotificationManager::class.java)
        notifications.activeNotifications.filter {
            it.notification.extras.getString(android.app.Notification.EXTRA_TITLE)?.startsWith("nagi-generated-qa") == true
        }.forEach { notifications.cancel(it.tag, it.id) }
        if (::server.isInitialized) {
            downloadRows().forEach { manager.remove(it.first) }
            server.close()
        }
        if (::spaceId.isInitialized) runBlocking { container.spaces.delete(spaceId) }
    }

    @Test fun newTabDownloadReturnsToSameLiveOpenerAndShowsSystemProgress() {
        js("document.querySelector('a').href='/download-slow';true")
        tapLink()
        waitFor { active() != parentId }
        val child = active()
        confirm(true)
        waitFor { tab(child).closedAt != null && active() == parentId }
        assertEquals("\"live-opener\"", js("window.sourceToken"))
        assertEquals("\"Unsent 日本語 draft\"", js("document.getElementById('draft').value"))
        // Explicit loopback-only diagnostic mode for QA networks that Android has
        // not validated. Default runs retain all ordinary DownloadManager constraints.
        val forceLocalJob = InstrumentationRegistry.getArguments().getString("forceLocalDownloadJob") == "true"
        var forcedId: Long? = null
        try { waitFor {
            manager.query(DownloadManager.Query()).use { c ->
                var progressing = false
                while (c.moveToNext()) {
                    val uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                    val bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (forceLocalJob && forcedId == null && uri == "${server.origin}/download-slow" &&
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_PENDING) {
                        val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                        val before = shell("cmd jobscheduler get-job-state -u 0 com.android.providers.downloads $id").trim()
                        val result = shell("cmd jobscheduler run -f -u 0 com.android.providers.downloads $id").trim()
                        File(instrumentation.targetContext.getExternalFilesDir(null), "download-scheduler-diagnostic.txt")
                            .writeText("Fixture job $id\nBefore: $before\nExplicit forced run: $result\n")
                        forcedId = id
                    }
                    if (uri == "${server.origin}/download-slow" && bytes in 1 until 2 * 1024 * 1024L) progressing = true
                }
                progressing
            }
        } } catch (failure: androidx.compose.ui.test.ComposeTimeoutException) {
            val details = manager.query(DownloadManager.Query()).use { c -> buildList {
                while (c.moveToNext()) {
                    if (c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)).startsWith(server.origin)) {
                        add(listOf(DownloadManager.COLUMN_ID, DownloadManager.COLUMN_STATUS, DownloadManager.COLUMN_REASON,
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            .associateWith { c.getLong(c.getColumnIndexOrThrow(it)) })
                    }
                }
            } }
            val jobs = details.associate { row ->
                val id = row.getValue(DownloadManager.COLUMN_ID)
                id to shell("cmd jobscheduler get-job-state -u 0 com.android.providers.downloads $id").trim()
            }
            val metadata = details.associate { row ->
                val id = row.getValue(DownloadManager.COLUMN_ID)
                id to runCatching {
                    val columns = arrayOf("uid", "notificationpackage", "control", "status", "deleted", "flags", "destination", "is_public_api", "allowed_network_types")
                    instrumentation.targetContext.contentResolver.query(android.net.Uri.parse("content://downloads/my_downloads/$id"),
                        columns, null, null, null)!!.use { c ->
                        if (c.moveToFirst()) columns.associateWith { c.getString(c.getColumnIndexOrThrow(it)) } else emptyMap()
                    }
                }.getOrElse { mapOf("queryError" to it.javaClass.simpleName) }
            }
            throw AssertionError("Fixture download did not expose in-flight progress: $details; jobs=$jobs; metadata=$metadata", failure)
        }
        capture("download-returned-to-opener")
        captureNotification("download-system-progress")
        waitFor { downloadRows().any { it.second == DownloadManager.STATUS_SUCCESSFUL } }
        val id = downloadRows().single().first
        manager.openDownloadedFile(id).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val bytes = input.readBytes()
                assertEquals(2 * 1024 * 1024, bytes.size)
                assertTrue(bytes.indices.all { bytes[it] == (it % 251).toByte() })
            }
        }
    }

    @Test fun cancelledDownloadKeepsNewTabAndDoesNotStartTransfer() {
        tapLink()
        waitFor { active() != parentId }
        val child = active()
        waitFor { confirmationVisible() }
        compose.onNodeWithText("nagi.txt").assertIsDisplayed()
        compose.onNodeWithText(android.net.Uri.parse(server.origin).host!!).assertIsDisplayed()
        captureConfirmation("download-confirmation-http")
        confirm(false)
        assertNull(tab(child).closedAt)
        assertEquals(child, active())
        assertTrue(downloadRows().isEmpty())
    }

    @Test fun manuallyCreatedTabIsKeptAfterDownload() {
        val manual = runBlocking { container.tabs.create(spaceId, "${server.origin}/download") }
        confirm(true)
        waitFor { downloadRows().any { it.second == DownloadManager.STATUS_SUCCESSFUL } }
        assertNull(tab(manual).closedAt)
        assertEquals(manual, active())
    }

    @Test fun acceptingBackgroundDownloadDoesNotReplaceNewSelection() {
        tapLink()
        waitFor { active() != parentId }
        val child = active()
        waitFor { confirmationVisible() }
        val selected = runBlocking { container.tabs.create(spaceId) }
        confirm(true)
        waitFor { tab(child).closedAt != null }
        assertEquals(selected, active())
        assertNull(tab(parentId).closedAt)
    }

    @Test fun popupThatHasDisplayedAPageIsKeptAfterDownload() {
        js("document.querySelector('a').href='/two';true")
        tapLink()
        waitFor { active() != parentId }
        val child = active()
        waitFor { tab(child).title == "Fixture Two" }
        main { web = views(compose.activity.window.decorView).filterIsInstance<WebView>().first { it.url == "${server.origin}/two" } }
        waitFor { js("document.readyState") == "\"complete\"" }
        main { web.loadUrl("${server.origin}/download") }
        confirm(true)
        waitFor { downloadRows().any { it.second == DownloadManager.STATUS_SUCCESSFUL } }
        assertNull(tab(child).closedAt)
        assertEquals(child, active())
    }

    @Test fun generatedDownloadShowsProgressThenCompletionAndKeepsSource() {
        val filename = "nagi-generated-qa-${System.nanoTime()}.txt"
        shell("pm grant ${instrumentation.targetContext.packageName} android.permission.POST_NOTIFICATIONS")
        // Pace this fixture so the system shade can render an in-flight transfer.
        js("""(()=>{const read=window.__nagiPageDownloads.read;window.__nagiPageDownloads.read=async(...args)=>{await new Promise(r=>setTimeout(r,30));return read(...args)};return true})()""")
        js("""(()=>{let a=document.querySelector('a');a.target='';a.download='$filename';a.href=URL.createObjectURL(new Blob(['x'.repeat(8*1024*1024)],{type:'text/plain'}));return true})()""")
        tapLink()
        confirm(true)
        val notifications = instrumentation.targetContext.getSystemService(android.app.NotificationManager::class.java)
        waitFor { notifications.activeNotifications.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == filename && it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0 } }
        captureNotification("download-generated-progress")
        waitFor { notifications.activeNotifications.any { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == filename && it.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT == 0 } }
        val completed = notifications.activeNotifications.single { it.notification.extras.getString(android.app.Notification.EXTRA_TITLE) == filename }
        assertEquals(compose.activity.getString(R.string.ui_download_saved), completed.notification.extras.getString(android.app.Notification.EXTRA_TEXT))
        assertEquals(parentId, active())
        assertEquals("\"Unsent 日本語 draft\"", js("document.getElementById('draft').value"))
        notifications.cancel(completed.tag, completed.id)
        // Remove only this fixture's generated output, never unrelated Downloads.
        val resolver = instrumentation.targetContext.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.query(collection, arrayOf(android.provider.MediaStore.MediaColumns._ID),
            "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(filename), null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            val uri = android.content.ContentUris.withAppendedId(collection, cursor.getLong(0))
            resolver.openInputStream(uri)!!.use { assertEquals(8 * 1024 * 1024, it.readBytes().size) }
            resolver.delete(uri, null, null)
        }
    }

    @Test fun generatedConfirmationShowsUnicodeFilenameAndCancelsWithoutTransfer() {
        val filename = "ゲーム理論入門第4回講義資料(2026秋).csv"
        js("""(()=>{let a=document.querySelector('a');a.target='';a.download='$filename';a.href=URL.createObjectURL(new Blob(['x'.repeat(120000)],{type:'text/csv'}));return true})()""")
        tapLink()
        waitFor { confirmationVisible() }
        compose.onNodeWithText(filename).assertIsDisplayed()
        compose.onNodeWithText("117 KB").assertIsDisplayed()
        compose.onNodeWithText(server.origin).assertIsDisplayed()
        assertNoDuplicateDismissControl()
        captureConfirmation("download-confirmation-generated")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        waitFor { !confirmationVisible() }
        assertEquals(parentId, active())
        assertEquals("\"Unsent 日本語 draft\"", js("document.getElementById('draft').value"))
        assertTrue(downloadRows().isEmpty())
    }

    @Test fun downloadFromSplitRestoresSavedPairAndOpenerDraft() {
        val right = runBlocking { container.tabs.create(spaceId, "${server.origin}/two", select = false) }
        runBlocking { container.tabs.pair(parentId, right) }
        waitFor { tab(right).title == "Fixture Two" }
        tapLink()
        waitFor { active() != parentId }
        val child = active()
        confirm(true)
        waitFor { tab(child).closedAt != null && active() == parentId }
        assertEquals(right, tab(parentId).splitRightTabId)
        assertNull(tab(right).closedAt)
        assertEquals("\"Unsent 日本語 draft\"", js("document.getElementById('draft').value"))
        waitFor {
            var restored = false
            main {
                val visible = views(compose.activity.window.decorView).filterIsInstance<WebView>().filter { it.isShown && it.width > 0 }
                restored = visible.any { it === web } && visible.any { it.url == "${server.origin}/two" }
            }
            restored && !confirmationVisible()
        }
        compose.waitForIdle()
        SystemClock.sleep(300) // Let the native dialog's exit animation finish for the screenshot.
        capture("download-split-restored")
    }
}
