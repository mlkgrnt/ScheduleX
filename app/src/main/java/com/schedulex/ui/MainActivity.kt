package com.schedulex.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.schedulex.ui.home.HomeScreen
import com.schedulex.ui.course.AddCourseScreen
import com.schedulex.ui.course.EditCourseScreen
import com.schedulex.ui.course.CourseListScreen
import com.schedulex.ui.settings.SettingsScreen
import com.schedulex.ui.settings.LlmSettingsScreen
import com.schedulex.ui.settings.TimeSettingsScreen
import com.schedulex.ui.import_.WebViewLoginScreen
import com.schedulex.ui.import_.PdfImportScreen
import com.schedulex.ui.import_.ImportPreviewScreen
import com.schedulex.ui.import_.ScreenshotImportScreen
import com.schedulex.ui.navigation.Screen
import com.schedulex.ui.navigation.bottomNavItems
import com.schedulex.ui.theme.ScheduleXTheme
import com.schedulex.data.model.ScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import com.schedulex.data.model.loadScheduleSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = this
            var settings by remember { mutableStateOf(ScheduleSettings()) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                settings = loadScheduleSettings(context.scheduleDataStore)
                scope.launch {
                    context.scheduleDataStore.data.collectLatest { prefs ->
                        settings = loadScheduleSettings(context.scheduleDataStore)
                    }
                }
            }

            ScheduleXTheme(themeMode = settings.themeMode) {
                ScheduleXApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleXApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.any { dest ->
        bottomNavItems.any { it.route == dest.route }
    } == true

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToAddCourse = { navController.navigate(Screen.AddCourse.route) },
                    onNavigateToImport = { navController.navigate(Screen.ImportSchedule.route) },
                    onNavigateToScreenshotImport = { navController.navigate(Screen.ScreenshotImport.route) },
                    onNavigateToPdfImport = { navController.navigate(Screen.PdfImport.route) },
                    onNavigateToEditCourse = { courseId ->
                        navController.navigate(Screen.EditCourse.createRoute(courseId))
                    }
                )
            }
            composable(Screen.CourseList.route) {
                CourseListScreen(
                    onNavigateToEdit = { courseId ->
                        navController.navigate(Screen.EditCourse.createRoute(courseId))
                    },
                    onNavigateToAdd = { navController.navigate(Screen.AddCourse.route) }
                )
            }
            composable(Screen.AddCourse.route) {
                AddCourseScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                Screen.EditCourse.route,
                arguments = listOf(navArgument("courseId") { type = NavType.LongType })
            ) { backStackEntry ->
                val courseId = backStackEntry.arguments?.getLong("courseId") ?: 0L
                EditCourseScreen(
                    courseId = courseId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLlmSettings = { navController.navigate(Screen.LlmSettings.route) },
                    onNavigateToImport = { navController.navigate(Screen.ImportSchedule.route) },
                    onNavigateToTimeSettings = { navController.navigate(Screen.TimeSettings.route) },
                    onNavigateToScreenshotImport = { navController.navigate(Screen.ScreenshotImport.route) },
                    onNavigateToPdfImport = { navController.navigate(Screen.PdfImport.route) }
                )
            }
            composable(Screen.LlmSettings.route) {
                LlmSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.TimeSettings.route) {
                TimeSettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.ImportSchedule.route) {
                WebViewLoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPreview = { navController.navigate(Screen.ImportPreview.route) }
                )
            }
            composable(Screen.ImportPreview.route) {
                ImportPreviewScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onImportComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.ScreenshotImport.route) {
                ScreenshotImportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPreview = {
                        navController.navigate(Screen.ImportPreview.route)
                    }
                )
            }
            composable(Screen.PdfImport.route) {
                PdfImportScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPreview = {
                        navController.navigate(Screen.ImportPreview.route)
                    }
                )
            }
        }
    }
}
