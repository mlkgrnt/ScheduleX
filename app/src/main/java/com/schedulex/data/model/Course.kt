package com.schedulex.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val teacher: String? = null,
    val color: String = "#4FC3F7",
    val createdAt: Long = System.currentTimeMillis(),
    val scheduleId: String = "default"  // 课表ID，默认课表
)
