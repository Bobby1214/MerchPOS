package com.bobbykong.merchpos.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bobbykong.merchpos.R
import com.bobbykong.merchpos.ui.components.pressBounce
import com.bobbykong.merchpos.ui.events.EventsScreen
import com.bobbykong.merchpos.ui.orders.OrdersScreen
import com.bobbykong.merchpos.ui.products.ProductsScreen
import com.bobbykong.merchpos.ui.report.ReportScreen
import com.bobbykong.merchpos.ui.sell.SellScreen
import com.bobbykong.merchpos.ui.theme.tintAt

enum class Tab(val label: String, val icon: ImageVector) {
    SELL("Sell", Icons.Filled.ShoppingCart),
    PRODUCTS("Products", Icons.Filled.Star),
    ORDERS("Orders", Icons.AutoMirrored.Filled.List),
    REPORT("Report", Icons.Filled.Info),
    EVENTS("Events", Icons.Filled.DateRange),
}

@Composable
fun MerchPosRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.SELL) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { Header(tab) },
        bottomBar = { PillNavBar(selected = tab, onSelect = { tab = it }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // New tab fades in and rises a little; the old one fades out fast so taps feel instant
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 20 })
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "tab",
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        ) { current ->
            val modifier = Modifier.fillMaxSize()
            when (current) {
                Tab.SELL -> SellScreen(snackbarHostState, modifier)
                Tab.PRODUCTS -> ProductsScreen(snackbarHostState, modifier)
                Tab.ORDERS -> OrdersScreen(snackbarHostState, modifier)
                Tab.REPORT -> ReportScreen(modifier)
                Tab.EVENTS -> EventsScreen(snackbarHostState, modifier)
            }
        }
    }
}

/** App icon + the tab name, which slides when the tab changes. */
@Composable
private fun Header(tab: Tab) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, Modifier.size(64.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "MerchPOS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedContent(
                targetState = tab.label,
                transitionSpec = {
                    (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                },
                label = "title",
            ) { title ->
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

/** Floating rounded bar. The chosen tab grows into a coloured pill with its name. */
@Composable
private fun PillNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth(),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Tab.entries.forEach { item ->
                NavPill(item, selected = item == selected, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun RowScope.NavPill(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val tint = tintAt(tab.ordinal)
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    val weight by animateFloatAsState(if (selected) 2.4f else 1f, bouncy, label = "navWeight")
    val background by animateColorAsState(if (selected) tint.container else Color.Transparent, label = "navBg")
    val content by animateColorAsState(
        if (selected) tint.content else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navContent",
    )
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .weight(weight)
            .height(48.dp)
            .pressBounce(interaction, 0.88f)
            .clip(CircleShape)
            .background(background)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = content)
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
