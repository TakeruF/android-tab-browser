package com.takeruf.nagi

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.testing.AutofillHarnessService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Opt-in anonymous public-page probe. No real vault data, signup, or final login submission. */
class LivePasswordAutofillTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun currentWebView(): WebView? {
        var result: WebView? = null
        scenario?.onActivity { activity ->
            fun visit(view: View) {
                if (view is WebView && view.id == R.id.browser_web_view && view.isShown && view.width > 100 && view.height > 100) result = view
                if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
            }
            visit(activity.window.decorView)
        }
        return result
    }

    private fun js(script: String): JSONObject {
        val view = currentWebView() ?: return JSONObject()
        val queue = LinkedBlockingQueue<String>()
        main { view.evaluateJavascript(script) { queue.offer(it) } }
        val raw = queue.poll(8, TimeUnit.SECONDS) ?: return JSONObject()
        if (raw == "null") return JSONObject()
        return JSONObject(raw)
    }

    // Only booleans, input categories, public origin and geometry. Never export values or URL queries.
    private fun snapshot(): JSONObject = js("""
        (()=>{const visible=e=>e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';
        const inputs=[...document.querySelectorAll('input')].filter(e=>visible(e)&&['text','email','tel','password'].includes(e.type));
        return {origin:location.origin,secure:isSecureContext,ready:document.readyState,
          inputs:inputs.map((e,index)=>({index,type:e.type,focused:document.activeElement===e,
            hints:(e.autocomplete||'').split(/\s+/).filter(h=>['username','email','current-password','new-password','off','on'].includes(h)),
            width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height,
            hasValue:e.value.length>0,
            syntheticUsername:e.value===${JSONObject.quote(AutofillHarnessService.LIVE_USERNAME)},
            syntheticPassword:e.value===${JSONObject.quote(AutofillHarnessService.LIVE_PASSWORD)}})),
          loginNavigation:inputs.length===0&&[...document.querySelectorAll('a,button')].some(e=>visible(e)&&e.textContent.trim()==='ログイン')};})()
    """.trimIndent())

    private fun tap(script: String): Boolean {
        val rect = js("""
            (()=>{const visible=e=>e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';
            const e=($script);if(!e)return {};e.scrollIntoView({block:'center'});
            const r=e.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,w:innerWidth};})()
        """.trimIndent())
        if (!rect.has("x")) return false
        var x = 0; var y = 0
        main {
            val view = checkNotNull(currentWebView())
            val location = IntArray(2); view.getLocationOnScreen(location)
            val scale = view.width / rect.getDouble("w")
            x = location[0] + (rect.getDouble("x") * scale).toInt()
            y = location[1] + (rect.getDouble("y") * scale).toInt()
        }
        shell("input tap $x $y")
        instrumentation.waitForIdleSync()
        return true
    }

    private suspend fun waitForInputs(timeoutMs: Long = 45_000): JSONObject {
        val until = System.currentTimeMillis() + timeoutMs
        var last = JSONObject()
        var navigationTapped = false
        while (System.currentTimeMillis() < until) {
            compose.waitForIdle()
            last = snapshot()
            if (last.optJSONArray("inputs")?.length()?.let { it > 0 } == true) return last
            if (last.optBoolean("loginNavigation") && !navigationTapped) {
                navigationTapped = tap("[...document.querySelectorAll('a,button')].find(e=>visible(e)&&e.textContent.trim()==='ログイン')")
            }
            delay(400)
        }
        return last
    }

    private fun screenshot(output: File, name: String) {
        val bounds = Rect()
        main { currentWebView()?.getGlobalVisibleRect(bounds) }
        if (bounds.isEmpty) return
        val screen = automation.takeScreenshot() ?: return
        if (!bounds.intersect(0, 0, screen.width, screen.height)) { screen.recycle(); return }
        // Crop away app chrome: OAuth query parameters must not enter screenshot artifacts.
        val page = android.graphics.Bitmap.createBitmap(screen, bounds.left, bounds.top, bounds.width(), bounds.height())
        File(output, name).outputStream().use { page.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        page.recycle(); screen.recycle()
    }

    private suspend fun chooseSyntheticDataset(output: File, name: String): Boolean {
        val until = System.currentTimeMillis() + 12_000
        var previous: Rect? = null
        while (System.currentTimeMillis() < until) {
            val node = automation.windows.asSequence().mapNotNull { it.root }
                .flatMap { it.findAccessibilityNodeInfosByText(AutofillHarnessService.LIVE_LABEL).asSequence() }
                .firstOrNull { it.isVisibleToUser }
            if (node != null) {
                val rect = Rect(); node.getBoundsInScreen(rect)
                if (!rect.isEmpty && rect == previous) {
                    screenshot(output, name)
                    shell("input tap ${rect.centerX()} ${rect.centerY()}")
                    delay(400)
                    return true
                }
                previous = rect
            }
            delay(100)
        }
        return false
    }

    private fun requestFromPageMenu() {
        compose.onNodeWithContentDescription("Page menu").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
        compose.onNodeWithTag("request-password-autofill").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun activateIdentifierContinue(): JSONObject = js("""
        (()=>{const e=[...document.querySelectorAll('button,input[type=submit]')].find(e=>
          e.getClientRects().length&&['続ける','Continue'].includes((e.textContent||e.value||'').trim()));
          if(!e)return {activated:false};const disabled=!!e.disabled;if(!disabled)e.click();
          return {activated:!disabled,disabled};})()
    """.trimIndent())

    @Test fun publicLoginForm(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit live probe only", arguments.getString("liveAutofill") == "true")
        assumeTrue("Separate diagnostic package only", BuildConfig.PASSWORD_AUTOFILL_DIAGNOSTICS &&
            instrumentation.targetContext.packageName == "com.takeruf.nagi.autofillqa")
        val site = arguments.getString("site") ?: "asahi"
        val mode = arguments.getString("mode") ?: "observe"
        require(site in setOf("asahi", "github"))
        require(mode in setOf("observe", "automatic", "manual", "google"))
        val entry = if (site == "asahi") "https://id.asahi.com/" else "https://github.com/login"
        val domains = if (site == "asahi") setOf("id.asahi.com", "auth.asahi.com", "account.asahi.com",
            "accounts.asahi.com", "login.asahi.com", "sso.asahi.com", "digital.asahi.com", "www.asahi.com") else setOf("github.com")
        val context = instrumentation.targetContext
        val output = File(context.filesDir, "autofill-live/$site-$mode").apply { mkdirs() }
        val earlierResult = File(context.getExternalFilesDir(null), "autofill-live/$site-$mode/result.json")
        if (earlierResult.isFile) earlierResult.copyTo(File(output, "earlier-result.json"), overwrite = true)
        val result = JSONObject().put("site", site).put("mode", mode).put("finalLoginSubmitted", false)
        val previous = Settings.Secure.getString(context.contentResolver, "autofill_service")
        if (mode == "google") assertTrue("Google control requires the original Google service", previous?.startsWith("com.google.android.gms/") == true)
        val previousInfo = automation.serviceInfo
        var stage = "setup"
        try {
            android.util.Log.i("NagiLiveAutofill", "phase=start site=$site mode=$mode")
            AutofillHarnessService.liveObservations.clear()
            AutofillHarnessService.totalRequests = 0
            AutofillHarnessService.manualRequests = 0
            AutofillHarnessService.liveProbe = if (mode == "google") null else
                AutofillHarnessService.LiveProbe(domains, mode in setOf("automatic", "manual"), mode == "manual")
            if (mode != "google") shell("settings put secure autofill_service ${context.packageName}/com.takeruf.nagi.testing.AutofillHarnessService")
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            delay(500)
            scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW).setData(Uri.parse(entry)))
            stage = "open public login form"
            val before = waitForInputs()
            result.put("before", before)
            if (mode != "google") screenshot(output, "page-before.png")
            assertTrue("Public HTTPS login inputs must be visible", before.optBoolean("secure") && before.optJSONArray("inputs")?.length()?.let { it > 0 } == true)
            val origin = Uri.parse(before.getString("origin"))
            assertTrue("The login origin must belong to the requested site", origin.scheme == "https" &&
                (if (site == "asahi") origin.host == "asahi.com" || origin.host.orEmpty().endsWith(".asahi.com") else origin.host == "github.com"))
            if (mode != "google") AutofillHarnessService.liveProbe = AutofillHarnessService.LiveProbe(
                domains + origin.host.orEmpty(), mode in setOf("automatic", "manual"), mode == "manual")
            stage = "focus login field"
            assertTrue(tap("[...document.querySelectorAll('input')].find(e=>visible(e)&&['email','text','tel'].includes(e.type))||[...document.querySelectorAll('input[type=password]')].find(visible)"))
            delay(if (mode == "google") 6_000 else 1_500)
            if (mode in setOf("observe", "manual", "google")) {
                stage = "manual request"
                requestFromPageMenu()
                delay(if (mode == "google") 6_000 else 1_500)
            }
            if (mode in setOf("automatic", "manual")) {
                stage = "choose system suggestion"
                assertTrue("Synthetic system dataset must be visible", chooseSyntheticDataset(output, "username-suggestion.png"))
                val after = snapshot()
                result.put("afterFirstFill", after)
                assertTrue("Native autofill must reach every visible login field", (0 until after.getJSONArray("inputs").length()).all {
                    val field = after.getJSONArray("inputs").getJSONObject(it)
                    field.getBoolean(if (field.getString("type") == "password") "syntheticPassword" else "syntheticUsername")
                })
                // Asahi's first Continue submits only the reserved, nonexistent identifier.
                // Never tap the final Login button or submit the password.
                if (site == "asahi" && arguments.getString("advanceAsahi") == "true" &&
                    (0 until after.getJSONArray("inputs").length()).none { after.getJSONArray("inputs").getJSONObject(it).getString("type") == "password" }) {
                    stage = "advance synthetic identifier"
                    // Only the non-authenticating identifier navigation uses DOM activation;
                    // dataset selection and field filling remain real native Autofill operations.
                    val activation = activateIdentifierContinue()
                    result.put("identifierContinue", activation)
                    assertTrue("Identifier Continue must be activated", activation.optBoolean("activated"))
                    val until = System.currentTimeMillis() + 15_000
                    var next = snapshot()
                    fun hasPassword(value: JSONObject): Boolean = (0 until (value.optJSONArray("inputs")?.length() ?: 0)).any {
                        value.getJSONArray("inputs").getJSONObject(it).getString("type") == "password"
                    }
                    while (!hasPassword(next) && System.currentTimeMillis() < until) { delay(400); next = snapshot() }
                    result.put("passwordStep", next)
                    result.put("passwordStageReached", hasPassword(next))
                    assertTrue("Asahi identifier navigation must reach the password step", hasPassword(next))
                    if (hasPassword(next)) {
                        stage = "fill password without submitting login"
                        tap("[...document.querySelectorAll('input[type=password]')].find(visible)")
                        if (mode == "manual") requestFromPageMenu()
                        val selected = chooseSyntheticDataset(output, "password-suggestion.png")
                        result.put("passwordSuggestionSelected", selected)
                        assertTrue("Password-step system suggestion must be selectable", selected)
                        val filled = snapshot()
                        result.put("afterPassword", filled)
                        assertTrue("Native autofill must reach the password field", (0 until filled.getJSONArray("inputs").length()).any {
                            filled.getJSONArray("inputs").getJSONObject(it).getBoolean("syntheticPassword")
                        })
                    }
                }
            }
            result.put("after", snapshot())
            if (mode != "google") screenshot(output, "page-after.png")
            if (mode != "google") {
                assertTrue("The selected service must receive the site's native structure",
                    AutofillHarnessService.liveObservations.any { it.domain == origin.host && it.fields.isNotEmpty() })
                if (mode in setOf("observe", "manual")) assertTrue("Manual request must reach the service",
                    AutofillHarnessService.liveObservations.any { it.manual && it.domain == origin.host })
            }
            result.put("passed", true)
        } catch (error: Throwable) {
            result.put("passed", false).put("failureStage", stage).put("errorClass", error.javaClass.simpleName)
            // Do not serialize PageState, exception messages, complete URLs, values, or HTML.
            throw AssertionError("Live autofill probe failed for $site / $mode at $stage")
        } finally {
            result.put("observations", JSONArray(AutofillHarnessService.liveObservations.map { observation ->
                JSONObject().put("manual", observation.manual).put("domain", observation.domain)
                    .put("response", observation.response).put("fields", JSONArray(observation.fields.map { field ->
                        JSONObject().put("kind", field.kind).put("hints", JSONArray(field.hints))
                            .put("focused", field.focused).put("width", field.width).put("height", field.height)
                            .put("hasAutofillId", field.hasAutofillId)
                    }))
            }))
            File(output, "result.json").writeText(result.toString(2))
            android.util.Log.i("NagiLiveAutofill", "phase=finish site=$site mode=$mode passed=${result.optBoolean("passed")}")
            AutofillHarnessService.liveProbe = null
            scenario?.close()
            automation.serviceInfo = previousInfo
            if (mode != "google") {
                if (previous == null) shell("settings delete secure autofill_service")
                else shell("settings put secure autofill_service $previous")
            }
        }
    }
}
