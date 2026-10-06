package com.takeruf.nagi

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.takeruf.nagi.domain.model.BrowserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class TabBackUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var server: FixtureServer
    private lateinit var container: AppContainer
    private lateinit var spaceId: String
    private lateinit var parentId: String

    @Before fun setup() {
        server = FixtureServer()
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            container.settings.update { BrowserSettings(automaticSearchRegion = false, desktopDefault = false) }
            container.spaces.create("Back QA ${System.nanoTime()}")
            spaceId = container.settings.settings.first().selectedSpaceId
            parentId = container.tabs.create(spaceId, "${server.origin}/one")
            // The nearest tab is deliberately different from the opener.
            container.tabs.create(spaceId, "${server.origin}/two", select = false)
        }
        awaitPage(parentId, "/one")
    }

    @After fun cleanup() { server.close() }

    private fun webView(view: View = compose.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) webView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun awaitPage(id: String, path: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("browser-pane-left-$id").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking { container.workspace.dao.tab(id)?.url == "${server.origin}$path" }
        }
        compose.waitUntil(15_000) {
            var ready = false
            compose.runOnIdle { ready = webView()?.let { it.url == "${server.origin}$path" && it.progress == 100 } == true }
            ready
        }
    }

    private fun openLink(element: String): String {
        var rect: JSONObject? = null
        compose.runOnIdle {
            webView()!!.evaluateJavascript("(function(){let r=document.getElementById('$element').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth};})()") {
                rect = JSONObject(it)
            }
        }
        compose.waitUntil(5_000) { rect != null }
        compose.runOnIdle {
            val view = webView()!!
            val point = rect!!
            val scale = view.width / point.getDouble("w")
            val x = (point.getDouble("x") * scale).toFloat()
            val y = (point.getDouble("y") * scale).toFloat()
            val time = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { i, action ->
                val event = MotionEvent.obtain(time, time + i * 60, action, x, y, 0)
                view.dispatchTouchEvent(event)
                event.recycle()
            }
        }
        compose.waitUntil(15_000) {
            runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId != parentId }
        }
        val child = runBlocking { container.workspace.dao.spaces().first { it.id == spaceId }.activeTabId!! }
        awaitPage(child, "/two")
        assertEquals(parentId, runBlocking { container.workspace.dao.tab(child)!!.parentTabId })
        return child
    }

    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun assertReturned(child: String) {
        awaitPage(parentId, "/one")
        assertNotNull(runBlocking { container.workspace.dao.tab(child)!!.closedAt })
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun popupBackClosesChildAndReturnsToOpener() {
        val child = openLink("popup")
        back()
        assertReturned(child)
    }

    @Test fun linkSettingBackClosesChildAfterActivityRecreation() {
        runBlocking { container.settings.update { it.copy(openLinksInNewTab = true) } }
        compose.waitForIdle()
        val child = openLink("next")
        compose.activityRule.scenario.recreate()
        awaitPage(child, "/two")
        back()
        assertReturned(child)
    }

    @Test fun pageHistoryIsUsedBeforeClosingChild() {
        val child = openLink("popup")
        compose.runOnIdle { webView()!!.loadUrl("${server.origin}/one") }
        awaitPage(child, "/one")
        back()
        awaitPage(child, "/two")
        assertNull(runBlocking { container.workspace.dao.tab(child)!!.closedAt })
        back()
        assertReturned(child)
    }
}
