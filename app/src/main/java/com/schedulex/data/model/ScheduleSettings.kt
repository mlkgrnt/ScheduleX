package com.schedulex.data.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.Calendar

val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_settings")

data class ScheduleSettings(
    val currentWeek: Int = 1,
    val totalWeeks: Int = 20,
    val startDate: String = "",
    val periodStarts: List<Int> = defaultPeriodStarts(),
    val totalPeriods: Int = 15,
    val useSameDuration: Boolean = true,  // 是否每节课时长相同
    val classDuration: Int = 45,          // 统一时长（useSameDuration=true时使用）
    val periodDurations: List<Int> = List(15) { 45 },  // 每节课独立时长（useSameDuration=false时使用）
    val weekAnchorDate: Long = 0L,  // 锚点：设置currentWeek时那周周一的时间戳
    val themeMode: Int = 0,  // 0=跟随系统, 1=浅色, 2=深色
    val reminderMinutes: Int = 0,  // 课前提醒分钟数，0=关闭
    val activeScheduleId: String = "default"  // 当前活跃的课表ID
)

object SchedulePrefs {
    val CURRENT_WEEK = intPreferencesKey("current_week")
    val TOTAL_WEEKS = intPreferencesKey("total_weeks")
    val START_DATE = stringPreferencesKey("start_date")
    val PERIOD_STARTS = stringPreferencesKey("period_starts")
    val TOTAL_PERIODS = intPreferencesKey("total_periods")
    val USE_SAME_DURATION = booleanPreferencesKey("use_same_duration")
    val CLASS_DURATION = intPreferencesKey("class_duration")
    val PERIOD_DURATIONS = stringPreferencesKey("period_durations")
    val WEEK_ANCHOR_DATE = longPreferencesKey("week_anchor_date")
    val THEME_MODE = intPreferencesKey("theme_mode")
    val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    val ACTIVE_SCHEDULE_ID = stringPreferencesKey("active_schedule_id")
    val SCHEDULE_NAMES = stringPreferencesKey("schedule_names") // JSON: {"default":"我的课表","id2":"name2"}
}

fun defaultPeriodStarts(): List<Int> {
    // 默认时间表（当前使用的）
    return listOf(
        480,  // 第1节 08:00
        535,  // 第2节 08:55
        600,  // 第3节 10:00
        655,  // 第4节 10:55
        710,  // 第5节 11:50
        840,  // 第6节 14:00
        895,  // 第7节 14:55
        960,  // 第8节 16:00
        1015, // 第9节 16:55
        1070, // 第10节 17:50
        1160, // 第11节 19:20
        1215, // 第12节 20:15
        1270  // 第13节 21:10
    )
}

/**
 * 获取本周一的日期（0点时间戳）
 */
fun getMondayOfWeek(calendar: Calendar = Calendar.getInstance()): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = calendar.timeInMillis
    // 设置为本周一 00:00:00
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val diff = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
    cal.add(Calendar.DAY_OF_MONTH, diff)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * 根据锚点计算实际当前周
 * 如果锚点为0，说明还没设置过，返回原始currentWeek
 */
fun calculateActualWeek(settings: ScheduleSettings): Int {
    if (settings.weekAnchorDate == 0L) return settings.currentWeek
    
    val anchorMonday = settings.weekAnchorDate
    val currentMonday = getMondayOfWeek()
    
    val diffMs = currentMonday - anchorMonday
    val diffWeeks = (diffMs / (7 * 24 * 60 * 60 * 1000L)).toInt()
    
    // 锚点 = 开学第一周的周一，所以当前周 = 1 + 已过周数
    val actualWeek = 1 + diffWeeks
    return actualWeek.coerceIn(1, settings.totalWeeks)
}

suspend fun loadScheduleSettings(dataStore: DataStore<Preferences>): ScheduleSettings {
    val prefs = dataStore.data.first()
    val periodStartsStr = prefs[SchedulePrefs.PERIOD_STARTS] ?: ""
    val periodDurationsStr = prefs[SchedulePrefs.PERIOD_DURATIONS] ?: ""
    
    val periodStarts = if (periodStartsStr.isNotEmpty()) {
        periodStartsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    } else {
        defaultPeriodStarts()
    }
    
    val periodDurations = if (periodDurationsStr.isNotEmpty()) {
        periodDurationsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    } else {
        List(15) { 45 }
    }
    
    return ScheduleSettings(
        currentWeek = prefs[SchedulePrefs.CURRENT_WEEK] ?: 1,
        totalWeeks = prefs[SchedulePrefs.TOTAL_WEEKS] ?: 20,
        startDate = prefs[SchedulePrefs.START_DATE] ?: "",
        periodStarts = periodStarts,
        totalPeriods = prefs[SchedulePrefs.TOTAL_PERIODS] ?: 15,
        useSameDuration = prefs[SchedulePrefs.USE_SAME_DURATION] ?: true,
        classDuration = prefs[SchedulePrefs.CLASS_DURATION] ?: 45,
        periodDurations = periodDurations,
        weekAnchorDate = prefs[SchedulePrefs.WEEK_ANCHOR_DATE] ?: 0L,
        themeMode = prefs[SchedulePrefs.THEME_MODE] ?: 0,
        reminderMinutes = prefs[SchedulePrefs.REMINDER_MINUTES] ?: 0,
        activeScheduleId = prefs[SchedulePrefs.ACTIVE_SCHEDULE_ID] ?: "default"
    )
}

