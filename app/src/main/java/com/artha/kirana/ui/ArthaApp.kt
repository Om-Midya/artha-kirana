package com.artha.kirana.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

@Composable
fun ArthaApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                TopDest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
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
        }
    }
}
