package com.willyshare.willykez.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.willyshare.willykez.ui.theme.SleekSurfaceContainer

/**
 * Grouped-list pattern: a column of rows that reads as one seamless card, with a
 * thin 2dp seam between rows instead of full gaps. First/last rows get the big
 * shape-scale corners on their outer edge; every seam between rows uses a tiny
 * corner radius so the group still feels like one rounded unit, not one row
 * with square corners butted against the next.
 */
enum class GroupPosition { FIRST, MIDDLE, LAST, ONLY }

@Composable
fun groupedItemShape(position: GroupPosition): CornerBasedShape {
    val outer = MaterialTheme.shapes.large
    val inner = MaterialTheme.shapes.extraSmall
    return when (position) {
        GroupPosition.FIRST -> outer.copy(bottomStart = inner.bottomStart, bottomEnd = inner.bottomEnd)
        GroupPosition.MIDDLE -> inner
        GroupPosition.LAST -> outer.copy(topStart = inner.topStart, topEnd = inner.topEnd)
        GroupPosition.ONLY -> outer
    }
}

@Composable
fun GroupedListItem(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupedItemShape(position),
        color = color ?: SleekSurfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        content()
    }
}

@Composable
fun GroupedListColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content()
    }
}

/** Helper: derive each item's [GroupPosition] from its index in a fixed-size group. */
fun groupPositionFor(index: Int, count: Int): GroupPosition =
    when {
        count == 1 -> GroupPosition.ONLY
        index == 0 -> GroupPosition.FIRST
        index == count - 1 -> GroupPosition.LAST
        else -> GroupPosition.MIDDLE
    }
