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

    suspend fun getCourseById(id: Long): Course? = courseDao.getCourseById(id)

    suspend fun insertCourse(course: Course): Long = courseDao.insertCourse(course)

    suspend fun updateCourse(course: Course) = courseDao.updateCourse(course)

    suspend fun deleteCourse(course: Course) = courseDao.deleteCourse(course)

    suspend fun deleteAllCourses() = courseDao.deleteAllCourses()

    fun getTimeSlotsForCourse(courseId: Long): Flow<List<TimeSlot>> =
        timeSlotDao.getTimeSlotsForCourse(courseId)

    fun getAllTimeSlots(): Flow<List<TimeSlot>> = timeSlotDao.getAllTimeSlots()

    suspend fun insertTimeSlot(timeSlot: TimeSlot): Long = timeSlotDao.insertTimeSlot(timeSlot)

    suspend fun insertTimeSlots(timeSlots: List<TimeSlot>) = timeSlotDao.insertTimeSlots(timeSlots)

    suspend fun deleteTimeSlotsForCourse(courseId: Long) = timeSlotDao.deleteTimeSlotsForCourse(courseId)

    suspend fun deleteAllTimeSlots() = timeSlotDao.deleteAllTimeSlots()

    suspend fun clearAllData() {
        timeSlotDao.deleteAllTimeSlots()
        courseDao.deleteAllCourses()
    }

    suspend fun insertCourseWithTimeSlots(course: Course, timeSlots: List<TimeSlot>): Long {
        val courseId = courseDao.insertCourse(course)
        val slotsWithCourseId = timeSlots.map { it.copy(courseId = courseId) }
        timeSlotDao.insertTimeSlots(slotsWithCourseId)
        return courseId
    }

    /**
     * 检查新时间安排是否与已有课程冲突
     * @param newSlot 要检查的新时间安排
     * @param excludeCourseId 排除的课程ID（编辑时排除自己）
     * @return 冲突的课程名+时间段描述，无冲突返回 null
     */
    suspend fun findConflict(
        newSlot: PendingSlotInfo,
        excludeCourseId: Long? = null
    ): String? {
        val allSlots = timeSlotDao.getAllTimeSlotsWithCourseName()
        val newWeeks = expandWeeks(newSlot.weeks, newSlot.weekType)

        for (existing in allSlots) {
            if (excludeCourseId != null && existing.courseId == excludeCourseId) continue
            if (existing.day != newSlot.day) continue

            // 检查节次是否重叠
            if (newSlot.startPeriod > existing.endPeriod || newSlot.endPeriod < existing.startPeriod) continue

            // 检查周次是否重叠
            val existingWeeks = expandWeeks(parseWeeks(existing.weeks), WeekType.valueOf(existing.type))
            if (!newWeeks.any { it in existingWeeks }) continue

            // 冲突！返回冲突信息
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

/**
 * 冲突检查用的简化时间安排信息
 */
data class PendingSlotInfo(
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: List<Int>,
    val weekType: WeekType
)
