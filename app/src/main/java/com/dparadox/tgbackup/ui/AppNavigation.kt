package com.dparadox.tgbackup.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dparadox.tgbackup.ui.screens.DashboardScreen
import com.dparadox.tgbackup.ui.screens.FoldersScreen
import com.dparadox.tgbackup.ui.screens.HistoryScreen
import com.dparadox.tgbackup.ui.screens.SettingsScreen
import com.dparadox.tgbackup.ui.screens.TermsScreen
import com.dparadox.tgbackup.ui.theme.Background
import com.dparadox.tgbackup.ui.theme.Border
import com.dparadox.tgbackup.ui.theme.Primary
import com.dparadox.tgbackup.ui.theme.Surface
import com.dparadox.tgbackup.ui.theme.SurfaceAlt
import com.dparadox.tgbackup.ui.theme.TextMuted
import com.dparadox.tgbackup.ui.theme.TextPrimary

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissions)
    }

    data class Tab(val route: String, val label: String, val icon: ImageVector)
    val tabs = listOf(
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("folders",   "Status",    Icons.Default.Info),
        Tab("history",   "History",   Icons.Default.History),
        Tab("settings",  "Settings",  Icons.Default.Settings),
    )

    Scaffold(
        containerColor = Background,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            if (currentRoute != "terms") {
                NavigationBar(
                    containerColor = Surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.shadow(32.dp, ambientColor = Color.Black, spotColor = Color.Black)
                ) {
                    val currentDest = navBackStackEntry?.destination

                    tabs.forEach { tab ->
                        val isSelected = currentDest?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor       = Primary,
                                selectedTextColor       = Primary,
                                indicatorColor          = SurfaceAlt,
                                unselectedIconColor     = TextMuted,
                                unselectedTextColor     = TextMuted,
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (viewModel.settings.termsAccepted) "dashboard" else "terms",
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(spring()) + slideInVertically { it / 20 } },
            exitTransition = { fadeOut(spring()) + slideOutVertically { it / 20 } }
        ) {
            composable("terms") { 
                TermsScreen(viewModel) {
                    navController.navigate("dashboard") {
                        popUpTo("terms") { inclusive = true }
                    }
                }
            }
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("folders")   { FoldersScreen(viewModel) }
            composable("history")   { HistoryScreen(viewModel) }
            composable("settings")  { SettingsScreen(viewModel) }
        }
    }
}
