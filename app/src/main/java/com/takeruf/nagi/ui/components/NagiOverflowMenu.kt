package com.takeruf.nagi.ui.components

import com.takeruf.nagi.ui.theme.NagiShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Nested corners share a center: outer radius = inner radius + inset. */
object NagiOverflowMenuDefaults {
    // Material DropdownMenu supplies the same 8dp inset above and below its content.
    val CardInset = 8.dp
    val ItemRadius = NagiShapes.Radius
    val CardRadius = ItemRadius + CardInset
    val ShadowElevation = 6.dp
    val AnchorGap = 4.dp
}

@Immutable
data class NagiMenuDimensions(
    val minWidth: Dp = 240.dp,
    val maxWidth: Dp = 320.dp,
    val rowHeight: Dp = 48.dp,
    val horizontalPadding: Dp = 12.dp,
    val verticalPadding: Dp = 12.dp,
    val iconSize: Dp = 20.dp,
    val iconTextGap: Dp = 12.dp,
    val dividerPadding: Dp = 4.dp,
)

private val TouchMenuDimensions = NagiMenuDimensions()
private val CompactMenuDimensions = NagiMenuDimensions(
    minWidth = 200.dp, maxWidth = 280.dp, rowHeight = 36.dp,
    horizontalPadding = 8.dp, verticalPadding = 6.dp,
    iconSize = 18.dp, iconTextGap = 8.dp, dividerPadding = 2.dp,
)

/** Also used by custom menu content, such as page zoom and blocking status. */
val LocalNagiMenuDimensions = staticCompositionLocalOf { TouchMenuDimensions }

/** Place inside the trigger's Box. Material retains anchoring, scrolling, focus and animation. */
@Composable
fun NagiOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dimensions = if (LocalNagiUiDimensions.current.compact) CompactMenuDimensions else TouchMenuDimensions
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(
            min = dimensions.minWidth,
            max = dimensions.maxWidth,
        ),
        offset = DpOffset(0.dp, NagiOverflowMenuDefaults.AnchorGap),
        shape = RoundedCornerShape(NagiOverflowMenuDefaults.CardRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = NagiOverflowMenuDefaults.ShadowElevation,
    ) {
        CompositionLocalProvider(LocalNagiMenuDimensions provides dimensions,
            LocalMinimumInteractiveComponentSize provides dimensions.rowHeight) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = NagiOverflowMenuDefaults.CardInset),
                content = content,
            )
        }
    }
}

@Composable
fun NagiOverflowMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val dimensions = LocalNagiMenuDimensions.current
    val interaction = remember { MutableInteractionSource() }
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides foreground.copy(alpha = if (enabled) 1f else 0.38f)) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Row(
                modifier.fillMaxWidth()
                    // A minimum, not a fixed height: large accessibility fonts may grow the row.
                    .heightIn(min = dimensions.rowHeight)
                    .clip(RoundedCornerShape(NagiOverflowMenuDefaults.ItemRadius))
                    // The Material indication supplies hover/focus/press layers, all clipped to the same R.
                    .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
                        indication = ripple(), onClick = onClick)
                    .padding(horizontal = dimensions.horizontalPadding,
                        vertical = dimensions.verticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.iconTextGap),
            ) {
                if (leadingIcon != null) Box(Modifier.size(dimensions.iconSize), contentAlignment = Alignment.Center) { leadingIcon() }
                Box(Modifier.weight(1f)) { text() }
            }
        }
    }
}

@Composable
fun NagiOverflowMenuDivider() {
    val dimensions = LocalNagiMenuDimensions.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = dimensions.horizontalPadding,
            vertical = dimensions.dividerPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
