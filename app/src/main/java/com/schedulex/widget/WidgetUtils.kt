package com.schedulex.widget

import android.content.Context
import com.schedulex.data.db.AppDatabase
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.Calendar

fun getDayOfWeekStr(calendar: Calendar): String {
    return when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        Calendar.SUNDAY -> "周日"
        else -> ""
    }
}

/**
 * 读取主题模式 0=跟随系统, 1=浅色, 2=深色
 */
fun getThemeMode(context: Context): Int {
    return try {
        val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
        settings.themeMode
    } catch (e: Exception) { 0 }
}

/**
 * 判断小组件是否应该使用深色主题
 */
fun isWidgetDarkMode(context: Context): Boolean {
    val mode = getThemeMode(context)
    return when (mode) {
        1 -> false   // 浅色
        2 -> true    // 深色
        else -> {    // 跟随系统
            val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}

/** 小组件深色模式背景 */
val WIDGET_BG_DARK = 0xFF1E1E1E.toInt()
/** 小组件浅色模式背景 */
val WIDGET_BG_LIGHT = 0xFFFFFFFF.toInt()
/** 深色模式标题文字 */
val WIDGET_TITLE_DARK = 0xFFE0E0E0.toInt()
/** 浅色模式标题文字 */
val WIDGET_TITLE_LIGHT = 0xFF1A1A1A.toInt()
/** 深色模式信息文字 */
val WIDGET_INFO_DARK = 0xFFBDBDBD.toInt()
/** 浅色模式信息文字 */
val WIDGET_INFO_LIGHT = 0xFF666666.toInt()
/** 深色模式课程名 */
val WIDGET_COURSE_NAME_DARK = 0xFFE0E0E0.toInt()
/** 浅色模式课程名 */
val WIDGET_COURSE_NAME_LIGHT = 0xFF1A1A1A.toInt()
/** 深色模式时间 */
val WIDGET_TIME_DARK = 0xFFBDBDBD.toInt()
/** 浅色模式时间 */
val WIDGET_TIME_LIGHT = 0xFF666666.toInt()
/** 深色模式地点 */
val WIDGET_LOCATION_DARK = 0xFF9E9E9E.toInt()
/** 浅色模式地点 */
val WIDGET_LOCATION_LIGHT = 0xFF999999.toInt()
/** 深色模式分割线 */
val WIDGET_DIVIDER_DARK = 0xFF424242.toInt()
/** 浅色模式分割线 */
val WIDGET_DIVIDER_LIGHT = 0xFFDDDDDD.toInt()

/**
 * 检查指定星期几在当前周是否有课程（不论是否已结束）
 * 用于小组件空状态文案判断
 */
fun hasCoursesForDay(context: Context, dayOfWeek: Int, week: Int): Boolean {
    return try {
        val db = AppDatabase.getDatabase(context)
        val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
        val allCourses = runBlocking { db.courseDao().getAllCoursesSync() }
        val allSlots = runBlocking { db.timeSlotDao().getAllTimeSlotsSync() }

        for (course in allCourses) {
            for (slot in allSlots.filter { it.courseId == course.id }) {
                if (slot.day != dayOfWeek) continue
                val weeks = try {
                    JSONArray(slot.weeks).let { a -> (0 until a.length()).map { a.getInt(it) } }
                } catch (_: Exception) { emptyList() }
                if (week !in weeks) continue
                if (slot.type.name == "ODD" && week % 2 == 0) continue
                if (slot.type.name == "EVEN" && week % 2 == 1) continue
                return true
            }
        }
        false
    } catch (_: Exception) {
        false
    }
}

/**
 * 刷新所有小组件（2×2 + 4×2）
 * 课程数据变更后调用，确保小组件即时反映最新状态
 */
fun refreshAllWidgets(context: Context) {
    WidgetWideReceiver.notifyAll(context)
    WidgetSingleReceiver.notifyAllUpdate(context)
}
