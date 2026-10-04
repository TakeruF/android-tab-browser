package com.orbit.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.orbit.browser.browser.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*

/** Tests real WebView WebAuthn behavior. Does not claim provider approval or a completed login. */
class PasskeyEngineTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
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
    private fun title(prefix: String): String = runBlocking {
        withTimeout(10_000) { engine.state.first { it.title.startsWith(prefix) }.title }
    }
    @Before fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            engine = WebViewBrowserEngine(it, host, host, { false }, false)
            it.addContentView(engine.surface, android.view.ViewGroup.LayoutParams(-1, -1))
        }
    }
    @After fun cleanup() { main { engine.destroy() }; scenario.close() }
    private fun securePage() {
        main { (engine.surface as WebView).loadDataWithBaseURL("https://orbit.test/", "<html><head><title>Passkey fixture</title></head><body>Passkey fixture</body></html>", "text/html", "UTF-8", null) }
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
            navigator.credentials.get({publicKey:{challenge:new Uint8Array(32),rpId:'orbit.test'},signal:controller.signal})
              .then(()=>document.title='cancel:unexpected-success',e=>document.title='cancel:'+e.name);
        """) }
        assertEquals("cancel:AbortError", title("cancel:"))
    }
}
