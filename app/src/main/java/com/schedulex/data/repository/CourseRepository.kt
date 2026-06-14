package com.schedulex.data.repository

import com.schedulex.data.db.CourseDao
import com.schedulex.data.db.TimeSlotDao
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

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
}
