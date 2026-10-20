package com.bobbykong.merchpos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbykong.merchpos.data.model.Product
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Left column: "All" + one row per category with its item count. [selected] null = All.
 * With [onReorder], categories can be held and dragged ("All" stays on top); the new order is passed on drop.
 */
@Composable
fun CategorySidebar(
    products: List<Product>,
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onReorder: ((List<String>) -> Unit)? = null,
) {
    val countByCategory = products.groupingBy { it.category }.eachCount()
    // Local copy so the list moves while the finger moves; saved once on drop
    var order by remember(categories) { mutableStateOf(categories) }
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // Keys are the category names; "All" has no String key, so it never moves
        val fromName = from.key as? String ?: return@rememberReorderableLazyListState
        val toName = to.key as? String ?: return@rememberReorderableLazyListState
        order = order.toMutableList().apply { add(indexOf(toName), removeAt(indexOf(fromName))) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    LazyColumn(
        modifier = modifier.width(112.dp).fillMaxHeight(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = 0) { SidebarItem("All", products.size, selected == null) { onSelect(null) } }
        items(order, key = { it }) { category ->
            ReorderableItem(reorderState, key = category, enabled = onReorder != null) { dragging ->
                SidebarItem(
                    label = category,
                    count = countByCategory[category] ?: 0,
                    selected = selected == category,
                    dragging = dragging,
                    modifier = Modifier.longPressDraggableHandle(
                        enabled = onReorder != null,
                        onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                        onDragStopped = { onReorder?.invoke(order) },
                    ),
                ) { onSelect(category) }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    dragging: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .shadow(if (dragging) 6.dp else 0.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected || dragging) colors.secondaryContainer else colors.surface)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("$count", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
