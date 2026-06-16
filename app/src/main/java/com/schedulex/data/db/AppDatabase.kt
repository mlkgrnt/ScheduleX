package com.schedulex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.schedulex.data.model.Course
import com.schedulex.data.model.TimeSlot

@Database(
    entities = [Course::class, TimeSlot::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun timeSlotDao(): TimeSlotDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 → v2: 添加 scheduleId 字段
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN scheduleId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("ALTER TABLE time_slots ADD COLUMN scheduleId TEXT NOT NULL DEFAULT 'default'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedulex.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
