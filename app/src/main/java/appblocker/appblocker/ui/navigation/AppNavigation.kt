package appblocker.appblocker.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import appblocker.appblocker.service.AppMonitorService
import appblocker.appblocker.ui.screens.AppDetailScreen
import appblocker.appblocker.ui.screens.AppUsageScreen
import appblocker.appblocker.ui.screens.MainScreen
import appblocker.appblocker.ui.screens.ReportsScreen
import appblocker.appblocker.ui.screens.SettingsScreen
import appblocker.appblocker.ui.screens.WeekDetailScreen
import appblocker.appblocker.ui.viewmodel.AppDetailViewModel
import appblocker.appblocker.ui.viewmodel.AppUsageViewModel
import appblocker.appblocker.ui.viewmodel.MainViewModel
import appblocker.appblocker.ui.viewmodel.ReportsViewModel
import appblocker.appblocker.ui.viewmodel.WeekDetailViewModel

@Composable
fun AppNavigation(
    mainVm: MainViewModel,
    usageVm: AppUsageViewModel,
    reportsVm: ReportsViewModel,
    hasUsagePermission: () -> Boolean,
    hasOverlayPermission: () -> Boolean,
    hasAccessibility: () -> Boolean,
    requestUsagePermission: () -> Unit,
    requestOverlay: () -> Unit,
    requestAccessibility: () -> Unit,
    startService: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Bottom bar only on top-level destinations
    val showBottomBar = currentRoute in Routes.TOP_LEVEL

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.BLOCKS,
                        onClick  = { navController.navigateTopLevel(Routes.BLOCKS) },
                        icon     = { Icon(Icons.Default.Lock, null) },
                        label    = { Text("Blocks") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.USAGE,
                        onClick  = { navController.navigateTopLevel(Routes.USAGE) },
                        icon     = { Icon(Icons.Default.Timeline, null) },
                        label    = { Text("Usage") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.REPORTS,
                        onClick  = { navController.navigateTopLevel(Routes.REPORTS) },
                        icon     = { Icon(Icons.Default.BarChart, null) },
                        label    = { Text("Reports") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick  = { navController.navigateTopLevel(Routes.SETTINGS) },
                        icon     = { Icon(Icons.Default.Settings, null) },
                        label    = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.BLOCKS,
            modifier         = Modifier.padding(innerPadding),
            enterTransition  = { fadeIn() },
            exitTransition   = { fadeOut() },
            popEnterTransition  = { fadeIn() },
            popExitTransition   = { fadeOut() }
        ) {

            // ── Top-level ────────────────────────────────────────────────────

            composable(Routes.BLOCKS) {
                MainScreen(
                    vm                   = mainVm,
                    hasUsagePermission   = hasUsagePermission,
                    hasOverlayPermission = hasOverlayPermission,
                    hasAccessibility     = hasAccessibility,
                    requestUsagePermission = requestUsagePermission,
                    requestOverlay       = requestOverlay,
                    requestAccessibility = requestAccessibility,
                    startService         = startService,
                    onOpenAppUsage       = { pkg -> navController.navigate(Routes.appDetail(pkg)) }
                )
            }

            composable(Routes.USAGE) {
                AppUsageScreen(
                    vm        = usageVm,
                    onOpenApp = { pkg -> navController.navigate(Routes.appDetail(pkg)) }
                )
            }

            composable(Routes.REPORTS) {
                ReportsScreen(
                    vm          = reportsVm,
                    onOpenWeek  = { weeksAgo -> navController.navigate(Routes.weekDetail(weeksAgo)) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }

            // ── Full-screen detail (no bottom bar) ───────────────────────────

            composable(
                route     = Routes.APP_DETAIL,
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) {
                val vm: AppDetailViewModel = viewModel()
                AppDetailScreen(vm = vm, onBack = { navController.popBackStack() })
            }

            composable(
                route     = Routes.WEEK_DETAIL,
                arguments = listOf(navArgument("weeksAgo") { type = NavType.IntType })
            ) {
                val vm: WeekDetailViewModel = viewModel()
                WeekDetailScreen(
                    vm        = vm,
                    onBack    = { navController.popBackStack() },
                    onOpenApp = { pkg -> navController.navigate(Routes.appDetail(pkg)) }
                )
            }
        }
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

