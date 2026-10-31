package com.takeruf.nagi

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import android.os.Message
import android.webkit.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.ProfileStore
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.browser.privacy.PrivateProfiles
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic cookies only. Public probes require opt-in and never submit login fields. */
class AuthenticationCompatibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private data class Configuration(val name: String, val desktop: Boolean, val blocking: Boolean = true,
        val thirdParty: Boolean = false)
    // Every comparison with desktop-baseline changes exactly one setting.
    private val configurations = listOf(Configuration("desktop-baseline", true),
        Configuration("mobile", false), Configuration("desktop-no-blocking", true, false),
        Configuration("desktop-third-party", true, thirdParty = true))

    private fun <T> withEngine(configuration: Configuration,
        automaticDesktop: () -> Boolean = { configuration.desktop },
        action: (WebViewBrowserEngine, SiteDisplayModeStore, CookieManager) -> T): T {
        assumeTrue("Isolated profiles required", PrivateProfiles.supported())
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var engine: WebViewBrowserEngine
        lateinit var store: SiteDisplayModeStore
        lateinit var cookies: CookieManager
        lateinit var profile: String
        var engineCreated = false
        var profileCreated = false
        try {
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                profile = PrivateProfiles.create()
                profileCreated = true
                store = SiteDisplayModeStore(activity, inMemory = true)
                engine = WebViewBrowserEngine(activity, host, host, { false }, configuration.desktop,
                    defaultDesktopMode = automaticDesktop,
                    siteDisplayModes = store, privateProfileName = profile,
                    adBlocker = (activity.application as NagiApplication).container.adBlocker)
                engineCreated = true
                cookies = WebViewCompat.getProfile(engine.surface as WebView).cookieManager
                cookies.setAcceptThirdPartyCookies(engine.surface as WebView, configuration.thirdParty)
                engine.configureContentBlocking(configuration.blocking, emptySet())
                activity.addContentView(engine.surface, ViewGroup.LayoutParams(-1, -1))
            }
            return action(engine, store, cookies)
        } finally {
            if (engineCreated) main { engine.destroy() }
            if (profileCreated) runBlocking { withTimeout(15_000) {
                val done = CompletableDeferred<Unit>()
                main { PrivateProfiles.clear(profile) { done.complete(Unit) } }
                done.await()
            } }
            scenario.close()
        }
    }
    private fun awaitPage(engine: WebViewBrowserEngine, url: String) = runBlocking {
        withTimeout(20_000) {
            engine.state.first { it.url == url && !it.isLoading && it.title == "Auth fixture" }
            var matches = false
            while (!matches) {
                val result = CompletableDeferred<String>()
                main { (engine.surface as WebView).evaluateJavascript(
                    "document.readyState==='complete' && document.documentElement.dataset.nagiFixturePath===${JSONObject.quote(Uri.parse(url).path)}") { result.complete(it) } }
                matches = result.await() == "true"
                if (!matches) delay(20)
            }
        }
    }
    private fun signals(view: WebView): JSONObject = runBlocking {
        val done = CompletableDeferred<String>()
        main { view.evaluateJavascript("""
            window.__nagiAuthSignals=null;
            (async()=>{const d=navigator.userAgentData;
              const text=(document.body?.innerText||'').toLowerCase();
              const high=d?await d.getHighEntropyValues(['architecture','bitness','platformVersion','fullVersionList']):{};
              return JSON.stringify({ua:navigator.userAgent,platform:navigator.platform,
                hints:d?d.toJSON():null,high,secure:isSecureContext,
                screenWidth:screen.width,screenHeight:screen.height,innerWidth,innerHeight,dpr:devicePixelRatio,
                identifier:!!document.querySelector('input[type=email],#identifierId'),
                password:!!document.querySelector('input[type=password]'),
                challenge:!!document.querySelector('iframe[src*="challenges.cloudflare.com"],#challenge-running,#challenge-form'),
                turnstileApi:typeof window.turnstile!=='undefined',
                turnstileResponseField:!!document.querySelector('input[name="cf-turnstile-response"]'),
                turnstileTokenPresent:!!document.querySelector('input[name="cf-turnstile-response"]')?.value,
                turnstileEvents:typeof window.__nagiSazankaSignalSnapshot==='function'?window.__nagiSazankaSignalSnapshot():null,
                chat:!!document.querySelector('#prompt-textarea'),rejected:location.pathname.includes('/rejected'),
                ready:document.readyState,contentType:document.contentType,bodyTextLength:text.length,
                scriptCount:document.scripts.length,textarea:!!document.querySelector('textarea,[contenteditable=true]'),
                chatUiText:['ask anything','what can i help','where should we begin'].some(x=>text.includes(x)),
                challengeText:['just a moment','verify you are human','checking your browser'].some(x=>text.includes(x)),
                denialText:['access denied','request blocked','request rejected','forbidden'].some(x=>text.includes(x))});
            })().then(x=>window.__nagiAuthSignals=x)
        """.trimIndent()) {} }
        // evaluateJavascript does not await a JavaScript Promise.
        withTimeout(10_000) {
            var result: String? = null
            while (result == null || result == "null") {
                delay(100)
                val sample = CompletableDeferred<String>()
                main { view.evaluateJavascript("window.__nagiAuthSignals||null") { sample.complete(it) } }
                result = sample.await()
            }
            done.complete(result)
        }
        JSONObject(JSONArray("[${done.await()}]").getString(0))
    }
    private fun write(name: String, result: JSONObject) {
        File(instrumentation.targetContext.getExternalFilesDir(null), "auth-compatibility").apply { mkdirs() }
            .resolve("$name.json").writeText(result.toString(2))
    }
    private fun tapPopup(view: WebView) {
        val bounds = CompletableDeferred<String>()
        main { view.evaluateJavascript("JSON.stringify((()=>{const r=document.getElementById('popup').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth}})())") { bounds.complete(it) } }
        val rect = runBlocking { JSONObject(JSONArray("[${withTimeout(5_000) { bounds.await() }}]").getString(0)) }
        main {
            val scale = view.width / rect.getDouble("w")
            val x = (rect.getDouble("x") * scale).toFloat()
            val y = (rect.getDouble("y") * scale).toFloat()
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0)
            view.dispatchTouchEvent(down); view.dispatchTouchEvent(up)
            down.recycle(); up.recycle()
        }
    }

    @Test fun sameHostRedirectsKeepIdentityAndHttpOnlyCookieForEveryConfiguration() {
        AuthFixture().use { server -> configurations.forEach { configuration ->
            withEngine(configuration) { engine, _, cookies ->
                main { engine.loadUrl("${server.origin}/start") }
                awaitPage(engine, "${server.origin}/finish")
                val requests = server.requests.filter { it.path in setOf("/start", "/middle", "/finish") }.takeLast(3)
                assertEquals(listOf("/start", "/middle", "/finish"), requests.map { it.path })
                assertEquals(1, requests.map { it.ua }.distinct().size)
                assertEquals(1, requests.map { it.mobileHint }.distinct().size)
                assertEquals(1, requests.map { it.platformHint }.distinct().size)
                assertTrue(requests.drop(1).all { it.syntheticCookie })
                assertEquals(if (configuration.desktop) "?0" else "?1", requests.last().mobileHint)
                val view = engine.surface as WebView
                val before = signals(view)
                assertEquals(!configuration.desktop, before.getJSONObject("hints").getBoolean("mobile"))
                val requestsBeforeReload = server.requests.count { it.path == "/finish" }
                main { cookies.flush(); engine.reload() }
                awaitPage(engine, "${server.origin}/finish")
                runBlocking { withTimeout(10_000) {
                    while (server.requests.count { it.path == "/finish" } <= requestsBeforeReload) delay(20)
                } }
                assertTrue(server.requests.last { it.path == "/finish" }.syntheticCookie)
                write("local-${configuration.name}", before.put("redirectRequests", requests.size)
                    .put("cookieSentAfterRedirect", true).put("cookieSentAfterReload", true))
            }
        } }
    }

    @Test fun crossHostRedirectChangesIdentityOnlyWhenSiteChoicesDiffer() {
        AuthFixture().use { server ->
            for (different in listOf(false, true)) withEngine(Configuration("redirect", true)) { engine, store, _ ->
                if (different) store.remember(server.otherOrigin, false)
                main { engine.loadUrl("${server.origin}/cross-host") }
                awaitPage(engine, "${server.otherOrigin}/finish")
                val received = server.requests.last { it.path == "/finish" }
                assertEquals(if (different) "?1" else "?0", received.mobileHint)
                assertEquals(!different, engine.state.value.desktopMode)
                write("cross-host-$different", signals(engine.surface as WebView)
                    .put("changedMode", different))
            }
        }
    }

    @Test fun deviceDefaultRedirectsAndReloadKeepNativeIdentityAndHttpOnlyCookie() {
        AuthFixture().use { server -> withEngine(Configuration("device-default", true)) { engine, modes, cookies ->
            modes.rememberDeviceDefault(server.origin)
            main { engine.loadUrl("${server.origin}/start") }
            awaitPage(engine, "${server.origin}/finish")
            assertTrue(engine.state.value.deviceDefaultMode)
            val requests = server.requests.filter { it.path in setOf("/start", "/middle", "/finish") }
            assertEquals(listOf("/start", "/middle", "/finish"), requests.map { it.path })
            val expected = WebSettings.getDefaultUserAgent((engine.surface as WebView).context)
            assertTrue(requests.all { it.ua == expected })
            assertTrue(requests.drop(1).all { it.syntheticCookie })
            val count = server.requests.count { it.path == "/finish" }
            main { cookies.flush(); engine.reload() }
            awaitPage(engine, "${server.origin}/finish")
            runBlocking { withTimeout(10_000) { while (server.requests.count { it.path == "/finish" } <= count) delay(20) } }
            assertEquals(expected, server.requests.last { it.path == "/finish" }.ua)
            assertTrue(server.requests.last { it.path == "/finish" }.syntheticCookie)
        } }
    }

    @Test fun switchingBackToMobileRestoresDefaultAndroidHints() {
        AuthFixture().use { server -> withEngine(Configuration("toggle", false)) { engine, _, _ ->
            main { engine.loadUrl("${server.origin}/finish") }
            awaitPage(engine, "${server.origin}/finish")
            val mobile = signals(engine.surface as WebView)
            main { engine.setDesktopMode(true) }
            awaitPage(engine, "${server.origin}/finish")
            val desktop = signals(engine.surface as WebView)
            main { engine.setDesktopMode(false) }
            awaitPage(engine, "${server.origin}/finish")
            val restored = signals(engine.surface as WebView)
            assertEquals(mobile.getString("ua"), restored.getString("ua"))
            assertEquals(mobile.getJSONObject("hints").getString("platform"), restored.getJSONObject("hints").getString("platform"))
            for (key in listOf("architecture", "bitness", "platformVersion"))
                assertEquals(mobile.getJSONObject("high").getString(key), restored.getJSONObject("high").getString(key))
            assertEquals("Linux", desktop.getJSONObject("hints").getString("platform"))
            assertEquals("x86", desktop.getJSONObject("high").getString("architecture"))
            write("mode-toggle", JSONObject().put("mobile", mobile).put("desktop", desktop).put("restored", restored))
        } }
    }

    @Test fun bundledOrCachedBlockerAllowsChallengeResources() = runBlocking {
        val blocker = (instrumentation.targetContext.applicationContext as NagiApplication).container.adBlocker
        withTimeout(20_000) { blocker.status.first { it != "loading" } }
        assertEquals("ready", blocker.status.value)
        val results = JSONArray()
        for (source in listOf("https://chatgpt.com/", "https://auth.openai.com/", "https://accounts.google.com/")) {
            for (resource in listOf("https://challenges.cloudflare.com/turnstile/v0/api.js",
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/g/turnstile/if/ov2/fixture",
                source + "cdn-cgi/challenge-platform/h/g/orchestrate/chl_page/v1")) {
                val request = object : WebResourceRequest {
                    override fun getUrl() = Uri.parse(resource)
                    override fun isForMainFrame() = false
                    override fun isRedirect() = false
                    override fun hasGesture() = false
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders() = mapOf("Sec-Fetch-Dest" to if ("/turnstile/if/" in resource) "iframe" else "script")
                }
                assertNull("Challenge fixture must be allowed", blocker.intercept(request, source))
                results.put(JSONObject().put("sourceHost", Uri.parse(source).host)
                    .put("resourceHost", Uri.parse(resource).host).put("blocked", false))
            }
        }
        write("blocker", JSONObject().put("results", results))
    }

    @Test fun thirdPartySyntheticCookieComparison() {
        AuthFixture().use { server ->
            for (allowed in listOf(false, true)) withEngine(Configuration("third-party", true, thirdParty = allowed)) { engine, _, _ ->
                main { engine.loadUrl("${server.otherOrigin}/set-third") }
                awaitPage(engine, "${server.otherOrigin}/set-third")
                main { engine.loadUrl("${server.origin}/embed-third") }
                awaitPage(engine, "${server.origin}/embed-third")
                runBlocking { withTimeout(10_000) { while (server.requests.none { it.path == "/third-resource" }) delay(50) } }
                val sent = server.requests.last { it.path == "/third-resource" }.syntheticCookie
                write("third-party-$allowed", JSONObject().put("allowed", allowed).put("syntheticCookieSent", sent))
                // Loopback Secure cookie behavior is not proof of public HTTPS behavior.
                assertEquals(allowed, sent)
                server.requests.clear()
            }
        }
    }

    @Test fun independentUserAgentAndHintsControlsReachTheWireWithoutLosingCookies() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
        AuthFixture().use { server ->
            val results = JSONArray()
            for (uaMobile in listOf(false, true)) for (hintsMobile in listOf(false, true))
                withEngine(Configuration("independent", false)) { engine, _, _ ->
                    val view = engine.surface as WebView
                    lateinit var mobileUa: String
                    lateinit var desktopUa: String
                    lateinit var mobileMetadata: androidx.webkit.UserAgentMetadata
                    lateinit var desktopMetadata: androidx.webkit.UserAgentMetadata
                    main {
                        mobileUa = view.settings.userAgentString
                        mobileMetadata = WebSettingsCompat.getUserAgentMetadata(view.settings)
                        engine.setDesktopMode(true)
                        desktopUa = view.settings.userAgentString
                        desktopMetadata = WebSettingsCompat.getUserAgentMetadata(view.settings)
                        engine.setDesktopMode(false)
                        // Deliberately mixed identities stay on loopback; never apply these to public sites.
                        view.settings.userAgentString = if (uaMobile) mobileUa else desktopUa
                        WebSettingsCompat.setUserAgentMetadata(view.settings, if (hintsMobile) mobileMetadata else desktopMetadata)
                        engine.loadUrl("${server.origin}/start")
                    }
                    awaitPage(engine, "${server.origin}/finish")
                    val received = server.requests.last { it.path == "/finish" }
                    val js = signals(view)
                    assertEquals(uaMobile, received.ua.contains("Mobile"))
                    assertEquals(if (hintsMobile) "?1" else "?0", received.mobileHint)
                    assertEquals(if (hintsMobile) "\"Android\"" else "\"Linux\"", received.platformHint)
                    assertEquals(hintsMobile, js.getJSONObject("hints").getBoolean("mobile"))
                    assertEquals(uaMobile, js.getString("ua").contains("Mobile"))
                    assertTrue(received.syntheticCookie)
                    results.put(JSONObject().put("uaMobile", uaMobile).put("hintsMobile", hintsMobile)
                        .put("cookieSent", received.syntheticCookie).put("nativePlatform", js.getString("platform")))
                }
            write("independent-identity-controls", JSONObject().put("results", results))
        }
    }

    @Test fun redirectsPreserveMethodsBodiesAndHostScopedCookies() {
        AuthFixture().use { server ->
            val results = JSONArray()
            for (differentMode in listOf(false, true)) withEngine(Configuration("http-semantics", true)) { engine, store, _ ->
                if (differentMode) store.remember(server.otherOrigin, false)
                main { engine.loadUrl("${server.otherOrigin}/seed-target") }
                awaitPage(engine, "${server.otherOrigin}/seed-target")
                for (crossHost in listOf(false, true)) for (method in listOf("GET", "POST"))
                    for (status in listOf(302, 303, 307, 308)) {
                        val suffix = "$status/${if (crossHost) "cross" else "same"}/$method"
                        val entry = "${server.origin}/redirect/$suffix"
                        val target = "${if (crossHost) server.otherOrigin else server.origin}/complete/$suffix"
                        main { engine.loadUrl("${server.origin}/form") }
                        awaitPage(engine, "${server.origin}/form")
                        server.requests.clear()
                        main {
                            engine.evaluateJavascript(if (method == "GET") "location.href=${JSONObject.quote(entry)}" else """
                                (()=>{const f=document.createElement('form');f.method='POST';f.action=${JSONObject.quote(entry)};
                                const i=document.createElement('input');i.name='nagi_prediction';i.value='synthetic';
                                f.append(i);document.body.append(f);f.submit()})()
                            """.trimIndent())
                        }
                        awaitPage(engine, target)
                        val case = "$status $method crossHost=$crossHost differentMode=$differentMode"
                        fun recordCase() = write("redirect-last-case", JSONObject().put("case", case)
                            .put("requests", JSONArray(server.requests.map {
                                JSONObject().put("path", it.path).put("method", it.method)
                                    .put("syntheticBody", it.syntheticBody).put("originCookie", it.originCookie)
                                    .put("targetCookie", it.targetCookie)
                            })))
                        recordCase()
                        // History callbacks can precede the network completion. Check the
                        // wire before asserting counts, rather than treating PageState as proof.
                        try { runBlocking { withTimeout(10_000) {
                            while (server.requests.none { it.path == "/complete/$suffix" }) delay(20)
                        } } } catch (timeout: TimeoutCancellationException) {
                            recordCase()
                            throw AssertionError("No fixture network completion for $case", timeout)
                        }
                        val starts = server.requests.filter { it.path == "/redirect/$suffix" }
                        val finishes = server.requests.filter { it.path == "/complete/$suffix" }
                        recordCase()
                        assertEquals(case, 1, starts.size)
                        assertEquals(case, 1, finishes.size)
                        val start = starts.single()
                        val finish = finishes.single()
                        val expectedMethod = if (method == "POST" && status in listOf(307, 308)) "POST" else "GET"
                        assertEquals(method, start.method)
                        assertEquals(case, expectedMethod, finish.method)
                        assertEquals(method == "POST", start.syntheticBody)
                        assertEquals(expectedMethod == "POST", finish.syntheticBody)
                        assertEquals(expectedMethod != "POST", finish.emptyBody)
                        // Cross-host GET applies the site's choice. POST retains the source identity/body.
                        val expectedMobile = crossHost && differentMode && expectedMethod == "GET"
                        assertEquals(case, expectedMobile, finish.ua.contains("Mobile"))
                        assertEquals(if (expectedMobile) "?1" else "?0", finish.mobileHint)
                        assertEquals(!expectedMobile, engine.state.value.desktopMode)
                        assertEquals(!crossHost, finish.originCookie)
                        if (crossHost && expectedMethod == "GET") assertTrue(finish.targetCookie)
                        results.put(JSONObject().put("status", status).put("sourceMethod", method)
                            .put("targetMethod", finish.method).put("crossHost", crossHost)
                            .put("differentSiteMode", differentMode).put("targetMobile", expectedMobile)
                            .put("bodyPreserved", finish.syntheticBody).put("originCookieSent", finish.originCookie)
                            .put("targetCookieSent", finish.targetCookie))
                        write("redirect-method-controls", JSONObject().put("results", results))
                    }
            }
            write("redirect-method-controls", JSONObject().put("results", results))
        }
    }

    @Test fun popupBootstrapInheritsIdentityAndCookiePolicyFromItsOpener() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
        AuthFixture().use { server ->
            val results = JSONArray()
            for (desktop in listOf(true, false)) for (thirdParty in listOf(false, true))
                withEngine(Configuration("popup", desktop, thirdParty = thirdParty)) { engine, _, cookies ->
                    val view = engine.surface as WebView
                    val observed = CompletableDeferred<JSONObject>()
                    main {
                        val delegate = view.webChromeClient!!
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(v: WebView, progress: Int) = delegate.onProgressChanged(v, progress)
                            override fun onReceivedTitle(v: WebView, title: String?) = delegate.onReceivedTitle(v, title)
                            override fun onReceivedIcon(v: WebView, icon: Bitmap?) = delegate.onReceivedIcon(v, icon)
                            override fun onCreateWindow(v: WebView, dialog: Boolean, gesture: Boolean, message: Message): Boolean {
                                val created = delegate.onCreateWindow(v, dialog, gesture, message)
                                val popup = (message.obj as WebView.WebViewTransport).webView
                                if (created && popup != null) {
                                    val parentHints = WebSettingsCompat.getUserAgentMetadata(v.settings)
                                    val childHints = WebSettingsCompat.getUserAgentMetadata(popup.settings)
                                    observed.complete(JSONObject().put("desktop", desktop).put("thirdParty", thirdParty)
                                        .put("sameUa", v.settings.userAgentString == popup.settings.userAgentString)
                                        .put("sameMobileHint", parentHints.isMobile == childHints.isMobile)
                                        .put("samePlatformHint", parentHints.platform == childHints.platform)
                                        .put("sameMetadata", parentHints == childHints)
                                        .put("sameCookiePolicy", cookies.acceptThirdPartyCookies(v) == cookies.acceptThirdPartyCookies(popup)))
                                }
                                return created
                            }
                        }
                        engine.loadUrl("${server.origin}/form")
                    }
                    awaitPage(engine, "${server.origin}/form")
                    tapPopup(view)
                    val result = runBlocking { withTimeout(10_000) { observed.await() } }
                    results.put(result)
                    write("popup-inheritance-controls", JSONObject().put("results", results))
                    for (key in listOf("sameUa", "sameMobileHint", "samePlatformHint", "sameMetadata", "sameCookiePolicy"))
                        assertTrue("Popup must inherit $key (desktop=$desktop, thirdParty=$thirdParty)", result.getBoolean(key))
                }
        }
    }

    @Test fun popupHandoffUsesDestinationModeAndSharedCookieJarWithoutBootstrapFetch() {
        AuthFixture().use { server ->
            val results = JSONArray()
            for (sourceDesktop in listOf(true, false)) for (destinationDesktop in listOf(true, false))
                withEngine(Configuration("handoff", sourceDesktop)) { parent, store, _ ->
                    val target = "${server.otherOrigin}/handoff-target"
                    val view = parent.surface as WebView
                    store.remember(target, destinationDesktop)
                    main { parent.loadUrl("${server.otherOrigin}/seed-target") }
                    awaitPage(parent, "${server.otherOrigin}/seed-target")
                    main { parent.loadUrl("${server.origin}/form") }
                    awaitPage(parent, "${server.origin}/form")
                    server.requests.clear()
                    main { parent.evaluateJavascript("document.getElementById('popup').onclick=()=>window.open(${JSONObject.quote(target)},'_blank')") }
                    tapPopup(view)
                    val event = runBlocking { withTimeout(10_000) { parent.events.first { it is EngineEvent.OpenTab } as EngineEvent.OpenTab } }
                    assertEquals(target, event.url)
                    instrumentation.waitForIdleSync()
                    assertEquals("URL-capturing popup must not fetch the destination", 0,
                        server.requests.count { it.path == "/handoff-target" })
                    var child: WebViewBrowserEngine? = null
                    try {
                        main {
                            child = WebViewBrowserEngine(view.context, host, host, { false }, sourceDesktop,
                                siteDisplayModes = store, privateProfileName = WebViewCompat.getProfile(view).name)
                            (view.parent as ViewGroup).addView(child!!.surface, ViewGroup.LayoutParams(-1, -1))
                            child!!.loadUrl(event.url)
                        }
                        awaitPage(child!!, target)
                        val received = server.requests.filter { it.path == "/handoff-target" }
                        assertEquals(1, received.size)
                        assertEquals(!destinationDesktop, received.single().ua.contains("Mobile"))
                        assertEquals(if (destinationDesktop) "?0" else "?1", received.single().mobileHint)
                        assertTrue(received.single().targetCookie)
                        assertFalse(received.single().originCookie)
                        assertEquals(sourceDesktop, parent.state.value.desktopMode)
                        results.put(JSONObject().put("sourceDesktop", sourceDesktop).put("destinationDesktop", destinationDesktop)
                            .put("bootstrapRequests", 0).put("destinationRequests", received.size).put("destinationCookieSent", true))
                        write("popup-wire-handoff", JSONObject().put("results", results))
                    } finally { main { child?.destroy() } }
                }
        }
    }

    @Test fun automaticDefaultChangeDuringRedirectAffectsOnlyUnconfiguredHosts() {
        val results = JSONArray()
        for (explicitDesktop in listOf(false, true)) for (changeDefault in listOf(false, true)) {
            val gate = RedirectGate()
            AuthFixture(gate).use { server ->
                var automaticDesktop = true
                withEngine(Configuration("default-change", true), automaticDesktop = { automaticDesktop }) { engine, store, _ ->
                    if (explicitDesktop) store.remember(server.origin, true)
                    try {
                        main { engine.loadUrl("${server.origin}/paused-redirect") }
                        assertTrue("Fixture must receive the initial request", gate.started.await(10, TimeUnit.SECONDS))
                        main { if (changeDefault) automaticDesktop = false }
                        gate.release.countDown()
                        awaitPage(engine, "${server.origin}/finish")
                        val first = server.requests.single { it.path == "/paused-redirect" }
                        val last = server.requests.single { it.path == "/finish" }
                        val expectedDesktop = explicitDesktop || !changeDefault
                        assertFalse(first.ua.contains("Mobile"))
                        assertEquals(!expectedDesktop, last.ua.contains("Mobile"))
                        assertEquals(if (expectedDesktop) "?0" else "?1", last.mobileHint)
                        assertTrue(last.syntheticCookie)
                        results.put(JSONObject().put("explicitDesktop", explicitDesktop).put("changedDefault", changeDefault)
                            .put("startedDesktop", true).put("finishedDesktop", expectedDesktop).put("cookieSent", true))
                        write("redirect-default-change", JSONObject().put("results", results))
                    } finally { gate.release.countDown() }
                }
            }
        }
    }

    @Test fun supersededOrDestroyedNavigationCannotRunQueuedModeSwitch() {
        AuthFixture().use { server ->
            val results = JSONArray()
            for (destroy in listOf(false, true)) withEngine(Configuration("stale-navigation", true)) { engine, store, _ ->
                val stale = "${server.otherOrigin}/cancelled-target"
                val chosen = "${server.origin}/chosen-target"
                store.remember(stale, false)
                main { engine.loadUrl("${server.origin}/form") }
                awaitPage(engine, "${server.origin}/form")
                server.requests.clear()
                val request = object : WebResourceRequest {
                    override fun getUrl() = Uri.parse(stale)
                    override fun isForMainFrame() = true
                    override fun isRedirect() = true
                    override fun hasGesture() = false
                    override fun getMethod() = "GET"
                    override fun getRequestHeaders() = emptyMap<String, String>()
                }
                main {
                    val view = engine.surface as WebView
                    assertTrue(view.webViewClient.shouldOverrideUrlLoading(view, request))
                    if (destroy) engine.destroy() else engine.loadUrl(chosen)
                }
                if (!destroy) awaitPage(engine, chosen)
                instrumentation.waitForIdleSync()
                assertEquals(0, server.requests.count { it.path == "/cancelled-target" })
                if (!destroy) assertEquals(1, server.requests.count { it.path == "/chosen-target" })
                assertTrue(engine.state.value.desktopMode)
                results.put(JSONObject().put("destroyed", destroy).put("staleRequests", 0))
                write("cancelled-navigation-controls", JSONObject().put("results", results))
            }
        }
    }

    /** Driver must force-stop the diagnostic app between these explicit phases. */
    @Test fun persistentSyntheticStorageAcrossSeparateProcesses() {
        val phase = InstrumentationRegistry.getArguments().getString("authPersistencePhase")
        assumeTrue("Explicit multi-process diagnostic only", phase != null)
        require(phase in setOf("seed", "verify-desktop", "verify-mobile", "cleanup"))
        val seedDwellMs = InstrumentationRegistry.getArguments().getString("authPersistenceSeedDwellMs")?.toLong() ?: 0L
        require(seedDwellMs in 0L..6_000L)
        assumeTrue(PrivateProfiles.supported())
        // This name cannot be created by Nagi's normal/private UI. Only this test owns it.
        val profileName = "nagi-auth-persistence-fixture"
        val context = instrumentation.targetContext
        val output = File(context.getExternalFilesDir(null), "auth-compatibility").apply { mkdirs() }
        val sessionFile = File(output, "persistence-session.json")
        if (phase == "cleanup") {
            main {
                val profiles = ProfileStore.getInstance()
                if (profileName in profiles.allProfileNames) assertTrue(profiles.deleteProfile(profileName))
            }
            write("persistence-cleanup", JSONObject().put("ownedProfileDeleted", true).put("processId", android.os.Process.myPid()))
            return
        }
        val version = WebViewCompat.getCurrentWebViewPackage(context)?.versionName
        if (phase == "seed") main {
            val profiles = ProfileStore.getInstance()
            if (profileName in profiles.allProfileNames) assertTrue(profiles.deleteProfile(profileName))
        }
        val session = if (phase == "seed") null else JSONObject(sessionFile.readText())
        if (session != null) assertEquals("Keep the provider fixed across process phases", session.getString("webViewVersion"), version)
        val server = AuthFixture(port = session?.getInt("port") ?: 0)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var engine: WebViewBrowserEngine? = null
        try {
            val url = "${server.origin}/${if (phase == "seed") "persistent-seed" else "persistent-verify"}"
            scenario.onActivity { activity ->
                engine = WebViewBrowserEngine(activity, host, host, { false }, phase != "verify-mobile",
                    siteDisplayModes = SiteDisplayModeStore(activity, inMemory = true), privateProfileName = profileName)
                activity.addContentView(engine!!.surface, ViewGroup.LayoutParams(-1, -1))
                engine!!.loadUrl(url)
            }
            awaitPage(engine!!, url)
            val dom = CompletableDeferred<String>()
            main { (engine!!.surface as WebView).evaluateJavascript("""
                JSON.stringify({localStoragePresent:localStorage.getItem('nagi_auth_storage')==='synthetic',
                  httpOnlyVisible:document.cookie.split(';').some(x=>x.trim().startsWith('nagi_persistent_fixture='))})
            """.trimIndent()) { dom.complete(it) } }
            val domResult = runBlocking { JSONObject(JSONArray("[${withTimeout(5_000) { dom.await() }}]").getString(0)) }
            var cookieInJar = false
            main {
                val cookies = WebViewCompat.getProfile(engine!!.surface as WebView).cookieManager
                cookieInJar = cookies.getCookie(url)?.split(';')?.any {
                    it.trim().substringBefore('=') == "nagi_persistent_fixture"
                } == true
                // Same persistence operation as the regular engine's onPageFinished path.
                cookies.flush()
            }
            val cookieOnWire = server.requests.last { it.path == Uri.parse(url).path }.persistentCookie
            write("persistence-$phase", domResult.put("cookieInJar", cookieInJar).put("cookieOnWire", cookieOnWire)
                .put("desktop", phase != "verify-mobile").put("webViewVersion", version).put("processId", android.os.Process.myPid())
                .put("seedDwellMs", if (phase == "seed") seedDwellMs else 0L))
            assertTrue("Synthetic persistent cookie must be in the jar", cookieInJar)
            assertTrue("Synthetic localStorage must survive", domResult.getBoolean("localStoragePresent"))
            assertFalse("HttpOnly must stay unavailable to JavaScript", domResult.getBoolean("httpOnlyVisible"))
            if (phase != "seed") assertTrue("Persistent cookie must reach the server in the new process", cookieOnWire)
            if (phase == "seed") {
                sessionFile.writeText(JSONObject().put("port", server.port).put("webViewVersion", version).toString())
                // Diagnostic control only: allow async DOM-storage commits before
                // instrumentation finishes and terminates its process. Never applied to browsing.
                android.os.SystemClock.sleep(seedDwellMs)
            }
        } finally {
            main { engine?.destroy() }
            scenario.close()
            server.close()
            // Deliberately retain only this synthetic profile until the cleanup phase.
        }
    }

    @Test fun liveChatGptWithoutCredentials() = livePublicWithoutCredentials("chatgpt")

    @Test fun liveSazankaLoginWithoutCredentials() = livePublicWithoutCredentials("sazanka")

    private fun livePublicWithoutCredentials(site: String) = runBlocking {
        require(site in setOf("chatgpt", "sazanka"))
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in only", args.getString(if (site == "chatgpt") "liveAuth" else "liveSazanka") == "true")
        if (site == "sazanka") assumeTrue("Document-start diagnostic required",
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val selected = args.getString("liveAuthCase")
        require(selected == null || configurations.any { it.name == selected })
        val reducedDesktopVersion = args.getString("liveDesktopReducedVersion") == "true"
        require(!reducedDesktopVersion || (site == "sazanka" && configurations.any { it.name == selected && it.desktop }))
        val nativeIdentity = args.getString("liveNativeIdentity") == "true"
        require(!nativeIdentity || (site == "sazanka" && configurations.any { it.name == selected && it.desktop }))
        require(!nativeIdentity || !reducedDesktopVersion)
        val deviceDefaultChoice = args.getString("liveDeviceDefaultChoice") == "true"
        require(!deviceDefaultChoice || (site == "sazanka" && selected != null && !nativeIdentity && !reducedDesktopVersion))
        val deviceDefaultRecovery = args.getString("liveDeviceDefaultRecovery") == "true"
        require(!deviceDefaultRecovery || (site == "sazanka" && configurations.any { it.name == selected && it.desktop } &&
            !deviceDefaultChoice && !nativeIdentity && !reducedDesktopVersion))
        if (nativeIdentity || reducedDesktopVersion) assumeTrue("UA metadata preservation required",
            WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
        val manual = args.getString("liveSazankaManual") == "true"
        require(!manual || (site == "sazanka" && selected != null))
        require(!manual || !deviceDefaultRecovery)
        val manualNonce = java.util.UUID.randomUUID().toString()
        val manualOutput = File(instrumentation.targetContext.getExternalFilesDir(null), "auth-compatibility")
        val manualDone = File(manualOutput, "sazanka-manual-done")
        if (manual) {
            manualOutput.mkdirs(); manualDone.delete()
            File(manualOutput, "sazanka-manual-ready.json").delete()
        }
        val entry = if (site == "chatgpt") "https://chatgpt.com/" else "https://www.shisetsuyoyaku.city.suginami.tokyo.jp/user/Home"
        fun category(url: Uri): String = when {
            url.host == "chatgpt.com" -> "chatgpt"
            url.host == "auth.openai.com" -> "auth"
            url.host == "www.shisetsuyoyaku.city.suginami.tokyo.jp" -> "sazanka"
            url.host == "www.yoyaku-sports.city.suginami.tokyo.jp" -> "legacy-sazanka"
            url.host == "challenges.cloudflare.com" || url.host.orEmpty().endsWith(".challenges.cloudflare.com") -> "cloudflare"
            url.host == "static.cloudflareinsights.com" -> "cloudflare-analytics"
            else -> "other"
        }
        fun resource(url: Uri): String = when {
            category(url) == "cloudflare" && url.path.orEmpty().contains("/pat/") -> "private-access-token"
            category(url) == "cloudflare" || url.path.orEmpty().startsWith("/cdn-cgi/") -> "challenge"
            url.path.orEmpty().contains("login", ignoreCase = true) -> "login"
            url.path in setOf("/", "/user/", "/user/Home") -> "home"
            else -> "other"
        }
        for (configuration in configurations.filter { selected == null || it.name == selected }) withEngine(configuration) { engine, modes, cookies ->
            val view = engine.surface as WebView
            val observations = CopyOnWriteArrayList<JSONObject>()
            val totalObserved = AtomicInteger()
            val cloudflareObserved = AtomicInteger()
            val identities = ConcurrentHashMap<String, AtomicInteger>()
            val navigations = JSONArray()
            val started = SystemClock.elapsedRealtime()
            main {
                val delegate = view.webViewClient
                fun navigation(phase: String, url: String, redirect: Boolean? = null, status: Int? = null) {
                    val uri = Uri.parse(url)
                    if (navigations.length() < 100) navigations.put(JSONObject().put("phase", phase).put("host", category(uri))
                        .put("resource", resource(uri)).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                        .put("desktop", engine.state.value.desktopMode).put("redirect", redirect).put("httpError", status)
                        .put("clearancePresentInJar", cookies.getCookie(entry)
                            ?.split(';')?.any { it.trim().substringBefore('=') == "cf_clearance" } == true))
                }
                // Preserve the engine's callbacks. Record only allowlisted categories and identity signals.
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                        val response = delegate.shouldInterceptRequest(v, r)
                        val category = category(r.url)
                        if (category == "other" && site != "sazanka") return response
                        totalObserved.incrementAndGet()
                        if (category == "cloudflare" || r.url.path.orEmpty().startsWith("/cdn-cgi/")) cloudflareObserved.incrementAndGet()
                        val observation = JSONObject().put("host", category)
                            .put("resource", resource(r.url)).put("method", r.method)
                            .put("mainFrame", r.isForMainFrame).put("blocked", response != null)
                            .put("mobileUa", r.requestHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value?.contains("Mobile"))
                            .put("mobileHint", r.requestHeaders.entries.firstOrNull { it.key.equals("Sec-CH-UA-Mobile", true) }?.value)
                            .put("platformHint", r.requestHeaders.entries.firstOrNull { it.key.equals("Sec-CH-UA-Platform", true) }?.value)
                        if (site == "sazanka") {
                            val ua = r.requestHeaders.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
                            observation.put("userAgentSha256", ua?.let { value ->
                                java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                            })
                        }
                        identities.getOrPut(observation.toString()) { AtomicInteger() }.incrementAndGet()
                        if (observations.size < 300) observations.add(JSONObject(observation.toString())
                            .put("elapsedMs", SystemClock.elapsedRealtime() - started))
                        return response
                    }
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        navigation("override", r.url.toString(), r.isRedirect)
                        return delegate.shouldOverrideUrlLoading(v, r)
                    }
                    override fun onPageStarted(v: WebView, u: String, f: Bitmap?) {
                        navigation("start", u); delegate.onPageStarted(v, u, f)
                    }
                    override fun onPageFinished(v: WebView, u: String) {
                        navigation("finish", u); delegate.onPageFinished(v, u)
                    }
                    override fun onPageCommitVisible(v: WebView, u: String) = delegate.onPageCommitVisible(v, u)
                    override fun doUpdateVisitedHistory(v: WebView, u: String, reload: Boolean) = delegate.doUpdateVisitedHistory(v, u, reload)
                    override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) = delegate.onReceivedError(v, r, e)
                    override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                        navigation("http-error", r.url.toString(), status = e.statusCode)
                        delegate.onReceivedHttpError(v, r, e)
                    }
                    override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) = delegate.onReceivedSslError(v, h, e)
                }
                if (site == "sazanka") WebViewCompat.addDocumentStartJavaScript(view, """
                    if(window.top===window){
                      const codes=[],events={};
                      Object.defineProperty(window,'__nagiSazankaSignalSnapshot',{value:()=>({codes:codes.slice(),events:{...events}})});
                      window.addEventListener('message',e=>{
                        let h;try{h=new URL(e.origin).hostname}catch(_){return}
                        if(h!=='challenges.cloudflare.com'&&!h.endsWith('.challenges.cloudflare.com'))return;
                        const d=e.data;if(!d||typeof d!=='object'||Array.isArray(d))return;
                        const name=['fail','complete','expired','timeout','reject','refreshRequest','interactionBegin','interactionEnd'].includes(d.event)?d.event:'other';
                        events[name]=(events[name]||0)+1;
                        const code=[d.code,d.errorCode].find(v=>(typeof v==='string'||typeof v==='number')&&/^\d{6}$/.test(String(v)));
                        if(code!==undefined&&codes.length<20)codes.push(String(code));
                      });
                    }
                """.trimIndent(), setOf("https://www.shisetsuyoyaku.city.suginami.tokyo.jp"))
                if (deviceDefaultChoice) modes.rememberDeviceDefault(entry)
                if (nativeIdentity) {
                    // Restore the provider's real tablet identity. This temporary
                    // settings-only view never loads a URL or reads browser storage.
                    val defaults = WebView(view.context)
                    try {
                        val metadata = WebSettingsCompat.getUserAgentMetadata(defaults.settings)
                        view.settings.userAgentString = defaults.settings.userAgentString
                        WebSettingsCompat.setUserAgentMetadata(view.settings, metadata)
                    } finally { defaults.destroy() }
                } else if (reducedDesktopVersion) {
                    // One documented Chromium version-format control, set before
                    // navigation. Keep the actual WebView brand and all UA-CH values.
                    val metadata = WebSettingsCompat.getUserAgentMetadata(view.settings)
                    val original = view.settings.userAgentString
                    val version = Regex("Chrome/([0-9]+)(?:\\.[0-9]+)+").find(original)
                    require(version != null)
                    view.settings.userAgentString = original.replaceRange(version.range,
                        "Chrome/${version.groupValues[1]}.0.0.0")
                    WebSettingsCompat.setUserAgentMetadata(view.settings, metadata)
                }
                engine.loadUrl(entry)
                if (manual) File(manualOutput, "sazanka-manual-ready.json").writeText(JSONObject()
                    .put("case", configuration.name).put("nonce", manualNonce).put("maximumSeconds", 180).toString())
            }
            var beforeLogin: JSONObject? = null
            var loginAction = "not-applicable"
            if (site == "sazanka" && !manual) {
                runBlocking { delay(15_000) }
                beforeLogin = signals(view)
                val clicked = CompletableDeferred<String>()
                main { view.evaluateJavascript("""
                    (()=>{
                      if(location.hostname!=='www.shisetsuyoyaku.city.suginami.tokyo.jp')return 'wrong-host';
                      if(document.querySelector('input[type=password]'))return 'login-already-visible';
                      if(document.querySelector('iframe[src*="challenges.cloudflare.com"],#challenge-running,#challenge-form'))return 'challenge-visible';
                      const label=e=>{
                        if(e.matches('input[type=button]'))return e.value.trim();
                        const copy=e.cloneNode(true);
                        copy.querySelectorAll('svg,i,.material-icons,.material-symbols-outlined,[aria-hidden=true]').forEach(x=>x.remove());
                        return (e.getAttribute('aria-label')||copy.textContent).trim().replace(/\s+/g,' ');
                      };
                      const isLogin=e=>/^(ログイン|ログインする|log\s*in|sign\s*in)$/i.test(label(e));
                      const link=[...document.querySelectorAll('a[href]')].find(a=>{
                        if(!isLogin(a))return false;
                        const u=new URL(a.href,location.href);
                        return u.protocol==='https:'&&u.hostname===location.hostname&&/login/i.test(u.pathname)&&!u.username&&!u.password;
                      });
                      document.addEventListener('submit',e=>e.preventDefault(),true);
                      if(link){link.click();return 'public-login-link-clicked';}
                      if(!/^\/user(?:\/Home)?\/?$/i.test(location.pathname))return 'no-safe-login-link';
                      const button=[...document.querySelectorAll('button,input[type=button],[role=button]')]
                        .find(e=>{
                          const form=e.form||e.closest('form');
                          const safeForm=!form||(e.type==='button'&&form.method==='get'&&!form.querySelector('input,textarea,select'));
                          return !e.disabled&&safeForm&&e.getClientRects().length>0&&isLogin(e);
                        });
                      if(button){button.click();return 'public-login-button-clicked';}
                      return 'no-safe-login-control';
                    })()
                """.trimIndent()) { clicked.complete(it) } }
                loginAction = runBlocking { JSONArray("[${withTimeout(5_000) { clicked.await() }}]").getString(0) }
            }
            if (manual) {
                loginAction = "manual-user-navigation"
                runBlocking {
                    val deadline = SystemClock.elapsedRealtime() + 180_000
                    while (SystemClock.elapsedRealtime() < deadline &&
                        !(manualDone.exists() && manualDone.readText().trim() == manualNonce)) delay(250)
                }
            } else runBlocking { delay(25_000) }
            var beforeDeviceDefault: JSONObject? = null
            if (deviceDefaultRecovery) {
                beforeDeviceDefault = signals(view)
                beforeDeviceDefault.put("humanVerificationHint", engine.state.value.humanVerification.turnstileFailed)
                main { engine.setDeviceDefaultMode() }
                runBlocking { delay(25_000) }
            }
            val result = signals(view).put("configuration", configuration.name)
                .put("humanVerificationHint", engine.state.value.humanVerification.turnstileFailed)
                .put("site", site).put("beforeLogin", beforeLogin).put("loginAction", loginAction)
                .put("manual", manual)
                .put("reducedDesktopVersion", reducedDesktopVersion)
                .put("nativeIdentity", nativeIdentity)
                .put("deviceDefaultChoice", deviceDefaultChoice)
                .put("deviceDefaultMode", engine.state.value.deviceDefaultMode)
                .put("deviceDefaultRecovery", deviceDefaultRecovery).put("beforeDeviceDefault", beforeDeviceDefault)
                .put("storedDeviceDefaultChoice", modes.mode(entry) == SiteDisplayMode.DEVICE_DEFAULT)
                .put("observations", JSONArray(observations.toList()))
                .put("observationLimitReached", observations.size >= 300)
                .put("totalObserved", totalObserved.get()).put("cloudflareObserved", cloudflareObserved.get())
                .put("identityCounts", JSONArray(identities.entries.map { JSONObject(it.key).put("count", it.value.get()) }))
                .put("navigations", navigations)
                .put("loading", engine.state.value.isLoading).put("hasEngineError", engine.state.value.error != null)
                .put("blockedRequests", engine.state.value.blockedRequests)
            main {
                // Cookie values never leave this callback. Absence of HttpOnly cookies in JS is not evidence.
                result.put("clearancePresentInJar", cookies.getCookie(entry)
                    ?.split(';')?.any { it.trim().substringBefore('=') == "cf_clearance" } == true)
                result.put("webViewVersion", WebViewCompat.getCurrentWebViewPackage(instrumentation.targetContext)?.versionName)
            }
            write(if (site == "chatgpt") "live-${configuration.name}" else
                "live-sazanka-${configuration.name}${if (deviceDefaultRecovery) "-device-default-recovery" else if (deviceDefaultChoice) "-device-default-choice" else if (nativeIdentity) "-native-identity" else if (reducedDesktopVersion) "-reduced-version" else ""}", result)
        }
    }
}

