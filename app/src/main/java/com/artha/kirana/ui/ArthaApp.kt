package com.artha.kirana.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.artha.kirana.ui.entry.SaleEntryScreen
import com.artha.kirana.ui.home.HomeScreen
import com.artha.kirana.ui.inventory.InventoryScreen
import com.artha.kirana.ui.khata.KhataScreen
import com.artha.kirana.ui.pnl.PnlScreen

enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Inventory("inventory", "Inventory", Icons.Filled.Inventory2),
    Khata("khata", "Khata", Icons.AutoMirrored.Filled.MenuBook),
    Pnl("pnl", "P&L", Icons.Filled.BarChart),
}

const val ROUTE_SALE_ENTRY = "sale_entry"

@Composable
fun ArthaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val topRoutes = TopDest.entries.map { it.route }

    Scaffold(
        bottomBar = {
            if (currentRoute in topRoutes) {
                NavigationBar {
                    TopDest.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == TopDest.Home.route) {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(ROUTE_SALE_ENTRY) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New Sale") },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopDest.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopDest.Home.route) { HomeScreen() }
            composable(TopDest.Inventory.route) { InventoryScreen() }
            composable(TopDest.Khata.route) { KhataScreen() }
            composable(TopDest.Pnl.route) { PnlScreen() }
            composable(ROUTE_SALE_ENTRY) {
                SaleEntryScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}
