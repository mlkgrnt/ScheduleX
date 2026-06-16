package com.schedulex.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.schedulex.data.db.AppDatabase
import com.schedulex.data.model.ScheduleSettings
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.data.model.getStartTime
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.util.Calendar

/**
 * 课前提醒调度器
 * 每次课程数据变化或设置变化时，为明天及今天的剩余课程设置提醒
 */
object ReminderScheduler {

    // 记录当前已设置的 requestCode，用于精确取消
    private val activeRequestCodes = mutableSetOf<Int>()

    /**
     * 为所有即将上课的课程设置提醒
     * 自动切到 IO 线程，避免 ANR
     */
    fun scheduleReminders(context: Context) {
        runBlocking {
            doSchedule(context)
        }
    }

    private suspend fun doSchedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 先取消已有提醒
        cancelAllReminders(context)

        val settings = try {
            loadScheduleSettings(context.scheduleDataStore)
        } catch (_: Exception) { return }

        if (settings.reminderMinutes <= 0) return

        val scheduleId = settings.activeScheduleId
        val db = AppDatabase.getDatabase(context)
        val allCourses = try {
            db.courseDao().getCoursesByScheduleSync(scheduleId)
        } catch (_: Exception) { return }
        val allSlots = try {
            db.timeSlotDao().getTimeSlotsByScheduleSync(scheduleId)
        } catch (_: Exception) { return }

        val currentWeek = calculateActualWeek(settings)
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val todayDow = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                       else cal.get(Calendar.DAY_OF_WEEK) - 1

        for (dayOffset in 0..1) {
            val targetDow = if (todayDow + dayOffset > 7) (todayDow + dayOffset) - 7 else todayDow + dayOffset
            val targetWeek = if (dayOffset == 1 && todayDow == 7) {
                (currentWeek + 1).coerceAtMost(settings.totalWeeks)
            } else currentWeek

            for (course in allCourses) {
                for (slot in allSlots.filter { it.courseId == course.id }) {
                    if (slot.day != targetDow) continue

                    val weeks = try {
                        JSONArray(slot.weeks).let { a -> (0 until a.length()).map { a.getInt(it) } }
                    } catch (_: Exception) { emptyList() }

                    if (targetWeek !in weeks) continue
                    if (slot.type.name == "ODD" && targetWeek % 2 == 0) continue
                    if (slot.type.name == "EVEN" && targetWeek % 2 == 1) continue

                    if (slot.startPeriod - 1 >= settings.periodStarts.size) continue
                    val startMinutes = settings.periodStarts[slot.startPeriod - 1]

                    if (dayOffset == 0 && nowMinutes >= startMinutes) continue

                    val reminderTime = startMinutes - settings.reminderMinutes
                    if (dayOffset == 0 && reminderTime <= nowMinutes) continue

                    val reminderCal = Calendar.getInstance().apply {
                        if (dayOffset == 1 || reminderTime < nowMinutes) {
                            add(Calendar.DAY_OF_YEAR, dayOffset)
                        }
                        set(Calendar.HOUR_OF_DAY, reminderTime / 60)
                        set(Calendar.MINUTE, reminderTime % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra(ReminderReceiver.EXTRA_COURSE_NAME, course.name)
                        putExtra(ReminderReceiver.EXTRA_LOCATION, slot.location ?: "")
                        putExtra(ReminderReceiver.EXTRA_MINUTES_BEFORE, settings.reminderMinutes)
                    }

                    val requestCode = (course.id * 1000 + targetDow * 100 + slot.startPeriod).toInt()
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            reminderCal.timeInMillis,
                            pendingIntent
                        )
                    } catch (_: SecurityException) {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            reminderCal.timeInMillis,
                            pendingIntent
                        )
                    }

                    activeRequestCodes.add(requestCode)
                }
            }
        }
    }

    /**
     * 取消所有提醒 — 只遍历已记录的 requestCode，不再暴力扫描 10 万次
     */
    fun cancelAllReminders(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (requestCode in activeRequestCodes) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        activeRequestCodes.clear()
    }
}
