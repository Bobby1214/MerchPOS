package com.bobbykong.merchpos.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.bobbykong.merchpos.ui.events.EventsScreen
import com.bobbykong.merchpos.ui.orders.OrdersScreen
import com.bobbykong.merchpos.ui.products.ProductsScreen
import com.bobbykong.merchpos.ui.report.ReportScreen
import com.bobbykong.merchpos.ui.sell.SellScreen

enum class Tab(val label: String, val icon: ImageVector) {
    SELL("Sell", Icons.Filled.ShoppingCart),
    PRODUCTS("Products", Icons.Filled.Star),
    ORDERS("Orders", Icons.AutoMirrored.Filled.List),
    REPORT("Report", Icons.Filled.Info),
    EVENTS("Events", Icons.Filled.DateRange),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchPosRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.SELL) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding).fillMaxSize()
        when (tab) {
            Tab.SELL -> SellScreen(snackbarHostState, modifier)
            Tab.PRODUCTS -> ProductsScreen(modifier)
            Tab.ORDERS -> OrdersScreen(modifier)
            Tab.REPORT -> ReportScreen(modifier)
            Tab.EVENTS -> EventsScreen(snackbarHostState, modifier)
        }
    }
}
