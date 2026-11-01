package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.VideoPresentation
import com.takeruf.nagi.browser.engine.VideoPresentationController
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import kotlinx.coroutines.delay
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
                VideoIconButton(NagiIcons.Minimize, strings(R.string.ui_exit_fullscreen),
                    onClick = { controller.close() }, tint = Color.White)
            }
        }
    } else BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        val accessibility = LocalAccessibilityManager.current
        var controlsVisible by remember(video.view) { mutableStateOf(false) }
        var interaction by remember(video.view) { mutableIntStateOf(0) }
        LaunchedEffect(controlsVisible, interaction) {
            if (controlsVisible) {
                delay(accessibility?.calculateRecommendedTimeoutMillis(3_000L,
                    containsIcons = true, containsText = true, containsControls = true) ?: 3_000L)
                controlsVisible = false
            }
        }
        val maxVideoWidth = minOf((viewportWidth - 16.dp), (viewportHeight - 16.dp) * video.playback.aspectRatio).coerceAtLeast(1.dp)
        val width = requestedWidth.dp.coerceIn(minOf(240.dp, maxVideoWidth), maxVideoWidth)
        val height = width / video.playback.aspectRatio
        val maxX = with(density) { (viewportWidth - width).coerceAtLeast(0.dp).toPx() }
        val maxY = with(density) { (viewportHeight - height).coerceAtLeast(0.dp).toPx() }
        val positionX = x.takeIf { it.isFinite() } ?: maxX
        val positionY = y.takeIf { it.isFinite() } ?: maxY
        Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp, color = Color.Black,
            modifier = Modifier.offset { IntOffset(positionX.coerceIn(0f, maxX).roundToInt(), positionY.coerceIn(0f, maxY).roundToInt()) }
                .size(width, height).testTag("video-popup")) {
            Box(Modifier.videoCornerResize(
                bounds = with(density) { Rect(Offset(positionX.coerceIn(0f, maxX), positionY.coerceIn(0f, maxY)),
                    Size(width.toPx(), height.toPx())) },
                viewport = with(density) { Size(viewportWidth.toPx(), viewportHeight.toPx()) },
                aspectRatio = video.playback.aspectRatio,
                onStart = { controlsVisible = false },
                onResize = { bounds ->
                    x = bounds.left; y = bounds.top
                    requestedWidth = with(density) { bounds.width.toDp().value }
                })) {
                NativeSurface(video.view, Modifier.fillMaxSize().onVideoPositioned(controller))
                // Intercept taps before the page so revealing controls never pauses the site player.
                // One finger moves the window; a pinch resizes it without permanent chrome.
                Box(Modifier.fillMaxSize().testTag("video-popup-drag")
                    .pointerInput(video.view, maxVideoWidth, viewportWidth, viewportHeight, density) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            controlsVisible = false
                            val oldWidth = requestedWidth.dp.coerceIn(minOf(240.dp, maxVideoWidth), maxVideoWidth)
                            val newWidth = (oldWidth * zoom).coerceIn(minOf(240.dp, maxVideoWidth), minOf(640.dp, maxVideoWidth))
                            with(density) {
                                val oldMaxX = (viewportWidth - oldWidth).coerceAtLeast(0.dp).toPx()
                                val oldMaxY = (viewportHeight - oldWidth / video.playback.aspectRatio).coerceAtLeast(0.dp).toPx()
                                val deltaWidth = (newWidth - oldWidth).toPx()
                                x = ((x.takeIf { it.isFinite() } ?: oldMaxX) + pan.x - deltaWidth / 2)
                                    .coerceIn(0f, (viewportWidth - newWidth).coerceAtLeast(0.dp).toPx())
                                y = ((y.takeIf { it.isFinite() } ?: oldMaxY) + pan.y - deltaWidth / video.playback.aspectRatio / 2)
                                    .coerceIn(0f, (viewportHeight - newWidth / video.playback.aspectRatio).coerceAtLeast(0.dp).toPx())
                            }
                            requestedWidth = newWidth.value
                        }
                    }) {
                    Box(Modifier.matchParentSize().testTag("video-popup-toggle-controls")
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClickLabel = strings(if (controlsVisible) R.string.ui_video_hide_controls else R.string.ui_video_show_controls)) {
                            controlsVisible = !controlsVisible
                            interaction++
                        })
                    if (controlsVisible) {
                        Box(Modifier.fillMaxSize().testTag("video-popup-controls").background(Brush.verticalGradient(listOf(
                            Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.6f))))) {
                            video.tabId?.let { id ->
                                VideoIconButton(NagiIcons.CornerUpLeft, strings(R.string.ui_video_return),
                                    onClick = { controller.close(); onReturnToTab(id) },
                                    modifier = Modifier.align(Alignment.TopStart).testTag("video-popup-return"), tint = Color.White)
                            }
                            VideoIconButton(NagiIcons.X, strings(R.string.ui_video_close),
                                onClick = { controller.close(pause = true) },
                                modifier = Modifier.align(Alignment.TopEnd).testTag("video-popup-close"), tint = Color.White)
                            VideoIconButton(if (video.playback.playing) NagiIcons.Pause else NagiIcons.Play,
                                strings(if (video.playback.playing) R.string.ui_video_pause else R.string.ui_video_play),
                                onClick = { interaction++; video.control(if (video.playback.playing) "pause" else "play") },
                                modifier = Modifier.align(Alignment.Center).testTag("video-popup-play-pause"), tint = Color.White)
                            Text("${videoTime(video.playback.currentTime)} / ${videoTime(video.playback.duration, unknown = true)}",
                                color = Color.White, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 16.dp)
                                    .testTag("video-popup-time"))
                            VideoIconButton(NagiIcons.Maximize, strings(R.string.ui_video_fullscreen),
                                onClick = controller::expand,
                                modifier = Modifier.align(Alignment.BottomEnd).testTag("video-popup-expand"), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private enum class VideoCorner(val left: Boolean, val top: Boolean) {
    TopLeft(true, true), TopRight(false, true), BottomLeft(true, false), BottomRight(false, false)
}

private fun videoCornerAt(point: Offset, size: Size, edge: Float): VideoCorner? {
    if (point.x !in 0f..size.width || point.y !in 0f..size.height) return null
    return VideoCorner.entries.firstOrNull {
        (if (it.left) point.x <= edge else point.x >= size.width - edge) &&
            (if (it.top) point.y <= edge else point.y >= size.height - edge)
    }
}

/** Mouse-only corners leave the video tap, touch drag, pinch and control hit targets intact. */
@Composable
private fun Modifier.videoCornerResize(bounds: Rect, viewport: Size, aspectRatio: Float,
    onStart: () -> Unit, onResize: (Rect) -> Unit): Modifier {
    val density = LocalDensity.current
    val context = LocalContext.current
    val edge = with(density) { 16.dp.toPx() }
    val minimum = with(density) { 240.dp.toPx() }
    val maximum = with(density) { 640.dp.toPx() }
    val currentBounds by rememberUpdatedState(bounds)
    val startResize by rememberUpdatedState(onStart)
    val resize by rememberUpdatedState(onResize)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hovered by remember { mutableStateOf<VideoCorner?>(null) }
    var grabbed by remember { mutableStateOf<VideoCorner?>(null) }
    val corner = grabbed ?: hovered
    val cursor = remember(context, corner) {
        if (corner == null) PointerIcon.Default else PointerIcon(android.view.PointerIcon.getSystemIcon(context,
            if (corner.left == corner.top) android.view.PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW
            else android.view.PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW))
    }
    return this.onGloballyPositioned { origin = it.positionInRoot() }
        .pointerHoverIcon(cursor, overrideDescendants = corner != null)
        .pointerInput(edge) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val mouse = event.changes.firstOrNull { it.type == PointerType.Mouse }
                    hovered = if (event.type == PointerEventType.Exit || mouse == null) null
                        else videoCornerAt(mouse.position, Size(size.width.toFloat(), size.height.toFloat()), edge)
                }
            }
        }
        .pointerInput(edge, minimum, maximum, viewport, aspectRatio) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.type != PointerType.Mouse || !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val handle = videoCornerAt(down.position, Size(size.width.toFloat(), size.height.toFloat()), edge)
                    ?: return@awaitEachGesture
                val initial = currentBounds
                val pointerStart = origin + down.position
                val anchorX = if (handle.left) initial.right else initial.left
                val anchorY = if (handle.top) initial.bottom else initial.top
                val availableWidth = if (handle.left) anchorX else viewport.width - anchorX
                val availableHeight = if (handle.top) anchorY else viewport.height - anchorY
                val limit = minOf(maximum, availableWidth, availableHeight * aspectRatio).coerceAtLeast(1f)
                grabbed = handle
                startResize()
                down.consume()
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        // Own the release as well, so it cannot become a click on the page/player.
                        change.consume()
                        if (!change.pressed) break
                        // Use root coordinates so moving the top/left edge never feeds back into the drag.
                        val delta = origin + change.position - pointerStart
                        val dx = delta.x * if (handle.left) -1f else 1f
                        val dy = delta.y * if (handle.top) -1f else 1f
                        val projected = (dx + dy / aspectRatio) / (1f + 1f / (aspectRatio * aspectRatio))
                        val width = (initial.width + projected).coerceIn(minOf(minimum, limit), limit)
                        val height = width / aspectRatio
                        val left = if (handle.left) anchorX - width else anchorX
                        val top = if (handle.top) anchorY - height else anchorY
                        resize(Rect(left, top, left + width, top + height))
                    }
                } finally { grabbed = null }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoIconButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: Color? = null) {
    Box(modifier) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
            IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
                Icon(icon, label, Modifier.size(24.dp), tint = tint ?: LocalContentColor.current)
            }
        }
    }
}

private fun Modifier.onVideoPositioned(controller: VideoPresentationController) =
    onGloballyPositioned { controller.refreshPip() }

private fun videoTime(seconds: Double, unknown: Boolean = false): String {
    if (!seconds.isFinite() || seconds <= 0.0) return if (unknown) "--:--" else "0:00"
    val total = seconds.toLong()
    val tail = "${total / 60 % 60}:${(total % 60).toString().padStart(2, '0')}"
    return if (total >= 3600) "${total / 3600}:${tail.padStart(5, '0')}" else "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
