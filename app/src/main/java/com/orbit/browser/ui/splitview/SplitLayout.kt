package com.orbit.browser.ui.splitview

import com.orbit.browser.R
import com.orbit.browser.ui.localization.rememberOrbitStrings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SplitLayout(ratio: Float, onRatioChange: (Float) -> Unit,
    left: @Composable () -> Unit, right: @Composable () -> Unit) {
    val strings = rememberOrbitStrings()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = (maxWidth - 24.dp).coerceAtLeast(1.dp)
        val widthPx = with(LocalDensity.current) { availableWidth.toPx() }
        // Keep both panes usable when the tablet is rotated or the sidebar is widened.
        val minimumRatio = maxOf(0.25f, (180.dp / availableWidth).coerceAtMost(0.5f))
        val maximumRatio = 1f - minimumRatio
        val displayedRatio = ratio.coerceIn(minimumRatio, maximumRatio)
        val currentChange by rememberUpdatedState(onRatioChange)
        val currentRatio by rememberUpdatedState(displayedRatio)
        val minimum by rememberUpdatedState(minimumRatio)
        val maximum by rememberUpdatedState(maximumRatio)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(displayedRatio)) { left() }
            Box(Modifier.width(24.dp).fillMaxHeight().semantics { contentDescription = strings(R.string.ui_resize_split_view) }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, amount ->
                        change.consume(); currentChange((currentRatio + amount / widthPx).coerceIn(minimum, maximum))
                    }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(4.dp).height(64.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)))
            }
            Box(Modifier.weight(1f - displayedRatio)) { right() }
        }
    }
}
