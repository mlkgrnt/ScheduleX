package com.schedulex.data.db

import androidx.room.TypeConverter
import com.schedulex.data.model.WeekType

class Converters {
    @TypeConverter
    fun fromWeekType(value: WeekType): String = value.name

    @TypeConverter
    fun toWeekType(value: String): WeekType = WeekType.valueOf(value)
}
