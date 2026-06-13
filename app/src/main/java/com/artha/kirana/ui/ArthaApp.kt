package com.artha.kirana.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.artha.kirana.ui.assistant.AssistantScreen
import com.artha.kirana.ui.entry.SaleEntryScreen
import com.artha.kirana.ui.home.HomeScreen
import com.artha.kirana.ui.inventory.InventoryScreen
import com.artha.kirana.ui.khata.KhataPartyDetail
import com.artha.kirana.ui.khata.KhataScreen
import com.artha.kirana.ui.pnl.PnlScreen
import com.artha.kirana.ui.theme.BrandGold

/** The four corner destinations (the center slot is the raised Assistant FAB). */
enum class TopDest(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Inventory("inventory", "Inventory", Icons.Filled.Inventory2),
    Khata("khata", "Khata", Icons.AutoMirrored.Filled.MenuBook),
    Pnl("pnl", "P&L", Icons.Filled.BarChart),
}

const val ROUTE_SALE_ENTRY = "sale_entry"
const val ROUTE_KHATA_DETAIL = "khata" // full pattern: khata/{partyId}
const val ROUTE_ASSISTANT = "assistant"

@Composable
fun ArthaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val barRoutes = TopDest.entries.map { it.route } + ROUTE_ASSISTANT

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result ignored — alerts simply stay silent if denied */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in barRoutes) {
                ArthaBottomBar(navController, currentRoute)
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
            composable(TopDest.Khata.route) {
                KhataScreen(onParty = { id -> navController.navigate("$ROUTE_KHATA_DETAIL/$id") })
            }
            composable(
                route = "$ROUTE_KHATA_DETAIL/{partyId}",
                arguments = listOf(navArgument("partyId") { type = NavType.LongType }),
            ) { KhataPartyDetail() }
            composable(TopDest.Pnl.route) { PnlScreen() }
            composable(ROUTE_ASSISTANT) { AssistantScreen() }
            composable(ROUTE_SALE_ENTRY) {
                SaleEntryScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun ArthaBottomBar(navController: NavController, currentRoute: String?) {
    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Box(Modifier.fillMaxWidth()) {
        NavigationBar {
            BarItem(TopDest.Home, currentRoute, ::go)
            BarItem(TopDest.Inventory, currentRoute, ::go)
            Spacer(Modifier.weight(1f)) // center gap for the raised Assistant FAB
            BarItem(TopDest.Khata, currentRoute, ::go)
            BarItem(TopDest.Pnl, currentRoute, ::go)
        }
        FloatingActionButton(
            onClick = { go(ROUTE_ASSISTANT) },
            containerColor = BrandGold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(64.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Assistant")
        }
    }
}

@Composable
private fun RowScope.BarItem(
    dest: TopDest,
    currentRoute: String?,
    onGo: (String) -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == dest.route,
        onClick = { onGo(dest.route) },
        icon = { Icon(dest.icon, contentDescription = dest.label) },
        label = { Text(dest.label) },
    )
}
