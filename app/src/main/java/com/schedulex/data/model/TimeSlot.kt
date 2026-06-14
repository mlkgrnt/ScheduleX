package com.schedulex.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WeekType { ALL, ODD, EVEN }

@Entity(
    tableName = "time_slots",
    foreignKeys = [ForeignKey(
        entity = Course::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("courseId")]
)
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val day: Int,           // 1=Mon ... 7=Sun
    val startPeriod: Int,   // starting period number (1-based)
    val endPeriod: Int,     // ending period number (inclusive)
    val location: String? = null,
    val weeks: String,      // JSON array of week numbers: "[1,2,3,5,7]"
    val type: WeekType = WeekType.ALL
)
