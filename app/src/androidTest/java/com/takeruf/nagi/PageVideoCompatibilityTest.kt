package com.takeruf.nagi

import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.engine.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Exercise YouTube-shaped SPA players in the real WebView without depending on live site data. */
class PageVideoCompatibilityTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var engine: WebViewBrowserEngine
    private lateinit var web: WebView
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val host = object : BrowserHost, FullscreenHost {
        override fun chooseFiles(request: FileSelectionRequest, result: (List<String>?) -> Unit) = result(null)
        override fun requestPermission(origin: String, permissions: Set<SitePermission>, result: (Set<SitePermission>) -> Unit) = result(emptySet())
        override fun download(request: DownloadRequest) {}
        override fun openExternal(url: String) {}
        override fun showMessage(message: String) {}
        override fun showFullscreen(view: View, exit: () -> Unit) = exit()
        override fun hideFullscreen() {}
    }
    private fun js(script: String): String {
        val result = CompletableDeferred<String>()
        instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun waitFor(predicate: () -> Boolean) = runBlocking {
        withTimeout(10_000) { while (!predicate()) delay(100) }
    }
    @Before fun setup() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val media = instrumentation.context.assets.open("video.mp4").use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
        scenario.onActivity { activity ->
            engine = WebViewBrowserEngine(activity, host, host, { false }, true)
            web = engine.surface as WebView
            web.settings.mediaPlaybackRequiresUserGesture = false
            activity.addContentView(web, ViewGroup.LayoutParams(-1, -1))
            web.loadDataWithBaseURL("https://www.youtube.com/@fixture", """
                <html><meta name="viewport" content="width=device-width,initial-scale=1"><body>
                <ytd-channel-video-player-renderer><div id="channel" class="html5-video-player">
                <video id="trailer" style="width:320px;height:180px"></video></div></ytd-channel-video-player-renderer>
                <div id="movie_player" class="html5-video-player" style="position:fixed;right:10px;top:250px;width:320px;height:180px">
                <video id="playing" loop muted src="data:video/mp4;base64,$media" style="width:320px;height:180px"></video>
                <button id="close" style="position:absolute;right:0;top:0;width:40px;height:40px">Close</button></div>
                <script>
                window.fixtureId='first';
                document.querySelector('ytd-channel-video-player-renderer').data={videoId:'first'};
                document.getElementById('channel').getPlayerResponse=()=>({videoDetails:{videoId:fixtureId,
                  thumbnail:{thumbnails:[{url:'https://i.ytimg.com/vi/'+fixtureId+'/hqdefault.jpg',width:480}]}}});
                </script></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        waitFor { js("!!document.getElementById('trailer') && !!window.__nagiVideo") == "true" }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { engine.destroy() }
        scenario.close()
    }
    @Test fun channelPosterFollowsSpaVideoAndPreservesSitePoster() {
        waitFor { js("document.getElementById('trailer').poster") == "\"https://i.ytimg.com/vi/first/hqdefault.jpg\"" }
        assertEquals("true", js("document.getElementById('trailer').paused && document.getElementById('trailer').currentTime === 0"))
        js("fixtureId='second'; true")
        // An old player response must not supply a poster for a new channel video.
        waitFor { js("document.getElementById('trailer').hasAttribute('poster')") == "false" }
        js("document.querySelector('ytd-channel-video-player-renderer').data.videoId='second'; true")
        waitFor { js("document.getElementById('trailer').poster") == "\"https://i.ytimg.com/vi/second/hqdefault.jpg\"" }
        js("document.getElementById('trailer').poster='https://i.ytimg.com/vi/site/hqdefault.jpg'; fixtureId='third'; document.querySelector('ytd-channel-video-player-renderer').data.videoId='third'; true")
        // Wait for an assistant tick, then verify a site's own poster was left alone.
        js("document.dispatchEvent(new Event('resize')); true")
        assertEquals("\"https://i.ytimg.com/vi/site/hqdefault.jpg\"", js("document.getElementById('trailer').poster"))
    }
    @Test fun noAssistantCoversMiniplayerAndInlinePresentationRestoresStyles() {
        js("document.getElementById('playing').play(); true")
        waitFor { js("!document.getElementById('playing').paused") == "true" }
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-assistant]')"))
        js("document.getElementById('movie_player').classList.add('ytp-player-minimized'); true")
        assertEquals("true", js("(()=>{const b=document.getElementById('close'),r=b.getBoundingClientRect();return document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===b})()"))
        val original = js("document.getElementById('playing').getAttribute('style')")
        js("document.getElementById('playing').setAttribute('controls','controls'); true")
        js("window.__nagiVideo.control('present'); true")
        assertEquals("false", js("document.getElementById('playing').controls"))
        assertEquals("true", js("document.getElementById('playing').hasAttribute('data-nagi-video-target')"))
        assertEquals("true", js("(()=>{const r=document.getElementById('playing').getBoundingClientRect();return r.x===0 && r.y===0 && r.width===innerWidth && r.height===innerHeight})()"))
        js("document.getElementById('playing').style.width='100px'; document.getElementById('playing').style.top='30px'; true")
        assertEquals("true", js("(()=>{const r=document.getElementById('playing').getBoundingClientRect();return r.x===0 && r.y===0 && r.width===innerWidth && r.height===innerHeight})()"))
        js("window.__nagiVideo.control('restore'); true")
        assertEquals(original, js("document.getElementById('playing').getAttribute('style')"))
        assertEquals("\"controls\"", js("document.getElementById('playing').getAttribute('controls')"))
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-target]')"))
        assertEquals("false", js("document.getElementById('playing').paused"))
    }
    @Test fun childCannotExpandItselfWithoutAnAppInitiatedPresentation() {
        js("""
            (()=>{const f=document.createElement('iframe');
            addEventListener('message',e=>{if(e.source===f.contentWindow)window.fixtureChildReported=true;});
            f.srcdoc="<script>parent.postMessage({nagiVideoSurface:'present'},'*')<"+"/script>";
            document.body.append(f);return true;})()
        """.trimIndent())
        waitFor { js("!!window.fixtureChildReported") == "true" }
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-surface]')"))
    }
    @Test fun zoomAndRepeatedShrinkingKeepTheWholeVideoInsideTheVisualViewport() {
        js("document.getElementById('playing').play(); true")
        waitFor { js("!document.getElementById('playing').paused") == "true" }
        js("window.__nagiVideo.control('present'); true")
        instrumentation.runOnMainSync { web.zoomBy(1.5f) }
        waitFor { js("visualViewport.scale > 1.1") == "true" }
        for (width in listOf(960, 480, 800, 360, 640)) {
            instrumentation.runOnMainSync {
                web.layoutParams = web.layoutParams.apply { this.width = width; height = width * 9 / 16 }
                web.requestLayout()
            }
            waitFor { web.width == width }
            waitFor { js("""
                (()=>{const r=document.getElementById('playing').getBoundingClientRect(),v=visualViewport;
                return Math.abs(r.left-v.offsetLeft)<1 && Math.abs(r.top-v.offsetTop)<1 &&
                    Math.abs(r.width-v.width)<1 && Math.abs(r.height-v.height)<1 &&
                    getComputedStyle(document.getElementById('playing')).objectFit==='contain';})()
            """.trimIndent()) == "true" }
            assertEquals("false", js("document.getElementById('playing').paused"))
        }
        js("window.__nagiVideo.control('restore'); true")
        assertEquals("false", js("!!document.querySelector('[data-nagi-video-target]')"))
        assertEquals("false", js("document.getElementById('playing').paused"))
    }
}
