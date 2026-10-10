package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.VideoPresentation
import com.takeruf.nagi.browser.engine.VideoPresentationController
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import kotlin.math.roundToInt

@Composable
fun VideoOverlay(video: VideoPresentation, controller: VideoPresentationController, systemPip: Boolean,
    onReturnToTab: (String) -> Unit) {
    val strings = rememberNagiStrings()
    var requestedWidth by remember(video.view) { mutableFloatStateOf(320f) }
    var x by remember(video.view) { mutableFloatStateOf(Float.NaN) }
    var y by remember(video.view) { mutableFloatStateOf(Float.NaN) }
    if (systemPip || !video.popup) {
        Box(Modifier.fillMaxSize().background(Color.Black).testTag("video-fullscreen")) {
            NativeSurface(video.view, Modifier.fillMaxSize().onVideoPositioned(controller))
            if (!systemPip) Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)) {
                if (controller.popupEnabled && video.playback.hasVideo) TextButton(onClick = { controller.minimize() }, modifier = Modifier.testTag("video-minimize")) {
                    Text(strings(R.string.ui_video_popout), color = Color.White)
                }
                TextButton(onClick = { controller.close() }) { Text(strings(R.string.ui_exit_fullscreen), color = Color.White) }
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val density = LocalDensity.current
        val width = requestedWidth.dp.coerceAtMost((maxWidth - 16.dp).coerceAtLeast(1.dp))
        val videoHeight = (width / video.playback.aspectRatio).coerceAtMost((maxHeight - 104.dp).coerceAtLeast(1.dp))
        val height = videoHeight + 96.dp
        val maxX = with(density) { (maxWidth - width).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (maxHeight - height).coerceAtLeast(0.dp).toPx() }
        val positionX = x.takeIf { it.isFinite() } ?: maxX
        val positionY = y.takeIf { it.isFinite() } ?: maxY
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp,
            modifier = Modifier.offset { IntOffset(positionX.coerceIn(0f, maxX).roundToInt(), positionY.coerceIn(0f, maxY).roundToInt()) }
                .width(width).testTag("video-popup")) {
            Column {
                Row(Modifier.fillMaxWidth().height(48.dp).testTag("video-popup-drag")
                    .pointerInput(video.view, maxX, maxY) {
                        detectDragGestures { change, delta ->
                            change.consume(); x = ((x.takeIf { it.isFinite() } ?: maxX) + delta.x).coerceIn(0f, maxX); y = ((y.takeIf { it.isFinite() } ?: maxY) + delta.y).coerceIn(0f, maxY)
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings(R.string.ui_video), Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { requestedWidth = if (requestedWidth < 400f) 440f else 240f }, modifier = Modifier.testTag("video-popup-resize")) {
                        Text(strings(R.string.ui_video_resize))
                    }
                    IconButton(onClick = { controller.close(pause = true) }, modifier = Modifier.testTag("video-popup-close")) {
                        Icon(com.takeruf.nagi.ui.components.NagiIcons.X, strings(R.string.ui_video_close))
                    }
                }
                NativeSurface(video.view, Modifier.fillMaxWidth().height(videoHeight).background(Color.Black)
                    .onVideoPositioned(controller))
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { video.control(if (video.playback.playing) "pause" else "play") }, modifier = Modifier.testTag("video-popup-play-pause")) {
                        Text(strings(if (video.playback.playing) R.string.ui_video_pause else R.string.ui_video_play))
                    }
                    TextButton(onClick = controller::expand, modifier = Modifier.testTag("video-popup-expand")) { Text(strings(R.string.ui_video_fullscreen)) }
                    video.tabId?.let { id -> TextButton(onClick = { controller.close(); onReturnToTab(id) }, modifier = Modifier.testTag("video-popup-return")) {
                        Text(strings(R.string.ui_video_return))
                    } }
                }
            }
        }
    }
}

private fun Modifier.onVideoPositioned(controller: VideoPresentationController) =
    onGloballyPositioned { controller.refreshPip() }
