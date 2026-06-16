package com.schedulex.data.db

import androidx.room.*
import com.schedulex.data.model.Course
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY createdAt DESC")
    fun getCoursesBySchedule(scheduleId: String): Flow<List<Course>>

    @Query("SELECT * FROM courses ORDER BY createdAt DESC")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY createdAt DESC")
    suspend fun getCoursesByScheduleSync(scheduleId: String): List<Course>

    @Query("SELECT * FROM courses ORDER BY createdAt DESC")
    suspend fun getAllCoursesSync(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: Long): Course?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteCoursesBySchedule(scheduleId: String)

    @Query("SELECT DISTINCT scheduleId FROM courses")
    suspend fun getAllScheduleIds(): List<String>
}
