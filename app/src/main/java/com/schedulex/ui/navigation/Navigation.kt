package com.schedulex.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CourseList : Screen("course_list")
    data object AddCourse : Screen("add_course")
    data object EditCourse : Screen("edit_course/{courseId}") {
        fun createRoute(courseId: Long) = "edit_course/$courseId"
    }
    data object Settings : Screen("settings")
    data object LlmSettings : Screen("llm_settings")
    data object TimeSettings : Screen("time_settings")
    data object ImportSchedule : Screen("import_schedule")
    data object ImportPreview : Screen("import_preview")
    data object ScreenshotImport : Screen("screenshot_import")
    data object WebViewLogin : Screen("webview_login")
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    BottomNavItem("课表", Icons.Default.CalendarToday, Screen.Home.route),
    BottomNavItem("课程", Icons.Default.List, Screen.CourseList.route),
    BottomNavItem("设置", Icons.Default.Settings, Screen.Settings.route)
)
