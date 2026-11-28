package com.takeruf.nagi

import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.search.InputResolver
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.ThemeMode
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercises Android intent delivery and the Room/session/native-window handoff in the real app. */
class BrowserLaunchUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val container get() = (compose.activity.application as NagiApplication).container
    private fun setup(): String = runBlocking {
        container.workspace.ready.await()
        val dark = InstrumentationRegistry.getArguments().getString("qaDark") == "true"
        container.settings.update { BrowserSettings(automaticSearchRegion = false, defaultSearchEngineId = "google",
            theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        container.spaces.create("Launch QA ${System.nanoTime()}")
        container.settings.settings.first().selectedSpaceId
    }
    private fun views(view: View): List<WebView> = if (view is WebView) listOf(view)
        else if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun page(url: String): WebView {
        lateinit var result: WebView
        compose.runOnIdle { result = views(compose.activity.window.decorView).single { it.url == url } }
        return result
    }
    private fun script(view: WebView, value: String): String = runBlocking {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(value) { result.complete(it) } }
        withTimeout(5_000) { result.await() }
    }
    private fun click(view: WebView, id: String) {
        val bounds = JSONObject(JSONArray("[${script(view, "JSON.stringify((()=>{const r=document.getElementById('$id').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})())")}]").getString(0))
        compose.runOnIdle {
            val ratio = view.width / bounds.getDouble("w")
            val x = (bounds.getDouble("x") * ratio).toFloat()
            val y = (bounds.getDouble("y") * ratio).toFloat()
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
    }
    private fun awaitTitle(space: String, title: String) {
        compose.waitUntil(15_000) { runBlocking {
            val active = container.workspace.dao.spaces().first { it.id == space }.activeTabId
            active?.let { container.workspace.dao.tab(it)?.title } == title
        } }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "launch-qa").apply { mkdirs() }
        val width = compose.activity.resources.configuration.screenWidthDp
        val dark = InstrumentationRegistry.getArguments().getString("qaDark") == "true"
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name-$width-${if (dark) "dark" else "light"}.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun androidWebSearchListsNagiAndUsesConfiguredEngineOnWarmDelivery() {
        val space = setup()
        val appContainer = container
        val activity = compose.activity
        val originalIntent = activity.intent
        try {
            val query = "公路交通 https://example.com & Kotlin + Compose"
            val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
            val matches = activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue(matches.any { it.activityInfo.packageName == activity.packageName })
            val engine = runBlocking { appContainer.workspace.snapshot.first().searchEngines.first { it.id == "google" } }
            val expected = InputResolver.search(engine, query).url
            fun awaitSearch() = compose.waitUntil(15_000) { runBlocking {
                appContainer.workspace.dao.tabs(space).any { it.url == expected && it.closedAt == null }
            } }
            compose.runOnIdle { activity.startActivity(Intent(intent).setPackage(activity.packageName)) }
            awaitSearch()
            val second = "bd literal https://example.com"
            // Deliver via Android's exported Activity, not a direct ViewModel call.
            compose.runOnIdle { activity.startActivity(Intent(Intent.ACTION_WEB_SEARCH)
                .setPackage(activity.packageName).putExtra(SearchManager.QUERY, second)) }
            compose.waitUntil(15_000) { runBlocking { appContainer.workspace.dao.tabs(space).any {
                it.url == InputResolver.search(engine, second).url && it.closedAt == null
            } } }
        } finally {
            // ActivityScenario matches lifecycle events against the original launch intent.
            // MainActivity correctly replaces getIntent() when a search is delivered.
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.intent = originalIntent }
        }
    }

    @Test fun androidWebSearchIsHandledOnColdActivityLaunch() {
        val space = setup()
        val appContainer = container
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val query = "https://example.com still searches"
        val engine = runBlocking { appContainer.workspace.snapshot.first().searchEngines.first { it.id == "google" } }
        compose.activityRule.scenario.close()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)).use {
            runBlocking { withTimeout(15_000) { appContainer.workspace.snapshot.first { workspace ->
                workspace.tabs.any { it.spaceId == space && it.url == InputResolver.search(engine, query).url }
            } } }
        }
    }

    @Test fun livePopupReturnsToItsOpenerAndPreservesTheDraft() {
        val space = setup()
        FixtureServer(pages = mapOf(
            "/auth-parent" to """<meta name="viewport" content="width=device-width"><title>Auth parent</title>
                <input id="draft" value=""><button id="popup" style="width:200px;height:80px" onclick="sessionStorage.setItem('fixture','synthetic');window.open('/auth-child','_blank')">Sign in</button>
                <script>addEventListener('message',e=>{if(e.origin===location.origin&&e.data==='done')document.title='Auth returned'})</script>""",
            "/auth-child" to """<meta name="viewport" content="width=device-width"><title>Auth child</title>
                <button id="finish" style="width:200px;height:80px" onclick="opener.postMessage('done',location.origin);window.close()">Finish</button>"""
        )).use { server ->
            var rightId: String? = null
            val parentId = runBlocking {
                val parent = container.tabs.create(space, "${server.origin}/auth-parent")
                if (InstrumentationRegistry.getArguments().getString("qaSplit") == "true") {
                    rightId = container.tabs.create(space, "${server.origin}/two", select = false)
                    container.tabs.pair(parent, rightId!!)
                    container.tabs.select(parent)
                }
                parent
            }
            awaitTitle(space, "Auth parent")
            val source = page("${server.origin}/auth-parent")
            script(source, "document.getElementById('draft').value='retained draft'")
            click(source, "popup")
            awaitTitle(space, "Auth child")
            val child = page("${server.origin}/auth-child")
            assertEquals("true", script(child, "!!opener && sessionStorage.getItem('fixture')==='synthetic'"))
            screenshot("popup-child")
            click(child, "finish")
            awaitTitle(space, "Auth returned")
            assertSame(source, page("${server.origin}/auth-parent"))
            assertEquals("\"retained draft\"", script(source, "document.getElementById('draft').value"))
            assertEquals(rightId, runBlocking { container.workspace.dao.tab(parentId)!!.splitRightTabId })
            screenshot("popup-returned")
            val tabs = runBlocking { container.workspace.dao.tabs(space) }
            assertEquals(1, tabs.count { it.url.endsWith("/auth-child") && it.closedAt != null })
        }
    }
}
