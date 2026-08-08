package com.dparadox.tgbackup.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.dparadox.tgbackup.gallery.ui.screens.*
import com.dparadox.tgbackup.ui.screens.*
import com.dparadox.tgbackup.ui.theme.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// FIX: Smooth cubic easing for enter/exit — replaces the bouncy spring that
//      made every screen transition feel rubbery and slow.
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private val EaseInCubic  = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)

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
    ) {}
    LaunchedEffect(Unit) { permissionLauncher.launch(permissions) }

    data class Tab(val route: String, val label: String, val icon: ImageVector)
    val tabs = listOf(
        Tab("gallery",   "Gallery",   Icons.Default.PhotoLibrary),
        Tab("dashboard", "Dashboard", Icons.Default.Dashboard),
        Tab("folders",   "Folders",   Icons.Default.FolderOpen),
        Tab("history",   "History",   Icons.Default.History),
        Tab("settings",  "Settings",  Icons.Default.Settings),
    )

    Scaffold(
        containerColor = Background,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute == "terms") return@Scaffold

            // Premium glass nav bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Background.copy(alpha = 0.97f))
                        )
                    )
            ) {
                NavigationBar(
                    containerColor = Surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
                                Box(contentAlignment = Alignment.Center) {
                                    // Glow behind selected icon
                                    if (isSelected) {
                                        Box(
                                            Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(PrimaryDim)
                                        )
                                    }
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 0.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor       = Primary,
                                selectedTextColor       = Primary,
                                indicatorColor          = Color.Transparent,
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
            navController    = navController,
            startDestination = if (viewModel.settings.termsAccepted) "dashboard" else "terms",
            modifier         = Modifier.padding(innerPadding),
            // FIX: Replaced Spring.DampingRatioMediumBouncy with smooth EaseOutCubic tween.
            //      The spring caused a rubbery bounce on every screen change and felt slow
            //      because the spring always overshoots before settling.
            enterTransition  = {
                fadeIn(tween(180, easing = LinearEasing)) +
                slideInVertically(tween(220, easing = EaseOutCubic)) { it / 14 }
            },
            exitTransition   = {
                fadeOut(tween(140, easing = LinearEasing)) +
                slideOutVertically(tween(160, easing = EaseInCubic)) { -(it / 18) }
            },
            popEnterTransition  = {
                fadeIn(tween(180, easing = LinearEasing)) +
                slideInVertically(tween(220, easing = EaseOutCubic)) { -(it / 14) }
            },
            popExitTransition   = {
                fadeOut(tween(140, easing = LinearEasing)) +
                slideOutVertically(tween(160, easing = EaseInCubic)) { it / 18 }
            }
        ) {
            composable("terms") {
                TermsScreen(viewModel) {
                    navController.navigate("dashboard") {
                        popUpTo("terms") { inclusive = true }
                    }
                }
            }
            composable("gallery") {
                GalleryContainer(
                    onOpenAlbum = { bucketId, bucketName ->
                        val encodedId = URLEncoder.encode(bucketId, StandardCharsets.UTF_8.toString())
                        val encodedName = URLEncoder.encode(bucketName, StandardCharsets.UTF_8.toString())
                        navController.navigate("album_detail/$encodedId/$encodedName")
                    },
                    onOpenMedia = { mediaId ->
                        navController.navigate("media_viewer/$mediaId/timeline/null")
                    },
                    onOpenFavorites = {
                        navController.navigate("special_gallery/favorites")
                    },
                    onOpenTrash = {
                        navController.navigate("special_gallery/trash")
                    }
                )
            }
            composable("album_detail/{bucketId}/{bucketName}") { backStackEntry ->
                val bucketId = backStackEntry.arguments?.getString("bucketId") ?: ""
                val bucketName = backStackEntry.arguments?.getString("bucketName") ?: "Album"
                AlbumDetailScreen(
                    bucketId = bucketId,
                    bucketName = java.net.URLDecoder.decode(bucketName, StandardCharsets.UTF_8.toString()),
                    onBack = { navController.popBackStack() },
                    onOpenMedia = { mediaId ->
                        navController.navigate("media_viewer/$mediaId/album/$bucketId")
                    }
                )
            }
            composable("special_gallery/{type}") { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "favorites"
                SpecialGalleryScreen(
                    type = type,
                    onBack = { navController.popBackStack() },
                    onOpenMedia = { mediaId, contextType ->
                        navController.navigate("media_viewer/$mediaId/$contextType/null")
                    }
                )
            }
            composable("media_viewer/{mediaId}/{type}/{bucketId}") { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString("mediaId")?.toLongOrNull() ?: 0L
                val type = backStackEntry.arguments?.getString("type") ?: "timeline"
                val bucketId = backStackEntry.arguments?.getString("bucketId").takeIf { it != "null" }
                MediaViewerScreen(
                    initialMediaId = mediaId,
                    contextType = type,
                    bucketId = bucketId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("dashboard") { DashboardScreen(viewModel) }
            composable("folders") {
                FoldersScreen(viewModel) { folderPath ->
                    val encoded = URLEncoder.encode(folderPath, StandardCharsets.UTF_8.toString())
                    navController.navigate("folder_detail/$encoded")
                }
            }
            composable("folder_detail/{folderPath}") { backStackEntry ->
                val folderPath = backStackEntry.arguments?.getString("folderPath") ?: ""
                FolderDetailScreen(viewModel, folderPath) { navController.popBackStack() }
            }
            composable("history")  { HistoryScreen(viewModel) }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}
