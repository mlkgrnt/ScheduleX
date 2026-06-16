package com.schedulex.data.repository

import com.schedulex.data.db.CourseDao
import com.schedulex.data.db.TimeSlotDao
import com.schedulex.data.db.TimeSlotWithCourseName
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot
import com.schedulex.data.model.WeekType
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

class CourseRepository(
    private val courseDao: CourseDao,
    private val timeSlotDao: TimeSlotDao
) {
    fun getAllCourses(): Flow<List<Course>> = courseDao.getAllCourses()
    fun getCoursesBySchedule(scheduleId: String): Flow<List<Course>> = courseDao.getCoursesBySchedule(scheduleId)

    suspend fun getCourseById(id: Long): Course? = courseDao.getCourseById(id)
    suspend fun getCoursesByScheduleSync(scheduleId: String): List<Course> = courseDao.getCoursesByScheduleSync(scheduleId)

    suspend fun insertCourse(course: Course): Long = courseDao.insertCourse(course)
    suspend fun updateCourse(course: Course) = courseDao.updateCourse(course)
    suspend fun deleteCourse(course: Course) = courseDao.deleteCourse(course)
    suspend fun deleteAllCourses() = courseDao.deleteAllCourses()

    fun getTimeSlotsForCourse(courseId: Long): Flow<List<TimeSlot>> =
        timeSlotDao.getTimeSlotsForCourse(courseId)

    fun getAllTimeSlots(): Flow<List<TimeSlot>> = timeSlotDao.getAllTimeSlots()
    fun getTimeSlotsBySchedule(scheduleId: String): Flow<List<TimeSlot>> = timeSlotDao.getTimeSlotsBySchedule(scheduleId)

    suspend fun getTimeSlotsByScheduleSync(scheduleId: String): List<TimeSlot> = timeSlotDao.getTimeSlotsByScheduleSync(scheduleId)

    suspend fun insertTimeSlot(timeSlot: TimeSlot): Long = timeSlotDao.insertTimeSlot(timeSlot)
    suspend fun insertTimeSlots(timeSlots: List<TimeSlot>) = timeSlotDao.insertTimeSlots(timeSlots)
    suspend fun deleteTimeSlotsForCourse(courseId: Long) = timeSlotDao.deleteTimeSlotsForCourse(courseId)
    suspend fun deleteAllTimeSlots() = timeSlotDao.deleteAllTimeSlots()

    /** 删除整个课表 */
    suspend fun deleteSchedule(scheduleId: String) {
        timeSlotDao.deleteTimeSlotsBySchedule(scheduleId)
        courseDao.deleteCoursesBySchedule(scheduleId)
    }

    /** 清除当前课表数据 */
    suspend fun clearScheduleData(scheduleId: String) {
        timeSlotDao.deleteTimeSlotsBySchedule(scheduleId)
        courseDao.deleteCoursesBySchedule(scheduleId)
    }

    suspend fun clearAllData() {
        timeSlotDao.deleteAllTimeSlots()
        courseDao.deleteAllCourses()
    }

    /** 获取所有已存在的课表ID */
    suspend fun getAllScheduleIds(): List<String> = courseDao.getAllScheduleIds()

    suspend fun insertCourseWithTimeSlots(course: Course, timeSlots: List<TimeSlot>): Long {
        val courseId = courseDao.insertCourse(course)
        val slotsWithCourseId = timeSlots.map { it.copy(courseId = courseId) }
        timeSlotDao.insertTimeSlots(slotsWithCourseId)
        return courseId
    }

    /**
     * 检查新时间安排是否与已有课程冲突
     */
    suspend fun findConflict(
        newSlot: PendingSlotInfo,
        excludeCourseId: Long? = null,
        scheduleId: String = "default"
    ): String? {
        val allSlots = timeSlotDao.getTimeSlotsWithCourseNameBySchedule(scheduleId)
        val newWeeks = expandWeeks(newSlot.weeks, newSlot.weekType)

        for (existing in allSlots) {
            if (excludeCourseId != null && existing.courseId == excludeCourseId) continue
            if (existing.day != newSlot.day) continue
            if (newSlot.startPeriod > existing.endPeriod || newSlot.endPeriod < existing.startPeriod) continue

            val existingWeeks = expandWeeks(parseWeeks(existing.weeks), WeekType.valueOf(existing.type))
            if (!newWeeks.any { it in existingWeeks }) continue

            val periodText = if (existing.startPeriod == existing.endPeriod)
                "第${existing.startPeriod}节"
            else "第${existing.startPeriod}-${existing.endPeriod}节"
            return "${existing.courseName}（$periodText）"
        }
        return null
    }

    private fun parseWeeks(json: String): List<Int> {
        return try { JSONArray(json).let { a -> (0 until a.length()).map { a.getInt(it) } } }
        catch (_: Exception) { emptyList() }
    }

    private fun expandWeeks(weeks: List<Int>, type: WeekType): List<Int> {
        return when (type) {
            WeekType.ALL -> weeks
            WeekType.ODD -> weeks.filter { it % 2 == 1 }
            WeekType.EVEN -> weeks.filter { it % 2 == 0 }
        }
    }
}

data class PendingSlotInfo(
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weekType: WeekType
)
