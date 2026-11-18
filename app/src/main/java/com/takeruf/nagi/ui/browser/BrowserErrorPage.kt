package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.takeruf.nagi.R
import com.takeruf.nagi.browser.engine.PageErrorKind
import com.takeruf.nagi.ui.components.NagiIcons
import com.takeruf.nagi.ui.localization.rememberNagiStrings
import com.takeruf.nagi.ui.theme.NagiShapes

/** Native replacement for provider-generated error documents; the engine keeps its history. */
@Composable
internal fun BrowserErrorPage(kind: PageErrorKind, loading: Boolean, onFocus: () -> Unit, onRetry: () -> Unit) {
    val strings = rememberNagiStrings()
    val colors = MaterialTheme.colorScheme
    val focus by rememberUpdatedState(onFocus)
    val (title, description) = when (kind) {
        PageErrorKind.CONNECTION -> R.string.ui_page_connection_title to R.string.ui_page_connection_description
        PageErrorKind.TIMEOUT -> R.string.ui_page_timeout_title to R.string.ui_page_timeout_description
        PageErrorKind.CERTIFICATE -> R.string.ui_page_certificate_title to R.string.ui_page_certificate_description
        PageErrorKind.OTHER -> R.string.ui_page_error_title to R.string.ui_page_error_description
    }
    Box(Modifier.fillMaxSize().background(colors.surface).testTag("browser-error-page").pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            focus()
        }
    }, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(88.dp).background(colors.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                if (loading) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 2.dp)
                else Icon(when (kind) {
                    PageErrorKind.CONNECTION -> NagiIcons.WifiOff
                    PageErrorKind.TIMEOUT -> NagiIcons.Clock
                    PageErrorKind.CERTIFICATE -> NagiIcons.ShieldAlert
                    PageErrorKind.OTHER -> NagiIcons.FileWarning
                }, null, Modifier.size(40.dp).testTag("browser-error-icon-${kind.name}"), tint = colors.onSurfaceVariant)
            }
            Text(strings(title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(strings(description), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Button(onClick = onRetry, enabled = !loading, shape = NagiShapes.Pill,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp).testTag("browser-error-retry")) {
                Icon(NagiIcons.RotateCw, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings(R.string.ui_retry))
            }
        }
    }
}
