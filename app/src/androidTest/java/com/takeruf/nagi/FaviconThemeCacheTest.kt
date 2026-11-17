package com.takeruf.nagi

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.takeruf.nagi.browser.favicon.*
import com.takeruf.nagi.domain.model.ThemeMode
import com.takeruf.nagi.ui.components.Favicon
import com.takeruf.nagi.ui.theme.NagiTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FaviconThemeCacheTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun bitmap(color: Int) = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    private fun pixelsContain(color: Int): Boolean {
        val image = compose.onNodeWithTag("icon").captureToImage().asAndroidBitmap()
        return (0 until image.height).any { y -> (0 until image.width).any { x -> image.getPixel(x, y) == color } }
    }

    @Test fun unvisitedIconsSwitchUsingOnlyCacheAndMissingThemeUsesPlaceholder() {
        FixtureServer().use { server ->
            val store = FaviconStore.get(context)
            val white = bitmap(android.graphics.Color.WHITE)
            val black = bitmap(android.graphics.Color.BLACK)
            var theme by mutableStateOf(ThemeMode.LIGHT)
            try {
                val oldPath = runBlocking { store.save(server.origin, white, true) }
                compose.setContent { NagiTheme(theme) {
                    Favicon(oldPath, Modifier.testTag("icon").background(androidx.compose.ui.graphics.Color.Gray), server.origin)
                } }
                compose.waitForIdle()
                assertFalse("Opposite-only icon must not be rendered", pixelsContain(android.graphics.Color.WHITE))
                runBlocking { store.save(server.origin, black, false) }
                compose.waitUntil(5000) { pixelsContain(android.graphics.Color.BLACK) }
                compose.runOnIdle { theme = ThemeMode.DARK }
                compose.waitUntil(5000) { pixelsContain(android.graphics.Color.WHITE) }
                compose.runOnIdle { theme = ThemeMode.LIGHT }
                compose.waitUntil(5000) { pixelsContain(android.graphics.Color.BLACK) }
                assertTrue("Cache selection requested a website", server.requests.isEmpty())
                val before = store.icons.value[faviconOrigin(server.origin)]!!
                runBlocking { FaviconStore(context).resolve(server.origin) }
                assertEquals(before, store.icons.value[faviconOrigin(server.origin)])
                assertTrue(server.requests.isEmpty())
                assertEquals(64, BitmapFactory.decodeFile(before.light).width)
            } finally { white.recycle(); black.recycle() }
        }
    }

    @Test fun nativeObservationWinsAnInflightFetchAndClearInvalidatesPendingWrites() = runBlocking {
        val directory = File.createTempFile("favicon-cache-test", "", context.cacheDir).apply { delete(); mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        val store = FaviconStore(isolated)
        val white = bitmap(android.graphics.Color.WHITE)
        try {
            FixtureServer(icons = mapOf("/slow.png" to android.graphics.Color.GREEN), iconDelaysMs = mapOf("/slow.png" to 750L)).use { server ->
                suspend fun startFetch(): Deferred<String?> {
                    val count = server.requests.count { it.path == "/slow.png" }
                    val pending = async(Dispatchers.Default) { store.resolve(server.origin, declared = listOf("${server.origin}/slow.png"), dark = true) }
                    withTimeout(5000) { while (server.requests.count { it.path == "/slow.png" } == count) delay(20) }
                    return pending
                }
                val pending = startFetch()
                val native = store.save(server.origin, white, true)
                assertEquals(native, pending.await())
                assertEquals(native, store.icons.value[faviconOrigin(server.origin)]?.dark)
                val next = startFetch()
                store.clear()
                assertNull(next.await())
                assertTrue(store.icons.value.isEmpty())
                assertTrue(File(directory, "favicons").listFiles().orEmpty().isEmpty())
            }
        } finally { white.recycle(); directory.deleteRecursively() }
    }
}
