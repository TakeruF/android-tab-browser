package com.takeruf.nagi

import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** User-authorized identifier step. No address in test parameters, files, logs or screenshots. */
class GoogleSignInIdentifierTest {
    private fun js(web: WebView, script: String): String {
        val queue = LinkedBlockingQueue<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { web.evaluateJavascript(script) { queue.offer(it) } }
        return checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "No diagnostic callback" }
    }
    private fun tapElement(web: WebView, selector: String): Boolean {
        val point = js(web, """(()=>{const e=document.querySelector('$selector');if(!e)return null;e.scrollIntoView({block:'center',behavior:'instant'});const r=e.getBoundingClientRect();return JSON.stringify({x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth})})()""")
        if (point == "null") return false
        val p = JSONObject(JSONArray("[$point]").getString(0))
        val origin = IntArray(2)
        var ratio = 1f
        val inst = InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync { web.getLocationOnScreen(origin); ratio = web.width.toFloat() / p.getDouble("w").toFloat() }
        val x = origin[0] + p.getDouble("x").toFloat() * ratio
        val y = origin[1] + p.getDouble("y").toFloat() * ratio
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 80)) {
            val event = MotionEvent.obtain(now, time, action, x, y, 0)
            try { inst.sendPointerSync(event) } finally { event.recycle() }
        }
        return true
    }
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    @Test fun compareConsentedIdentifierStep() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("liveSignIn") == "true")
        Assume.assumeTrue(PrivateProfiles.supported())
        val identifier = ServerSocket(39818, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 60_000
            server.accept().use {
                it.getOutputStream().write(1)
                DataInputStream(it.getInputStream()).readUTF()
            }
        }
        check(identifier.length < 255 && '@' in identifier) { "Identifier required" }
        val inst = InstrumentationRegistry.getInstrumentation()
        val context = inst.targetContext
        val output = File(context.getExternalFilesDir(null), "google-signin/${args.getString("label") ?: "identifier"}").apply { mkdirs() }
        val profiles = args.getString("profiles")?.split(',') ?: listOf("mobile-blocking", "desktop-blocking")
        for (profile in profiles) {
            var engine: WebViewBrowserEngine? = null
            var privateName: String? = null
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    privateName = PrivateProfiles.create()
                    engine = WebViewBrowserEngine(activity, host, host, { false }, profile.startsWith("desktop"),
                        siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true),
                        adBlocker = (context.applicationContext as NagiApplication).container.adBlocker,
                        privateProfileName = privateName)
                    engine!!.configureContentBlocking(!profile.endsWith("no-blocking"), emptySet())
                    if (profile.endsWith("third-party")) WebViewCompat.getProfile(engine!!.surface).cookieManager.setAcceptThirdPartyCookies(engine!!.surface, true)
                    activity.addContentView(engine!!.surface, android.view.ViewGroup.LayoutParams(-1, -1))
                    engine!!.loadUrl("https://accounts.google.com/ServiceLogin?service=wise&continue=https%3A%2F%2Fdocs.google.com%2F")
                }
                var input = false
                val started = System.currentTimeMillis()
                while (!input && System.currentTimeMillis() - started < 25_000) {
                    input = js(engine!!.surface, "!!document.querySelector('#identifierId')") == "true"
                    if (!input) delay(300)
                }
                val result = JSONObject().put("profile", profile).put("entry", "docs-signin").put("identifierFieldFound", input)
                if (input) {
                    check(tapElement(engine!!.surface, "#identifierId"))
                    delay(300)
                    inst.sendStringSync(identifier)
                    delay(500)
                    result.put("identifierSet", js(engine!!.surface, "document.querySelector('#identifierId').value===" + JSONObject.quote(identifier)) == "true")
                    inst.runOnMainSync { (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(engine!!.surface.windowToken, 0) }
                    delay(500)
                    result.put("nextClicked", tapElement(engine!!.surface, "#identifierNext"))
                    delay(10_000)
                    val deadline = System.currentTimeMillis() + 15_000
                    while (System.currentTimeMillis() < deadline && js(engine!!.surface, "!!document.querySelector('#identifierId') && document.querySelector('#identifierId').getClientRects().length>0") == "true") delay(500)
                }
                val queue = LinkedBlockingQueue<String>()
                inst.runOnMainSync { engine!!.surface.evaluateJavascript("""
                    (()=>{const t=(document.body?.innerText||'').toLowerCase();
                    const visible=s=>[...document.querySelectorAll(s)].some(e=>e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden');
                    return JSON.stringify({identifier:visible('input[type=email],#identifierId'),password:visible('input[type=password]'),
                    rejected:location.pathname.includes('/rejected')||t.includes('this browser or app may not be secure')||t.includes('couldn’t sign you in')||t.includes("couldn't sign you in"),
                    tryAnotherWay:t.includes('try another way'),accountNotFound:t.includes("couldn't find your google account")||t.includes('couldn’t find your google account'),emptyIdentifier:t.includes('enter an email or phone number'),invalidIdentifier:t.includes('enter a valid email'),rateLimited:t.includes('too many')||t.includes('try again later'),captcha:!!document.querySelector('iframe[src*=recaptcha]'),ready:document.readyState})})()
                """.trimIndent()) { queue.offer(it) } }
                val raw = checkNotNull(queue.poll(10, TimeUnit.SECONDS)) { "No diagnostic callback" }
                val state = engine!!.state.value
                val uri = Uri.parse(state.url)
                result.put("location", "${uri.scheme}://${uri.host}${uri.path}").put("engineError", state.error ?: JSONObject.NULL)
                    .put("blockedRequests", state.blockedRequests).put("signals", JSONObject(JSONArray("[$raw]").getString(0)))
                File(output, "$profile.json").writeText(result.toString(2))
            } finally {
                inst.runOnMainSync { engine?.destroy() }
                privateName?.let { name ->
                    val done = CountDownLatch(1)
                    inst.runOnMainSync { PrivateProfiles.clear(name) { done.countDown() } }
                    check(done.await(10, TimeUnit.SECONDS)) { "Probe cleanup timed out" }
                }
                scenario.close()
            }
        }
    }
}
