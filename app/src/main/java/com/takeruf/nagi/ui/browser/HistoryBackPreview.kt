package com.takeruf.nagi.ui.browser

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.takeruf.nagi.browser.engine.AndroidEngineSurface
import com.takeruf.nagi.browser.engine.BrowserEngine
import com.takeruf.nagi.browser.engine.HistoryPagePreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Composition-owned visuals only. History is untouched until the gesture commits. */
class HistoryBackPreviewState {
    var engine by mutableStateOf<BrowserEngine?>(null)
        private set
    var page by mutableStateOf<HistoryPagePreview?>(null)
        private set
    private val position = Animatable(0f)
    private var generation = 0L
    val progress get() = position.value
    var fromRight by mutableStateOf(false)
        private set
    var nativeTranslation by mutableStateOf(false)
        private set

    suspend fun begin(): Long { reset(); return generation }

    suspend fun update(owner: Long, target: BrowserEngine, event: BackEventCompat) {
        update(owner, target, true, event.progress, event.swipeEdge == BackEventCompat.EDGE_RIGHT)
    }

    suspend fun update(owner: Long, target: BrowserEngine, back: Boolean, progress: Float, fromRight: Boolean = !back,
        nativeTranslation: Boolean = false) {
        if (owner != generation) return
        if (engine !== target) {
            page = (target as? AndroidEngineSurface)?.historyPagePreview(back) ?: return
            engine = target
        }
        this.fromRight = fromRight
        this.nativeTranslation = nativeTranslation
        position.snapTo(progress.coerceIn(0f, 1f))
    }

    suspend fun complete(owner: Long, navigate: () -> Unit) {
        if (owner != generation) return
        val renderer = engine as? AndroidEngineSurface
        val visualChanges = renderer?.historyVisualGeneration
        try {
            if (engine != null) position.animateTo(1f, settle())
            if (owner != generation) return
            val before = visualChanges?.value
            navigate()
            // Keep the previous-page image covering the old document until native paint commits.
            if (visualChanges != null) withTimeoutOrNull(1_500) { visualChanges.first { it != before } }
        } finally { if (owner == generation) reset() }
    }

    suspend fun cancel(owner: Long) {
        if (owner != generation) return
        try { if (engine != null) position.animateTo(0f, settle()) }
        finally { if (owner == generation) reset() }
    }

    suspend fun reset() { generation++; engine = null; page = null; position.snapTo(0f) }
    private fun settle() = tween<Float>(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
}

/** Each pane owns its trackpad animation; changing Split focus cannot cancel its first frame. */
internal class TrackpadHistoryPreview(private val scope: CoroutineScope, private val engine: BrowserEngine) {
    val state = HistoryBackPreviewState()
    private var owner: Long? = null
    private var back: Boolean? = null
    private var job: Job? = null
    fun dispose() { job?.cancel(); owner = null; back = null }

    fun progress(back: Boolean, progress: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (owner == null || this@TrackpadHistoryPreview.back != back) {
                owner = state.begin()
                this@TrackpadHistoryPreview.back = back
            }
            state.update(owner!!, engine, back, progress, nativeTranslation = true)
        }
    }

    fun finish(back: Boolean?) {
        job?.cancel()
        val token = owner
        owner = null
        this.back = null
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (back == null) token?.let { state.cancel(it) }
            else {
                val active = token ?: state.begin().also { state.update(it, engine, back, 0f, nativeTranslation = true) }
                state.complete(active) { if (back) engine.goBack() else engine.goForward() }
            }
        }
    }
}

@Composable
internal fun HistoryBackPreview(state: HistoryBackPreviewState?, engine: BrowserEngine,
    modifier: Modifier = Modifier, newTab: @Composable () -> Unit, content: @Composable () -> Unit) {
    val active = state?.engine === engine
    Box(modifier.clipToBounds()) {
        if (active) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface)
            .testTag("history-back-preview").clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            val page = state?.page
            if (page?.bitmap != null) Image(page.bitmap.asImageBitmap(), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            else if (page?.url == "about:blank") newTab()
            else Text(page?.url.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.24f * (1f - state!!.progress))))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = if (active && !state!!.nativeTranslation) size.width * state.progress * (if (state.fromRight) -1 else 1) else 0f
        }) { content() }
    }
}
