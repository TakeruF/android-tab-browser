package com.takeruf.nagi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.lucide.R as LucideR

/** One Lucide family for generic app controls; site favicons and Space emoji keep their identity. */
object NagiIcons {
    val BookOpen: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_book_open)
    val SlidersHorizontal: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_sliders_horizontal)
    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_search)
    val Plus: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_plus)
    val House: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_house)
    val ChevronRight: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_chevron_right)
    val ChevronDown: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_chevron_down)
    val ChevronUp: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_chevron_up)
    val Ellipsis: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_ellipsis)
    val History: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_history)
    val Bookmark: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_bookmark)
    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_settings)
    val Check: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_check)
    val X: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_x)
    val Globe: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_globe)
    val PanelsTopLeft: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_panels_top_left)
    val Layers: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_layers)
    val Zap: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_zap)
    val Link: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_link)
    val Share: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_share)
    val Lock: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_lock)
    val RotateCw: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_rotate_cw)
    val Trash2: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_trash_2)
    val CircleCheck: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_circle_check)
    val Pencil: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_pencil)
    val KeyRound: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_key_round)
    val Pin: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_pin)
    val PinOff: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_pin_off)
    val Star: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_star)
    val Monitor: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_monitor)
    val Smartphone: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_smartphone)
    val Columns2: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_columns_2)
    val ArrowLeftRight: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_arrow_left_right)
    val Palette: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_palette)
    val ShieldCheck: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_shield_check)
    val ShieldOff: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_shield_off)
    val Archive: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_archive)
    val ArrowBack: ImageVector
        @Composable get() = ImageVector.vectorResource(if (LocalLayoutDirection.current == LayoutDirection.Rtl)
            LucideR.drawable.lucide_ic_arrow_right else LucideR.drawable.lucide_ic_arrow_left)
    val ArrowForward: ImageVector
        @Composable get() = ImageVector.vectorResource(if (LocalLayoutDirection.current == LayoutDirection.Rtl)
            LucideR.drawable.lucide_ic_arrow_left else LucideR.drawable.lucide_ic_arrow_right)
    val SidebarClose: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_panel_left_close)
    val SidebarOpen: ImageVector
        @Composable get() = ImageVector.vectorResource(LucideR.drawable.lucide_ic_panel_left_open)
}
