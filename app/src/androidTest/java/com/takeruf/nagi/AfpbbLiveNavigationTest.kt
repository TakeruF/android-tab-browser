package com.takeruf.nagi

import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Opt-in public-site check; local certificate regressions run separately. */
class AfpbbLiveNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var view: WebView
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    @Before fun setup() {
        Assume.assumeTrue("Explicit live-site opt-in required", InstrumentationRegistry.getArguments().getString("nagiLiveAfpbb") == "true")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun cleanup() {
        if (::engine.isInitialized) main { engine.destroy() }
        if (::scenario.isInitialized) scenario.close()
    }
    @Test fun mobileBareHostRecoversAndRendersTheWwwSite() = exercise(false)
    @Test fun desktopBareHostRecoversAndRendersTheWwwSite() = exercise(true)
    private fun exercise(desktop: Boolean) {
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, desktop)
            view = engine.surface as WebView
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
        }
        fun load(url: String): PageState {
            main { engine.loadUrl(url) }
            return runBlocking { withTimeout(45_000) { engine.state.first {
                it.error != null || (!it.isLoading && it.progress == 100 && it.url.startsWith("https://www.afpbb.com/"))
            } } }
        }
        val bare = load("https://afpbb.com")
        val direct = load("https://www.afpbb.com/")
        val title = CompletableDeferred<String>()
        main { view.evaluateJavascript("document.title") { title.complete(it) } }
        val documentTitle = runBlocking { withTimeout(5_000) { title.await() } }
        File(instrumentation.targetContext.getExternalFilesDir(null), "afpbb-${if (desktop) "desktop" else "mobile"}.txt")
            .writeText("bareUrl=${bare.url}\nbareError=${bare.error}\nbareLoading=${bare.isLoading}\ndirectUrl=${direct.url}\ndirectError=${direct.error}\ndirectLoading=${direct.isLoading}\ndocumentTitle=$documentTitle\n")
        assertNull("Direct www control: ${direct.error}", direct.error)
        assertTrue(documentTitle.contains("AFPBB", ignoreCase = true))
        assertNull("Bare host: ${bare.error}", bare.error)
        assertTrue(bare.url.startsWith("https://www.afpbb.com/"))
    }
}
