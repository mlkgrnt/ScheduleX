# ScheduleX ProGuard Rules

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class **
-keep @androidx.room.Dao class **
-keepclassmembers class * { @androidx.room.* <methods>; }
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.schedulex.**$$serializer { *; }
-keepclassmembers class com.schedulex.** { *** Companion; }
-keepclasseswithmembers class com.schedulex.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# DataStore
-keep class androidx.datastore.** { *; }

# Glance Widget
-keep class androidx.glance.** { *; }
-keep class com.schedulex.widget.** { *; }

# Keep model classes used by Room
-keep class com.schedulex.data.model.** { *; }
-keep class com.schedulex.data.db.** { *; }

# Keep reminder classes
-keep class com.schedulex.reminder.** { *; }

# Keep navigation
-keep class com.schedulex.ui.navigation.** { *; }

# SLF4J (referenced by Ktor)
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
