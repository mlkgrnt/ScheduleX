package com.schedulex.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.schedulex.R
import com.schedulex.data.db.AppDatabase
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.data.model.getEndTime
import com.schedulex.data.model.getStartTime
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import org.json.JSONArray

/**
 * 2×2 今日课程列表Service
 */
class WidgetCourseListService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetCourseListFactory(applicationContext)
    }
}

class WidgetCourseListFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var courses: List<CourseItem> = emptyList()
    private var isDark: Boolean = false

    override fun onCreate() {}
    override fun onDataSetChanged() {
        isDark = isWidgetDarkMode(context)
        courses = loadCourses()
    }
    override fun onDestroy() { courses = emptyList() }
    override fun getCount() = courses.size
    override fun getViewTypeCount() = 1
    override fun getItemId(pos: Int) = pos.toLong()
    override fun hasStableIds() = true

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_course_item)
        if (position >= courses.size) return views

        val c = courses[position]
        views.setTextViewText(R.id.item_course_name, c.name)
        views.setTextViewText(R.id.item_course_time, c.time)
        views.setTextViewText(R.id.item_course_location, c.location)
        views.setInt(R.id.item_color_bar, "setBackgroundColor", c.color)

        views.setTextColor(R.id.item_course_name, if (isDark) WIDGET_COURSE_NAME_DARK else WIDGET_COURSE_NAME_LIGHT)
        views.setTextColor(R.id.item_course_time, if (isDark) WIDGET_TIME_DARK else WIDGET_TIME_LIGHT)
        views.setTextColor(R.id.item_course_location, if (isDark) WIDGET_LOCATION_DARK else WIDGET_LOCATION_LIGHT)
        views.setInt(R.id.item_root, "setBackgroundColor", if (isDark) WIDGET_BG_DARK else WIDGET_BG_LIGHT)

        // 点击列表项打开APP（配合 setPendingIntentTemplate 使用）
        val fillInIntent = Intent().apply {
            putExtra("click", true)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

        return views
    }

    override fun getLoadingView() = RemoteViews(context.packageName, R.layout.widget_course_item)

    private fun loadCourses(): List<CourseItem> {
        return try {
            val db = AppDatabase.getDatabase(context)
            val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
            val currentWeek = calculateActualWeek(settings)

            val cal = Calendar.getInstance()
            val todayDow = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                           else cal.get(Calendar.DAY_OF_WEEK) - 1

            val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val allCourses = runBlocking { db.courseDao().getAllCoursesSync() }
            val allSlots = runBlocking { db.timeSlotDao().getAllTimeSlotsSync() }

            val result = mutableListOf<CourseItem>()
            for (course in allCourses) {
                val colorInt = try {
                    android.graphics.Color.parseColor(course.color)
                } catch (_: Exception) { 0xFF4FC3F7.toInt() }

                for (slot in allSlots.filter { it.courseId == course.id }) {
                    if (slot.day != todayDow) continue

                    val weeks = parseWeeks(slot.weeks)
                    if (currentWeek !in weeks) continue
                    if (slot.type.name == "ODD" && currentWeek % 2 == 0) continue
                    if (slot.type.name == "EVEN" && currentWeek % 2 == 1) continue

                    // 获取真实开始/结束时间
                    val start = getStartTime(slot.startPeriod, settings)
                    val end = getEndTime(slot.endPeriod, settings)

                    // 用结束时间判断是否已下课 → 不显示
                    if (end.isNotEmpty()) {
                        val eParts = end.split(":")
                        if (eParts.size == 2) {
                            val eMin = (eParts[0].toIntOrNull() ?: 0) * 60 + (eParts[1].toIntOrNull() ?: 0)
                            if (nowMinutes >= eMin) continue  // 已下课，跳过
                        }
                    }

                    // 判断是否正在上课（当前时间在 start 和 end 之间）
                    var inClass = false
                    if (start.isNotEmpty() && end.isNotEmpty()) {
                        val sParts = start.split(":")
                        val eParts = end.split(":")
                        if (sParts.size == 2 && eParts.size == 2) {
                            val sMin = (sParts[0].toIntOrNull() ?: 0) * 60 + (sParts[1].toIntOrNull() ?: 0)
                            val eMin = (eParts[0].toIntOrNull() ?: 0) * 60 + (eParts[1].toIntOrNull() ?: 0)
                            if (nowMinutes in sMin until eMin) inClass = true
                        }
                    }

                    val time = if (start.isNotEmpty()) {
                        if (end.isNotEmpty()) "$start-$end" else start
                    } else "第${slot.startPeriod}-${slot.endPeriod}节"

                    val displayName = if (inClass) "${course.name}(上课中)" else course.name
                    result.add(CourseItem(displayName, time, slot.location ?: "", slot.startPeriod, colorInt))
                }
            }
            result.sortedBy { it.startNode }
        } catch (e: Exception) {
            android.util.Log.e("WidgetCourseList", "Error", e)
            emptyList()
        }
    }

    private fun parseWeeks(json: String): List<Int> {
        return try { JSONArray(json).let { a -> (0 until a.length()).map { a.getInt(it) } } }
        catch (e: Exception) { emptyList() }
    }
}