suspend fun saveScheduleSettings(dataStore: DataStore<Preferences>, settings: ScheduleSettings) {
    dataStore.edit { prefs ->
        prefs[SchedulePrefs.CURRENT_WEEK] = settings.currentWeek
        prefs[SchedulePrefs.TOTAL_WEEKS] = settings.totalWeeks
        prefs[SchedulePrefs.START_DATE] = settings.startDate
        prefs[SchedulePrefs.PERIOD_STARTS] = settings.periodStarts.joinToString(",")
        prefs[SchedulePrefs.TOTAL_PERIODS] = settings.totalPeriods
        prefs[SchedulePrefs.USE_SAME_DURATION] = settings.useSameDuration
        prefs[SchedulePrefs.CLASS_DURATION] = settings.classDuration
        prefs[SchedulePrefs.PERIOD_DURATIONS] = settings.periodDurations.joinToString(",")
        prefs[SchedulePrefs.WEEK_ANCHOR_DATE] = settings.weekAnchorDate
        prefs[SchedulePrefs.THEME_MODE] = settings.themeMode
        prefs[SchedulePrefs.REMINDER_MINUTES] = settings.reminderMinutes
        prefs[SchedulePrefs.ACTIVE_SCHEDULE_ID] = settings.activeScheduleId
    }
}

/**
 * 保存设置并自动更新锚点
 * 当用户改变currentWeek时，自动更新weekAnchorDate为那周的周一
 */
suspend fun saveScheduleSettingsWithAnchor(dataStore: DataStore<Preferences>, settings: ScheduleSettings) {
    // 锚点 = 开学第一周的周一 = 当前周一 - (currentWeek - 1) 周
    val currentMonday = getMondayOfWeek()
    val weekMs = 7 * 24 * 60 * 60 * 1000L
    val newAnchor = currentMonday - ((settings.currentWeek - 1).toLong() * weekMs)
    val newSettings = settings.copy(weekAnchorDate = newAnchor)
    saveScheduleSettings(dataStore, newSettings)
}

fun getStartTime(period: Int, settings: ScheduleSettings): String {
    if (period < 1 || period > settings.periodStarts.size) return ""
    val minutes = settings.periodStarts[period - 1]
    val h = minutes / 60
    val m = minutes % 60
    return String.format("%02d:%02d", h, m)
}

fun getEndTime(period: Int, settings: ScheduleSettings): String {
    if (period < 1 || period > settings.periodStarts.size) return ""
    val startMinutes = settings.periodStarts[period - 1]
    val duration = if (settings.useSameDuration) {
        settings.classDuration
    } else {
        settings.periodDurations.getOrElse(period - 1) { 45 }
    }
    val endMinutes = startMinutes + duration
    val h = endMinutes / 60
    val m = endMinutes % 60
    return String.format("%02d:%02d", h, m)
}

/**
 * 获取课表名映射。默认 {"default": "我的课表"}
 */
suspend fun loadScheduleNames(dataStore: DataStore<Preferences>): Map<String, String> {
    val prefs = dataStore.data.first()
    val json = prefs[SchedulePrefs.SCHEDULE_NAMES] ?: return mapOf("default" to "我的课表")
    return try {
        val obj = org.json.JSONObject(json)
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { map[it] = obj.getString(it) }
        if (map.isEmpty()) mapOf("default" to "我的课表") else map
    } catch (_: Exception) {
        mapOf("default" to "我的课表")
    }
}

/**
 * 保存课表名映射
 */
suspend fun saveScheduleNames(dataStore: DataStore<Preferences>, names: Map<String, String>) {
    val obj = org.json.JSONObject()
    names.forEach { (k, v) -> obj.put(k, v) }
    dataStore.edit { prefs ->
        prefs[SchedulePrefs.SCHEDULE_NAMES] = obj.toString()
    }
}

/**
 * 新增课表
 */
suspend fun createSchedule(dataStore: DataStore<Preferences>, id: String, name: String) {
    val names = loadScheduleNames(dataStore).toMutableMap()
    names[id] = name
    saveScheduleNames(dataStore, names)
    // 切换到新课表
    val settings = loadScheduleSettings(dataStore)
    saveScheduleSettings(dataStore, settings.copy(activeScheduleId = id))
}

/**
 * 删除课表（不可删除最后一个）
 */
suspend fun deleteScheduleProfile(dataStore: DataStore<Preferences>, scheduleId: String): Boolean {
    val names = loadScheduleNames(dataStore).toMutableMap()
    if (names.size <= 1) return false
    names.remove(scheduleId)
    saveScheduleNames(dataStore, names)
    // 如果删的是当前活跃的，切回 default
    val settings = loadScheduleSettings(dataStore)
    if (settings.activeScheduleId == scheduleId) {
        saveScheduleSettings(dataStore, settings.copy(activeScheduleId = names.keys.first()))
    }
    return true
}
