package com.takeruf.nagi

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.favicon.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import java.io.File

/** Explicitly opt in with -e liveFavicon true. These are time-sensitive provider checks. */
class FaviconLiveSiteTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val evidence get() = File(context.getExternalFilesDir(null), "favicon-live").apply { mkdirs() }
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun optIn() = Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveFavicon") == "true")
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun js(engine: BrowserEngine, script: String): String {
        val result = CompletableDeferred<String>()
        main { ((engine as AndroidEngineSurface).surface as WebView).evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(10_000) { result.await() } }
    }

    @Test fun githubAndChatgptActualWebViewFavicons() {
        optIn()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var engine: BrowserEngine
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, false,
                siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true))
            activity.addContentView((engine as AndroidEngineSurface).surface, ViewGroup.LayoutParams(-1, -1))
        }
        val store = FaviconStore.get(context)
        val results = JSONArray()
        val failures = mutableListOf<String>()
        try {
            for (site in listOf("https://github.com/", "https://chatgpt.com/")) {
                val record = JSONObject().put("site", site)
                results.put(record)
                try {
                    for (dark in listOf(true, false)) {
                        main { engine.setDarkTheme(dark); engine.loadUrl(site) }
                        runBlocking { withTimeout(60_000) { engine.state.first { !it.isLoading && it.url != "about:blank" } } }
                        val page = engine.state.value
                        val origin = faviconOrigin(page.url) ?: error("Not an HTTP(S) page")
                        runBlocking { withTimeout(30_000) {
                            store.icons.first { it[origin]?.exact(dark) != null }[origin]!!.exact(dark)!!
                        } }
                        // Public sites can update provisional icons again after hydration.
                        // Record a quiet final observation rather than the first bitmap.
                        val path = runBlocking { withTimeout(30_000) {
                            val started = android.os.SystemClock.elapsedRealtime()
                            var changed = started
                            var current = store.icons.value[origin]?.exact(dark)
                            while (android.os.SystemClock.elapsedRealtime() - started < 3000 ||
                                android.os.SystemClock.elapsedRealtime() - changed < 2000) {
                                delay(100)
                                val next = store.icons.value[origin]?.exact(dark)
                                if (next != current) { current = next; changed = android.os.SystemClock.elapsedRealtime() }
                            }
                            current ?: error("Icon disappeared")
                        } }
                        val theme = if (dark) "dark" else "light"
                        File(path).copyTo(File(evidence, "${java.net.URI(site).host}-$theme.png"), overwrite = true)
                        val dom = js(engine, """JSON.stringify({dark:matchMedia('(prefers-color-scheme:dark)').matches,
                            icons:Array.from(document.querySelectorAll('link[rel][href]')).filter(l=>/icon/i.test(l.rel))
                            .map(l=>({rel:l.rel,href:l.href,media:l.media,matches:!l.media||matchMedia(l.media).matches}))})""")
                        record.put(theme, JSONObject().put("url", page.url).put("title", page.title).put("error", page.error)
                            .put("path", path).put("native", FaviconDiskCache(File(context.filesDir, "favicons")).hasNative(origin, dark))
                            .put("document", org.json.JSONTokener(dom).nextValue()))
                        assertNull("Page failed: ${page.url}", page.error)
                        assertFalse("Provider challenge reached", page.title.contains("Just a moment", ignoreCase = true))
                    }
                    // Remove the live page, then switch repeatedly using only the learned index.
                    main { engine.loadUrl("about:blank") }
                    for (dark in listOf(true, false, true, false)) {
                        main { engine.setDarkTheme(dark) }
                        assertEquals(record.getJSONObject(if (dark) "dark" else "light").getString("path"),
                            store.icons.value[faviconOrigin(site)]?.select(dark))
                    }
                    record.put("cacheSwitch", true)
                } catch (error: Exception) {
                    record.put("failure", error.toString()).put("state", engine.state.value.toString())
                    failures.add("$site: $error")
                } catch (error: AssertionError) {
                    record.put("failure", error.toString())
                    failures.add("$site: $error")
                }
                File(evidence, "results.json").writeText(results.toString(2))
            }
        } finally { main { engine.destroy() }; scenario.close() }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun reopenLiveCacheWithoutCreatingAPage() {
        optIn()
        val records = JSONArray(File(evidence, "results.json").readText())
        val store = FaviconStore(context)
        assertEquals(2, records.length())
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            assertFalse(record.optString("failure"), record.has("failure"))
            val site = record.getString("site")
            runBlocking { store.resolve(site) }
            for (dark in listOf(true, false)) {
                val expected = record.getJSONObject(if (dark) "dark" else "light").getString("path")
                assertEquals(expected, store.icons.value[faviconOrigin(site)]?.select(dark))
                assertTrue(File(expected).isFile)
            }
        }
    }
}
