package com.takeruf.nagi.ui.browser

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.View
import androidx.core.net.toUri
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.takeruf.nagi.ui.theme.NagiShapes
import org.jsoup.Jsoup

internal fun droppedWebUrl(clip: ClipData?): String? {
    if (clip == null) return null
    for (index in 0 until clip.itemCount.coerceAtMost(16)) {
        val item = clip.getItemAt(index)
        val htmlLink = item.htmlText?.takeIf { it.length <= 8192 }?.let {
            Jsoup.parse(it).selectFirst("a[href]")?.attr("href")
        }
        for (candidate in listOfNotNull(item.uri?.toString(), item.text?.toString(), item.intent?.dataString, htmlLink)) {
            val text = candidate.trim().takeIf { it.length <= 8192 } ?: continue
            val uri = text.toUri()
            if (uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && !text.any { it.isWhitespace() }) return text
        }
    }
    return null
}

/** Native drop receiver interoperates with WebView sources; it doesn't handle ordinary touches. */
@Composable
fun WebUrlDropTarget(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val currentOpen by rememberUpdatedState(onOpen)
    var entered by remember { mutableStateOf(false) }
    Box(modifier) {
        AndroidView(factory = { context -> View(context) }, modifier = Modifier.matchParentSize(),
            onRelease = { it.setOnDragListener(null) }, update = { view ->
                view.setOnDragListener { _, event ->
                    when (event.action) {
                        DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.let { description ->
                            description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                                description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) || description.hasMimeType("text/uri-list")
                        } == true
                        DragEvent.ACTION_DRAG_ENTERED -> { entered = true; true }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { entered = false; true }
                        DragEvent.ACTION_DROP -> {
                            entered = false
                            val url = droppedWebUrl(event.clipData)
                            if (url != null) { currentOpen(url); true } else false
                        }
                        else -> true
                    }
                }
            })
        if (entered) Box(Modifier.matchParentSize().border(2.dp, MaterialTheme.colorScheme.primary, NagiShapes.Rounded))
    }
}
