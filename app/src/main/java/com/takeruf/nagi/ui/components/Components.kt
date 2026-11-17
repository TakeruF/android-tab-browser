package com.takeruf.nagi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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

/**
 * True while a physical keyboard is attached. Shortcut hints such as "Ctrl L" only make sense
 * then; LocalConfiguration updates when a keyboard is connected or removed.
 */
@Composable
fun hardwareKeyboardConnected(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
        configuration.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO
}

@Composable
fun AddressActionButton(modifier: Modifier = Modifier, enabled: Boolean = true,
    onClick: () -> Unit, content: @Composable () -> Unit) {
    // Keep paired actions compact in both address bars: a 32 x 48dp touch area whose
    // pressed/hover feedback is a full 32dp circle, not a stretched pill.
    val color = LocalContentColor.current
    Box(modifier.width(32.dp).height(48.dp)
        .clickable(enabled = enabled, role = Role.Button, interactionSource = null,
            indication = ripple(bounded = false, radius = 16.dp), onClick = onClick),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) color else color.copy(alpha = 0.38f), content = content)
    }
}

@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 21.dp, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Favicon(path: String?, modifier: Modifier = Modifier, siteUrl: String? = null,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp) {
    val context = LocalContext.current
    if ((context as? com.takeruf.nagi.MainActivity)?.isPrivateBrowsing == true) {
        Box(modifier.size(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(NagiIcons.Globe, null, Modifier.size(16.dp))
        }
        return
    }
    val store = remember(context.applicationContext) { FaviconStore.get(context) }
    val shared by store.icons.collectAsState()
    val dark = com.takeruf.nagi.ui.theme.LocalNagiDarkTheme.current
    val origin = remember(siteUrl) { faviconOrigin(siteUrl) }
    LaunchedEffect(path, siteUrl) { store.resolve(siteUrl, path) }
    // Theme changes only select a local variant; they never restart network recovery.
    val variants = shared[origin]
    val sharedPath = variants?.select(dark)
    val source = remember(path, sharedPath, variants) {
        faviconSource(if (variants != null) sharedPath else path?.takeIf { !it.startsWith('/') }, null)
    }
    var loaded by remember(source) { mutableStateOf(false) }
    // Chrome-style: the favicon sits directly on its row or tile, without a backing plate.
    Box(modifier.size(maxOf(24.dp, iconSize + 6.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        AsyncImage(model = source, contentDescription = null, modifier = Modifier.size(iconSize),
            onState = { loaded = it is AsyncImagePainter.State.Success })
        if (!loaded) {
            // Sites without an icon get the same globe everywhere (tabs, favorites, drag previews).
            Icon(NagiIcons.Globe, null, modifier = Modifier.size(iconSize - 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
