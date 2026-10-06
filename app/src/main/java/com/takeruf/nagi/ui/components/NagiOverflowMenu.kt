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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** Nested corners share a center: outer radius = inner radius + inset. */
object NagiOverflowMenuDefaults {
    val CardInset = 8.dp
    val ItemRadius = NagiShapes.Radius
    val CardRadius = ItemRadius + CardInset
    // Material DropdownMenu supplies the same 8dp inset above and below its content.
    val ItemMinHeight = 48.dp
    val ItemHorizontalPadding = 12.dp
    val ItemVerticalPadding = 12.dp
    val IconSize = 20.dp
    val IconTextGap = 12.dp
    val MinWidth = 240.dp
    val MaxWidth = 320.dp
    val ShadowElevation = 6.dp
    val AnchorGap = 4.dp
    val DividerVerticalPadding = 4.dp
}

/** Place inside the trigger's Box. Material retains anchoring, scrolling, focus and animation. */
@Composable
fun NagiOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(
            min = NagiOverflowMenuDefaults.MinWidth,
            max = NagiOverflowMenuDefaults.MaxWidth,
        ),
        offset = DpOffset(0.dp, NagiOverflowMenuDefaults.AnchorGap),
        shape = RoundedCornerShape(NagiOverflowMenuDefaults.CardRadius),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = NagiOverflowMenuDefaults.ShadowElevation,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = NagiOverflowMenuDefaults.CardInset),
            content = content,
        )
    }
}

@Composable
fun NagiOverflowMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val foreground = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    CompositionLocalProvider(LocalContentColor provides foreground.copy(alpha = if (enabled) 1f else 0.38f)) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Row(
                modifier.fillMaxWidth()
                    // A minimum, not a fixed height: large accessibility fonts may grow the row.
                    .heightIn(min = NagiOverflowMenuDefaults.ItemMinHeight)
                    .clip(RoundedCornerShape(NagiOverflowMenuDefaults.ItemRadius))
                    // The Material indication supplies hover/focus/press layers, all clipped to the same R.
                    .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction,
                        indication = ripple(), onClick = onClick)
                    .padding(horizontal = NagiOverflowMenuDefaults.ItemHorizontalPadding,
                        vertical = NagiOverflowMenuDefaults.ItemVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NagiOverflowMenuDefaults.IconTextGap),
            ) {
                Box(Modifier.size(NagiOverflowMenuDefaults.IconSize), contentAlignment = Alignment.Center) { leadingIcon() }
                Box(Modifier.weight(1f)) { text() }
            }
        }
    }
}

@Composable
fun NagiOverflowMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = NagiOverflowMenuDefaults.ItemHorizontalPadding,
            vertical = NagiOverflowMenuDefaults.DividerVerticalPadding),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
