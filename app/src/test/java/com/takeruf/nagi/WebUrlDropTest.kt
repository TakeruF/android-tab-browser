package com.takeruf.nagi

import android.app.Application
import android.content.ClipData
import android.net.Uri
import com.takeruf.nagi.ui.browser.droppedWebUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WebUrlDropTest {
    @Test fun webLinksCanArriveAsTextUriOrHtml() {
        val url = "https://example.com/page?q=tablet"
        assertEquals(url, droppedWebUrl(ClipData.newPlainText("link", url)))
        assertEquals(url, droppedWebUrl(ClipData.newRawUri("link", Uri.parse(url))))
        assertEquals(url, droppedWebUrl(ClipData.newHtmlText("link", "Example", "<a href='$url'>Example</a>")))
    }
    @Test fun filesExecutableSchemesAndOrdinaryTextDoNotNavigate() {
        listOf("javascript:alert(1)", "content://files/image.png", "file:///private/file", "ordinary text", "https://").forEach {
            assertNull(droppedWebUrl(ClipData.newPlainText("drop", it)))
        }
    }
}
