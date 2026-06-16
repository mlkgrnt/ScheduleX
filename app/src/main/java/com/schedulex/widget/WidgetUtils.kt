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

/** 获取当前活跃课表ID */
fun getActiveScheduleId(context: Context): String {
    return try {
        val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
        settings.activeScheduleId
    } catch (_: Exception) { "default" }
}

fun getThemeMode(context: Context): Int {
    return try {
        val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
        settings.themeMode
    } catch (e: Exception) { 0 }
}

fun isWidgetDarkMode(context: Context): Boolean {
    val mode = getThemeMode(context)
    return when (mode) {
        1 -> false
        2 -> true
        else -> {
            val nightModeFlags = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
    }
}

val WIDGET_BG_DARK = 0xFF1E1E1E.toInt()
val WIDGET_BG_LIGHT = 0xFFFFFFFF.toInt()
val WIDGET_TITLE_DARK = 0xFFE0E0E0.toInt()
val WIDGET_TITLE_LIGHT = 0xFF1A1A1A.toInt()
val WIDGET_INFO_DARK = 0xFFBDBDBD.toInt()
val WIDGET_INFO_LIGHT = 0xFF666666.toInt()
val WIDGET_COURSE_NAME_DARK = 0xFFE0E0E0.toInt()
val WIDGET_COURSE_NAME_LIGHT = 0xFF1A1A1A.toInt()
val WIDGET_TIME_DARK = 0xFFBDBDBD.toInt()
val WIDGET_TIME_LIGHT = 0xFF666666.toInt()
val WIDGET_LOCATION_DARK = 0xFF9E9E9E.toInt()
val WIDGET_LOCATION_LIGHT = 0xFF999999.toInt()
val WIDGET_DIVIDER_DARK = 0xFF424242.toInt()
val WIDGET_DIVIDER_LIGHT = 0xFFDDDDDD.toInt()

/**
 * 检查指定星期几在当前周是否有课程
 */
fun hasCoursesForDay(context: Context, dayOfWeek: Int, week: Int): Boolean {
    return try {
        val db = AppDatabase.getDatabase(context)
        val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
        val scheduleId = settings.activeScheduleId
        val allCourses = runBlocking { db.courseDao().getCoursesByScheduleSync(scheduleId) }
        val allSlots = runBlocking { db.timeSlotDao().getTimeSlotsByScheduleSync(scheduleId) }

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

fun refreshAllWidgets(context: Context) {
    WidgetWideReceiver.notifyAll(context)
    WidgetSingleReceiver.notifyAllUpdate(context)
    try {
        val settings = kotlinx.coroutines.runBlocking {
            com.schedulex.data.model.loadScheduleSettings(context.scheduleDataStore)
        }
        if (settings.reminderMinutes > 0) {
            com.schedulex.reminder.ReminderScheduler.scheduleReminders(context)
        }
    } catch (_: Exception) {}
}