/** Loopback fixture discards all Cookie values after comparing synthetic test names. */
private class RedirectGate {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
}

private class AuthFixture(private val gate: RedirectGate? = null, port: Int = 0) : AutoCloseable {
    private val socket = ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"))
    val port get() = socket.localPort
    private val executor = Executors.newCachedThreadPool()
    val origin = "http://127.0.0.1:${socket.localPort}"
    val otherOrigin = "http://localhost:${socket.localPort}"
    data class Request(val path: String, val ua: String, val mobileHint: String?, val platformHint: String?, val syntheticCookie: Boolean,
        val method: String, val syntheticBody: Boolean, val emptyBody: Boolean, val originCookie: Boolean, val targetCookie: Boolean,
        val persistentCookie: Boolean)
    val requests = CopyOnWriteArrayList<Request>()
    init { executor.execute {
        while (!socket.isClosed) runCatching {
            val accepted = socket.accept()
            executor.execute { runCatching { accepted.use { client ->
                client.soTimeout = 5_000
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine()?.split(' ') ?: return@use
                val method = requestLine[0]
                val path = requestLine.getOrNull(1) ?: return@use
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                require(length in 0..4096)
                val chars = CharArray(length)
                var read = 0
                while (read < length) { val count = reader.read(chars, read, length - read); if (count < 0) break; read += count }
                val bodyMatches = String(chars, 0, read) == "nagi_prediction=synthetic"
                val cookieNames = headers["cookie"]?.split(';')?.map { it.trim().substringBefore('=') }.orEmpty()
                requests.add(Request(path, headers["user-agent"].orEmpty(), headers["sec-ch-ua-mobile"],
                    headers["sec-ch-ua-platform"], headers["cookie"]?.split(';')?.any {
                        it.trim().substringBefore('=') in setOf("nagi_auth_fixture", "nagi_third_fixture") } == true,
                    method, bodyMatches, read == 0, "nagi_origin_session" in cookieNames, "nagi_target_session" in cookieNames,
                    "nagi_persistent_fixture" in cookieNames))
                val redirectParts = path.removePrefix("/redirect/").split('/')
                val location = when {
                    path == "/start" -> "/middle"
                    path == "/middle" -> "/finish"
                    path == "/cross-host" -> "$otherOrigin/finish"
                    path == "/paused-redirect" -> "/finish"
                    path.startsWith("/redirect/") -> "${if (redirectParts[1] == "cross") otherOrigin else origin}/complete/${redirectParts.joinToString("/")}"
                    else -> null
                }
                val cookie = when (path) {
                    "/start", "/paused-redirect" -> "Set-Cookie: nagi_auth_fixture=synthetic; Path=/; HttpOnly; SameSite=Lax\r\n"
                    "/set-third" -> "Set-Cookie: nagi_third_fixture=synthetic; Path=/; Secure; SameSite=None\r\n"
                    "/form" -> "Set-Cookie: nagi_origin_session=synthetic; Path=/; HttpOnly; SameSite=Lax\r\n"
                    "/seed-target" -> "Set-Cookie: nagi_target_session=synthetic; Path=/; HttpOnly; SameSite=Lax\r\n"
                    "/persistent-seed" -> "Set-Cookie: nagi_persistent_fixture=synthetic; Path=/; HttpOnly; SameSite=Lax; Max-Age=3600\r\n"
                    else -> ""
                }
                val body = "<html><head><title>Auth fixture</title><meta name='viewport' content='width=device-width,initial-scale=1'></head><body>" +
                    "<button id='popup' style='margin:30px;width:200px;height:90px' onclick=\"window.open('/popup-child','_blank')\">Open fixture popup</button>" +
                    (if (path == "/embed-third") "<iframe src='$otherOrigin/third-resource'></iframe>" else "") +
                    (if (path == "/persistent-seed") "<script>localStorage.setItem('nagi_auth_storage','synthetic')</script>" else "") +
                    "<script>document.documentElement.dataset.nagiFixturePath=${JSONObject.quote(path)}</script></body></html>"
                val bytes = body.toByteArray()
                val status = if (location == null) "200 OK" else if (path.startsWith("/redirect/")) "${redirectParts[0]} Redirect" else "302 Found"
                val redirect = location?.let { "Location: $it\r\n" }.orEmpty()
                if (path == "/paused-redirect" && gate != null) {
                    gate.started.countDown()
                    check(gate.release.await(15, TimeUnit.SECONDS))
                }
                client.getOutputStream().write(("HTTP/1.1 $status\r\n$redirect$cookie" +
                    "Accept-CH: Sec-CH-UA-Platform-Version, Sec-CH-UA-Arch, Sec-CH-UA-Bitness\r\n" +
                    "Cache-Control: no-store\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                client.getOutputStream().write(bytes)
            } } }
        }
    } }
    override fun close() { gate?.release?.countDown(); socket.close(); executor.shutdownNow() }
}
