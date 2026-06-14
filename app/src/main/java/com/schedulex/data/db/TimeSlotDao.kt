package com.schedulex.data.db

import androidx.room.*
import com.schedulex.data.model.TimeSlot
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeSlotDao {
    @Query("SELECT * FROM time_slots WHERE courseId = :courseId")
    fun getTimeSlotsForCourse(courseId: Long): Flow<List<TimeSlot>>

    @Query("SELECT * FROM time_slots")
    fun getAllTimeSlots(): Flow<List<TimeSlot>>

    @Query("SELECT * FROM time_slots")
    suspend fun getAllTimeSlotsSync(): List<TimeSlot>

    @Query("SELECT * FROM time_slots WHERE day = :day")
    fun getTimeSlotsByDay(day: Int): Flow<List<TimeSlot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSlot(timeSlot: TimeSlot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeSlots(timeSlots: List<TimeSlot>)

    @Update
    suspend fun updateTimeSlot(timeSlot: TimeSlot)

    @Delete
    suspend fun deleteTimeSlot(timeSlot: TimeSlot)

    @Query("DELETE FROM time_slots WHERE courseId = :courseId")
    suspend fun deleteTimeSlotsForCourse(courseId: Long)

    @Query("DELETE FROM time_slots")
    suspend fun deleteAllTimeSlots()
}
