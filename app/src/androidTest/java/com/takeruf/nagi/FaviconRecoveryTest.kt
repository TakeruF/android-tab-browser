package com.takeruf.nagi

import android.content.Context
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.takeruf.nagi.browser.favicon.FaviconStore
import com.takeruf.nagi.ui.components.Favicon
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FaviconRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unopenedFavoriteDiscoversSvgAfterBrokenCandidateAndSharesItAcrossPaths() {
        FixtureServer(false, "<link rel='icon' href='/not-an-image'><link rel='icon' href='/custom-icon.svg'>" + " ".repeat(300_000)).use { server ->
            compose.setContent { MaterialTheme { Favicon("${server.origin}/broken.png", Modifier.testTag("favicon"), "${server.origin}/private?secret", "S") } }
            compose.waitUntil(20_000) {
                val bitmap = compose.onNodeWithTag("favicon").captureToImage().asAndroidBitmap()
                (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == Color.CYAN } }
            }
            val store = FaviconStore.get(context)
            val first = runBlocking { store.resolve("${server.origin}/another-path") }!!
            assertTrue(File(first).isFile)
            server.close()
            assertEquals(first, runBlocking { store.resolve("${server.origin}/offline") })
            assertEquals(first, runBlocking { FaviconStore(context).resolve("${server.origin}/after-restart") })
            assertTrue(File(context.filesDir, "favicons").listFiles()!!.any { it.extension == "txt" && it.readText() == first })
        }
    }

    @Test fun missingIcoUsesAppleTouchIconAndCorruptSavedFileDoesNotBlockRecovery() {
        FixtureServer(false).use { server ->
            val corrupt = File.createTempFile("corrupt-icon", ".png", context.cacheDir).apply { writeText("not an image") }
            try {
                val path = runBlocking { FaviconStore.get(context).resolve("${server.origin}/page", corrupt.absolutePath) }!!
                val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                assertEquals(Color.CYAN, bitmap.getPixel(32, 32))
            } finally { corrupt.delete() }
        }
    }

    @Test fun icoContainerDecodesWithoutFallingBackToTouchIcon() {
        FixtureServer(useIcoContainer = true).use { server ->
            val path = runBlocking { FaviconStore.get(context).resolve(server.origin) }!!
            assertEquals(Color.MAGENTA, android.graphics.BitmapFactory.decodeFile(path).getPixel(32, 32))
        }
    }
}
