package com.takeruf.nagi

import android.net.Uri
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Live diagnostic, explicitly opted in. Does not submit identifiers or credentials. */
class GoogleSignInCompatibilityTest {
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    @Test fun livePublicSignInWithActualNagiEngine() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        Assume.assumeTrue("Opt-in only", InstrumentationRegistry.getArguments().getString("liveSignIn") == "true")
        Assume.assumeTrue("Never use the user's regular cookie profile", PrivateProfiles.supported())
        val context = instrumentation.targetContext
        val label = InstrumentationRegistry.getArguments().getString("label") ?: "nagi-engine"
        val output = File(context.getExternalFilesDir(null), "google-signin/$label").apply { mkdirs() }
        val blocker = (context.applicationContext as NagiApplication).container.adBlocker
        for ((profile, desktop, blocking) in listOf(
            Triple("mobile-blocking", false, true), Triple("mobile-no-blocking", false, false),
            Triple("desktop-blocking", true, true)
        )) for ((entry, url) in listOf(
            "accounts" to "https://accounts.google.com/", "docs" to "https://docs.google.com/document/u/0/",
            "gmail" to "https://mail.google.com/mail/u/0/"
        )) {
            var engine: WebViewBrowserEngine? = null
            var name: String? = null
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    name = PrivateProfiles.create()
                    engine = WebViewBrowserEngine(activity, host, host, { false }, desktop,
                        siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true),
                        adBlocker = blocker, privateProfileName = name)
                    activity.addContentView(engine!!.surface, android.view.ViewGroup.LayoutParams(-1, -1))
                    engine!!.configureContentBlocking(blocking, emptySet())
                    engine!!.loadUrl(url)
                }
                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < 35_000) {
                    val state = engine!!.state.value
                    if (!state.isLoading && state.url.startsWith("https://accounts.google.com/") && System.currentTimeMillis() - started > 6_000) break
                    delay(300)
                }
                delay(2_000)
                val queue = LinkedBlockingQueue<String>()
                instrumentation.runOnMainSync {
                    (engine!!.surface as WebView).evaluateJavascript("""
                        (()=>{const t=(document.body?.innerText||'').toLowerCase();
                        const visible=s=>[...document.querySelectorAll(s)].some(e=>e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden');return JSON.stringify({
                        identifier:visible('input[type=email],#identifierId'),
                        password:visible('input[type=password]'),
                        rejected:location.pathname.includes('/rejected')||t.includes('this browser or app may not be secure')||t.includes('couldn’t sign you in')||t.includes("couldn't sign you in"),
                        secure:isSecureContext,ua:navigator.userAgent,ready:document.readyState})})()
                    """.trimIndent()) { queue.offer(it) }
                }
                val raw = checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "No public page diagnostic callback" }
                val state = engine!!.state.value
                val uri = Uri.parse(state.url)
                val result = JSONObject().put("profile", profile).put("entry", entry)
                    .put("location", "${uri.scheme}://${uri.host}${uri.path}")
                    .put("loading", state.isLoading).put("engineError", state.error ?: JSONObject.NULL)
                    .put("blockedRequests", state.blockedRequests)
                    .put("signals", JSONObject(JSONArray("[$raw]").getString(0)))
                File(output, "$profile-$entry.json").writeText(result.toString(2))
            } finally {
                instrumentation.runOnMainSync { engine?.destroy() }
                name?.let { profileName ->
                    val cleared = java.util.concurrent.CountDownLatch(1)
                    instrumentation.runOnMainSync { PrivateProfiles.clear(profileName) { cleared.countDown() } }
                    check(cleared.await(10, TimeUnit.SECONDS)) { "Probe profile cleanup timed out" }
                }
                scenario.close()
            }
        }
    }
}
