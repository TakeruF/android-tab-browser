package com.orbit.browser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun Favicon(path: String?, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (path != null) AsyncImage(model = if (path.startsWith('/')) File(path) else path,
            contentDescription = null, modifier = Modifier.size(18.dp))
        else Icon(Icons.Outlined.Language, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
