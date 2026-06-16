package com.schedulex.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.schedulex.ui.MainActivity

/**
 * 课前提醒广播接收器
 * 接收 AlarmManager 发出的提醒，发送通知
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "class_reminder"
        const val EXTRA_COURSE_NAME = "course_name"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_MINUTES_BEFORE = "minutes_before"

        /**
         * 创建通知渠道（Android 8.0+）
         */
        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "课前提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "上课前发送提醒通知"
                    enableVibration(true)
                }
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME) ?: "即将上课"
        val location = intent.getStringExtra(EXTRA_LOCATION) ?: ""
        val minutesBefore = intent.getIntExtra(EXTRA_MINUTES_BEFORE, 5)

        // 确保通知渠道存在
        createNotificationChannel(context)

        // 点击通知打开 APP
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "还有${minutesBefore}分钟上课"
        val body = buildString {
            append(courseName)
            if (location.isNotBlank()) {
                append(" · 📍$location")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
