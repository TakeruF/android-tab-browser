package com.takeruf.nagi.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import coil3.compose.AsyncImage
import com.takeruf.nagi.browser.newtab.NewTabPhotoStore
import com.takeruf.nagi.domain.model.BrowserSettings
import com.takeruf.nagi.domain.model.NewTabBackground

@Composable
internal fun NewTabBackground(settings: BrowserSettings, modifier: Modifier = Modifier) {
    val color = Color(settings.newTabColor or 0xFF000000)
    val base = MaterialTheme.colorScheme.surface
    Box(modifier.background(base).testTag("new-tab-background-${settings.newTabBackground.name}")) {
        when (settings.newTabBackground) {
            NewTabBackground.DEFAULT -> Unit
            NewTabBackground.SOLID -> Box(Modifier.matchParentSize().background(color))
            NewTabBackground.GRADIENT -> Box(Modifier.matchParentSize().background(
                Brush.linearGradient(listOf(color, Color(0xFF172C3A)))))
            NewTabBackground.PHOTO -> AsyncImage(
                model = NewTabPhotoStore.file(LocalContext.current, settings.newTabPhoto),
                contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // A subtle scrim softens photos; foreground controls have their own readable surfaces.
        if (settings.newTabBackground == NewTabBackground.PHOTO)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f)))
    }
}
