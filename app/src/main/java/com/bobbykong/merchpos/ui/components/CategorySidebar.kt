package com.bobbykong.merchpos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.bobbykong.merchpos.ui.theme.Tint
import com.bobbykong.merchpos.ui.theme.categoryTint
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
        modifier = modifier.width(100.dp).fillMaxHeight(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item(key = 0) { SidebarItem(ALL, products.size, selected == null) { onSelect(null) } }
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
    // "All" uses the main purple; each category has its own pastel
    val tint = if (label == ALL) Tint(colors.primaryContainer, colors.onPrimaryContainer) else categoryTint(label)
    val active = selected || dragging
    val background by animateColorAsState(if (active) tint.container else Color.Transparent, label = "sidebarBg")
    val scale by animateFloatAsState(
        if (dragging) 1.06f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sidebarScale",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(start = 8.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Coloured dot: the same colour as the product placeholders of this category
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) tint.content else tint.container))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) tint.content else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) tint.content.copy(alpha = 0.7f) else colors.onSurfaceVariant,
            )
        }
    }
}

private const val ALL = "All"
