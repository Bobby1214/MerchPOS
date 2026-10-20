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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bobbykong.merchpos.data.model.Product

/** Left column: "All" + one row per category with its item count. [selected] null = All. */
@Composable
fun CategorySidebar(
    products: List<Product>,
    categories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val countByCategory = products.groupingBy { it.category }.eachCount()
    LazyColumn(
        modifier = modifier.width(112.dp).fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item { SidebarItem("All", products.size, selected == null) { onSelect(null) } }
        items(categories) { category ->
            SidebarItem(category, countByCategory[category] ?: 0, selected == category) { onSelect(category) }
        }
    }
}

@Composable
private fun SidebarItem(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.secondaryContainer else colors.surface)
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
