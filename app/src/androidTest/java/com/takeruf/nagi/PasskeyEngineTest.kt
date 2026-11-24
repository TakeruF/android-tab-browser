package com.takeruf.nagi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.takeruf.nagi.browser.engine.*
import com.takeruf.nagi.testing.PasskeyHarnessService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

/** Tests real WebView WebAuthn behavior. Does not claim provider approval or a completed login. */
class PasskeyEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var server: FixtureServer
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: android.view.View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText().trim() }
    private fun title(prefix: String): String = runBlocking {
        withTimeout(10_000) { engine.state.first { it.title.startsWith(prefix) }.title }
    }
    @Before fun setup() {
        server = FixtureServer(pages = mapOf("/passkey" to "<html><head><title>Passkey fixture</title></head><body>Passkey fixture</body></html>"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            it.addContentView(engine.surface, android.view.ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close(); server.close() }
    private fun securePage() {
        // Loopback is a secure context. Use a real document navigation rather than a
        // synthetic HTTPS base URL, which newer WebViews may reject before RP validation.
        main { engine.loadUrl(server.origin + "/passkey") }
        title("Passkey fixture")
    }
    @Test fun browserModeAndOriginPermissionAreConfigured() {
        main {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                assertEquals(WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER,
                    WebSettingsCompat.getWebAuthenticationSupport((engine.surface as WebView).settings))
            }
            if (Build.VERSION.SDK_INT >= 34) assertEquals(PackageManager.PERMISSION_GRANTED,
                engine.surface.context.checkSelfPermission(Manifest.permission.CREDENTIAL_MANAGER_SET_ORIGIN))
        }
        securePage()
        main { engine.evaluateJavascript("document.title='capability:'+typeof PublicKeyCredential+':'+typeof navigator.credentials.create+':'+typeof navigator.credentials.get") }
        val actual = title("capability:")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) assertEquals("capability:function:function:function", actual)
        else assertTrue(actual.startsWith("capability:undefined:"))
    }
    @Test fun unrelatedRelyingPartyIsRejectedByNativeWebAuthn() {
        Assume.assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION))
        securePage()
        main { engine.evaluateJavascript("""
            navigator.credentials.create({publicKey:{challenge:new Uint8Array(32),
                rp:{id:'unrelated.test',name:'Invalid RP'},user:{id:new Uint8Array([1]),name:'fixture',displayName:'Fixture'},
                pubKeyCredParams:[{type:'public-key',alg:-7}]}})
              .then(()=>document.title='rp:unexpected-success',e=>document.title='rp:'+e.name);
        """) }
        assertEquals("rp:SecurityError", title("rp:"))
    }
    @Test fun canceledAuthenticationRejectsWithoutOpeningProviderUi() {
        Assume.assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION))
        securePage()
        main { engine.evaluateJavascript("""
            var controller=new AbortController();controller.abort();
            navigator.credentials.get({publicKey:{challenge:new Uint8Array(32),rpId:'nagi.test'},signal:controller.signal})
              .then(()=>document.title='cancel:unexpected-success',e=>document.title='cancel:'+e.name);
        """) }
        assertEquals("cancel:AbortError", title("cancel:"))
    }

    @Test fun validAuthenticationReachesNativeProviderWithWebsiteOrigin() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 34)
        Assume.assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION))
        val previous = shell("settings get secure credential_service")
        val previousPrimary = shell("settings get secure credential_service_primary")
        // UiAutomation executes the command directly; shell quoting would become part
        // of the stored provider name. Component lists contain no shell whitespace.
        val componentList = Regex("[A-Za-z0-9_./:$]+")
        listOf(previous, previousPrimary).filter { it != "null" && it.isNotBlank() }
            .forEach { require(componentList.matches(it)) { "Unexpected credential provider setting format" } }
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        val component = "$packageName/com.takeruf.nagi.testing.PasskeyHarnessService"
        val origin = server.origin.replace("127.0.0.1", "localhost")
        fun restore(key: String, value: String) {
            shell(if (value == "null" || value.isBlank()) "settings delete secure $key" else "settings put secure $key $value")
        }
        try {
            PasskeyHarnessService.expectedOrigin = origin
            PasskeyHarnessService.matchingRequests.set(0)
            PasskeyHarnessService.receivedRequests.set(0)
            PasskeyHarnessService.lastCallerMatched = false
            PasskeyHarnessService.lastOriginMatched = false
            PasskeyHarnessService.lastTypeMatched = false
            PasskeyHarnessService.lastOriginProbe = "no-provider"
            shell("settings put secure credential_service $component")
            shell("settings put secure credential_service_primary $component")
            assertEquals(component, shell("settings get secure credential_service"))
            assertEquals(component, shell("settings get secure credential_service_primary"))
            main { engine.loadUrl("$origin/passkey") }
            title("Passkey fixture")
            main { engine.evaluateJavascript("""
                navigator.credentials.get({publicKey:{challenge:new Uint8Array(32),rpId:'localhost',timeout:5000}})
                  .then(()=>document.title='native:unexpected-success',e=>document.title='native:'+e.name);
            """) }
            assertEquals("native:NotAllowedError", title("native:"))
            // Only fixture-origin comparison flags; never log a challenge or credential.
            android.util.Log.i("NagiPasskeyProbe", PasskeyHarnessService.lastOriginProbe)
            assertTrue("Native GET must reach the provider with its website origin: " +
                "received=${PasskeyHarnessService.receivedRequests.get()}, " +
                "caller=${PasskeyHarnessService.lastCallerMatched}, " +
                "origin=${PasskeyHarnessService.lastOriginMatched}, type=${PasskeyHarnessService.lastTypeMatched}, " +
                PasskeyHarnessService.lastOriginProbe,
                PasskeyHarnessService.matchingRequests.get() > 0)
        } finally {
            PasskeyHarnessService.expectedOrigin = null
            restore("credential_service", previous)
            restore("credential_service_primary", previousPrimary)
            assertEquals(previous.takeUnless { it.isBlank() } ?: "null", shell("settings get secure credential_service"))
            assertEquals(previousPrimary.takeUnless { it.isBlank() } ?: "null", shell("settings get secure credential_service_primary"))
        }
    }
}
