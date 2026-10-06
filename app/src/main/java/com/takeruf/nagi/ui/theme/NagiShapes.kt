package com.takeruf.nagi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Shared corners for controls, cards and their pressed/hovered backgrounds. */
object NagiShapes {
    val Radius = 12.dp
    val Rounded = RoundedCornerShape(Radius)
    val Sidebar = RoundedCornerShape(topEnd = Radius, bottomEnd = Radius)
    val Bottom = RoundedCornerShape(bottomStart = Radius, bottomEnd = Radius)
    val Material = Shapes(
        extraSmall = Rounded, small = Rounded, medium = Rounded,
        large = Rounded, extraLarge = Rounded,
    )
}
