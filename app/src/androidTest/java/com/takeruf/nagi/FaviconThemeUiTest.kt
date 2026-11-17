package com.takeruf.nagi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.takeruf.nagi.browser.favicon.FaviconStore
import com.takeruf.nagi.browser.favicon.faviconOrigin
import com.takeruf.nagi.data.room.entity
import com.takeruf.nagi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** Actual WebView callbacks, persisted indexes and rendered app pixels. No public network. */
@OptIn(ExperimentalTestApi::class)
class FaviconThemeUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var container: AppContainer
    private lateinit var original: BrowserSettings
    private lateinit var originalSize: String
    private lateinit var originalNight: String
    private lateinit var server: FixtureServer
    private lateinit var spaceId: String
    private lateinit var tabId: String
    private val favoriteId = UUID.randomUUID().toString()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    private fun resize(width: Int) {
        val density = compose.activity.resources.displayMetrics.density
        shell("wm size ${(width * density).toInt()}x${(820 * density).toInt()}")
        compose.waitUntil(10_000) { kotlin.math.abs(compose.activity.resources.configuration.screenWidthDp - width) < 15 }
        compose.waitForIdle()
    }
    private fun views(): List<WebView> {
        fun find(view: View): List<WebView> = when (view) {
            is WebView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { find(view.getChildAt(it)) }
            else -> emptyList()
        }
        var result = emptyList<WebView>()
        compose.runOnIdle { result = find(compose.activity.window.decorView).filter { it.isShown } }
        return result
    }
    private fun js(view: WebView, script: String): String {
        val result = CompletableDeferred<String>()
        compose.runOnIdle { view.evaluateJavascript(script) { result.complete(it) } }
        return runBlocking { withTimeout(5_000) { result.await() } }
    }
    private fun theme(mode: ThemeMode) {
        runBlocking { container.settings.update { it.copy(theme = mode) } }
        compose.waitForIdle()
        val expectedDark = mode == ThemeMode.DARK || mode == ThemeMode.SYSTEM &&
            compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        compose.waitUntil(10_000) { views().all { js(it, "matchMedia('(prefers-color-scheme:dark)').matches") == expectedDark.toString() } }
    }
    private val store get() = FaviconStore.get(compose.activity)
    private fun awaitVariant(dark: Boolean, color: Int) {
        compose.waitUntil(15_000) {
            val path = store.icons.value[faviconOrigin(server.origin)]?.exact(dark)
            path != null && BitmapFactory.decodeFile(path)?.let {
                try { it.getPixel(it.width / 2, it.height / 2) == color } finally { it.recycle() }
            } == true
        }
    }
    private fun hasColor(description: String, color: Int): Boolean {
        if (compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty()) return false
        val image = compose.onAllNodesWithContentDescription(description).onFirst().captureToImage().asAndroidBitmap()
        // The fixture icon is a solid square; a center patch excludes label text and badges.
        return (0 until image.height).any { y -> (0 until image.width - 5).any { x ->
            (0..5).all { offset -> image.getPixel(x + offset, y) == color }
        } }
    }
    private fun awaitFavorite(color: Int) = compose.waitUntil(10_000) { hasColor("Favorite Theme icon", color) }
    private fun capture(name: String) {
        compose.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "favicon-theme-$name.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        image.recycle()
    }

    @Before fun setup() {
        originalSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) ?: "reset"
        originalNight = shell("cmd uimode night").substringAfter(":").trim()
        val document = """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="light dark"><title>Theme fixture</title>
            <link id="icon" rel="icon">
            <script>document.getElementById('icon').href=matchMedia('(prefers-color-scheme:dark)').matches?'/white.png':'/black.png';
            window.documentToken=String(Math.random());</script>
            <style>body{min-height:2000px}</style><input id="draft"><p>Theme favicon fixture</p>
        """.trimIndent()
        server = FixtureServer(pages = mapOf("/scheme" to document,
            "/media" to """<!doctype html><meta name="color-scheme" content="light dark"><title>Media fixture</title>
                <link rel="icon" href="/black.png" media="(prefers-color-scheme:light)">
                <link rel="icon" href="/white.png" media="(prefers-color-scheme:dark)"><input id="draft">""",
            "/dynamic" to """<!doctype html><title>Dynamic fixture</title><link id="icon" rel="icon" href="/fixed.png">
                <script>window.changeIcon=()=>document.getElementById('icon').href='/white.png';</script>""",
            "/fixed" to """<!doctype html><title>Fixed fixture</title><link rel="icon" href="/fixed.png"><p>Fixed</p>"""),
            redirects = mapOf("/redirect" to "/scheme"),
            icons = mapOf("/white.png" to Color.WHITE, "/black.png" to Color.BLACK, "/fixed.png" to Color.GREEN))
        container = (compose.activity.application as NagiApplication).container
        runBlocking {
            container.workspace.ready.await()
            original = container.settings.settings.first()
            container.settings.update { BrowserSettings(theme = ThemeMode.DARK, automaticSearchRegion = false) }
            container.spaces.create("Theme favicon QA")
            spaceId = container.settings.settings.first().selectedSpaceId
            tabId = container.tabs.create(spaceId, "${server.origin}/redirect")
        }
        resize(1000)
        awaitVariant(true, Color.WHITE)
        runBlocking { container.workspace.dao.putBookmark(Bookmark(favoriteId, "${server.origin}/scheme", "Theme icon", isFavorite = true, createdAt = -10).entity()) }
        awaitFavorite(Color.WHITE)
    }
    @After fun cleanup() {
        runBlocking {
            if (::container.isInitialized) {
                container.library.removeBookmark(favoriteId)
                if (::spaceId.isInitialized) container.spaces.delete(spaceId)
                if (::original.isInitialized) container.settings.update { original }
            }
        }
        if (::originalNight.isInitialized) shell("cmd uimode night $originalNight")
        if (::originalSize.isInitialized) shell("wm size $originalSize")
        if (::server.isInitialized) server.close()
    }

    @Test fun bothVariantsSwitchWithoutRevisitingAcrossSystemThemeSplitAndNarrowWindows() {
        val page = views().single()
        val token = js(page, "document.getElementById('draft').value='unfinished 日本語';window.scrollTo(0,250);documentToken")
        theme(ThemeMode.LIGHT)
        val light = store.icons.value[faviconOrigin(server.origin)]?.light
        val color = light?.let { BitmapFactory.decodeFile(it)?.getPixel(0, 0) }
        assertNull("Unexpected light variant $light color=$color; requests=${server.requests.map { it.path }}", light)
        assertEquals(token, js(page, "documentToken"))
        assertEquals(1, server.requests.count { it.path == "/scheme" })
        compose.runOnIdle { page.reload() }
        awaitVariant(false, Color.BLACK)
        awaitFavorite(Color.BLACK)
        val currentToken = js(page, "document.getElementById('draft').value='keep 日本語';documentToken")
        val count = server.requests.size
        theme(ThemeMode.DARK); awaitFavorite(Color.WHITE)
        assertSame(page, views().single())
        assertEquals(currentToken, js(page, "documentToken"))
        assertEquals("\"keep 日本語\"", js(page, "document.getElementById('draft').value"))
        theme(ThemeMode.LIGHT); awaitFavorite(Color.BLACK)
        assertEquals("Theme switches reloaded the document: ${server.requests.drop(count)}", 2, server.requests.count { it.path == "/scheme" })
        assertTrue("Unexpected recovery request: ${server.requests.drop(count)}",
            server.requests.drop(count).all { it.path == "/black.png" })
        capture("wide-light")
        val fixedOrigin = server.origin.replace("127.0.0.1", "localhost")
        val right = runBlocking {
            container.tabs.create(spaceId, "$fixedOrigin/fixed", select = false).also { container.tabs.pair(tabId, it) }
        }
        compose.waitUntil(15_000) { views().size == 2 && views().all { js(it, "document.readyState") == "\"complete\"" } }
        compose.waitUntil(15_000) { store.icons.value[faviconOrigin(fixedOrigin)]?.light != null }
        theme(ThemeMode.DARK); awaitFavorite(Color.WHITE)
        val fixedPage = views().first { js(it, "document.title") == "\"Fixed fixture\"" }
        compose.runOnIdle { fixedPage.reload() }
        compose.waitUntil(15_000) { store.icons.value[faviconOrigin(fixedOrigin)]?.dark != null }
        val fixed = store.icons.value[faviconOrigin(fixedOrigin)]!!
        assertEquals("Fixed variants should share one PNG", fixed.light, fixed.dark)
        assertEquals(right, runBlocking { container.workspace.dao.tab(tabId)!!.splitRightTabId })
        assertSame(page, views().first())
        capture("split-dark")
        resize(411)
        compose.onNodeWithTag("compact-tabs-button").performClick()
        compose.onNodeWithText("Favorites").performClick()
        awaitFavorite(Color.WHITE)
        capture("phone-dark")
        theme(ThemeMode.LIGHT); awaitFavorite(Color.BLACK)
        capture("phone-light")
        theme(ThemeMode.SYSTEM)
        shell("cmd uimode night yes")
        compose.waitUntil(10_000) { hasColor("Favorite Theme icon", Color.WHITE) }
        shell("cmd uimode night no")
        compose.waitUntil(10_000) { hasColor("Favorite Theme icon", Color.BLACK) }
        val reopened = FaviconStore(compose.activity)
        runBlocking { reopened.resolve("${server.origin}/offline") }
        assertEquals(store.icons.value[faviconOrigin(server.origin)], reopened.icons.value[faviconOrigin(server.origin)])
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("compact-tabs-button").performClick()
        if (compose.onAllNodesWithContentDescription("Favorite Theme icon").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithText("Favorites").performClick()
        }
        awaitFavorite(Color.BLACK)
    }

    @Test fun matchingMediaAndJavascriptUpdatesAreObservedWithoutFallbackOverwrites() {
        val page = views().single()
        compose.runOnIdle { page.loadUrl("${server.origin}/media") }
        compose.waitUntil(15_000) { js(page, "document.title") == "\"Media fixture\"" }
        awaitVariant(true, Color.WHITE)
        theme(ThemeMode.LIGHT)
        compose.runOnIdle { page.reload() }
        awaitVariant(false, Color.BLACK)
        compose.runOnIdle { page.loadUrl("${server.origin}/dynamic") }
        compose.waitUntil(15_000) { js(page, "document.title") == "\"Dynamic fixture\"" }
        awaitVariant(false, Color.GREEN)
        js(page, "changeIcon();true")
        awaitVariant(false, Color.WHITE)
        // Page-finished supplementary fetching must not replace the subsequent native change.
        compose.runOnIdle { page.reload() }
        awaitVariant(false, Color.GREEN)
        js(page, "changeIcon();true")
        awaitVariant(false, Color.WHITE)
        assertEquals("\"/white.png\"", js(page, "new URL(document.getElementById('icon').href).pathname"))
    }
}
