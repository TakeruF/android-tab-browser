package com.takeruf.nagi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.takeruf.nagi.browser.favicon.FaviconStore
import com.takeruf.nagi.browser.favicon.faviconOrigin
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.io.File
import java.net.URI

internal fun faviconSource(path: String?, siteUrl: String?): Any? {
    if (!path.isNullOrBlank()) {
        if (!path.startsWith('/')) return path
        File(path).takeIf { it.isFile }?.let { return it }
    }
    return runCatching {
        val uri = URI(siteUrl ?: return null)
        if (uri.scheme !in setOf("https", "http") || uri.host == null) return null
        URI(uri.scheme, null, uri.host, uri.port, "/favicon.ico", null, null).toString()
    }.getOrNull()
}

@Composable
fun AddressActionButton(modifier: Modifier = Modifier, enabled: Boolean = true,
    onClick: () -> Unit, content: @Composable () -> Unit) {
    // Keep paired actions compact in both address bars.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(onClick = onClick, enabled = enabled,
            modifier = modifier.width(32.dp).height(48.dp), content = content)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun Favicon(path: String?, modifier: Modifier = Modifier, siteUrl: String? = null, fallbackText: String? = null) {
    val context = LocalContext.current
    if ((context as? com.takeruf.nagi.MainActivity)?.isPrivateBrowsing == true) {
        Box(modifier.size(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(NagiIcons.Globe, null, Modifier.size(16.dp))
        }
        return
    }
    val store = remember(context.applicationContext) { FaviconStore.get(context) }
    val shared by store.icons.collectAsState()
    val origin = remember(siteUrl) { faviconOrigin(siteUrl) }
    LaunchedEffect(path, siteUrl) { store.resolve(siteUrl, path) }
    val sharedPath = shared[origin]
    val source = remember(path, siteUrl, sharedPath) { faviconSource(sharedPath ?: path, null) }
    var loaded by remember(source) { mutableStateOf(false) }
    Box(modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        AsyncImage(model = source, contentDescription = null, modifier = Modifier.size(18.dp),
            onState = { loaded = it is AsyncImagePainter.State.Success })
        if (!loaded) {
            if (!fallbackText.isNullOrBlank()) Text(fallbackText.take(1), style = MaterialTheme.typography.titleLarge)
            else Icon(NagiIcons.Globe, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
