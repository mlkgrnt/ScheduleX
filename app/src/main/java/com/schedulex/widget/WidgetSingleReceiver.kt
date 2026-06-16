package com.schedulex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.schedulex.R
import com.schedulex.data.model.calculateActualWeek
import com.schedulex.data.model.loadScheduleSettings
import com.schedulex.data.model.scheduleDataStore
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * 2×2 小组件：只显示今日课程
 */
class WidgetSingleReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        MidnightReceiver.scheduleNext(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 系统主题变化时刷新小组件深色模式
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WidgetSingleReceiver::class.java))
            if (ids.isNotEmpty()) {
                for (id in ids) updateWidget(context, mgr, id)
            }
        }
    }

    override fun onEnabled(context: Context) {
        MidnightReceiver.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
    }

    companion object {
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_course_single)
            val isDark = isWidgetDarkMode(context)

            val settings = try {
                runBlocking { loadScheduleSettings(context.scheduleDataStore) }
            } catch (e: Exception) { null }

            val currentWeek = try {
                if (settings != null) calculateActualWeek(settings) else 1
            } catch (e: Exception) { 1 }

            val calendar = Calendar.getInstance()
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeek = getDayOfWeekStr(calendar)

            views.setTextViewText(R.id.widget_week_info, "第${currentWeek}周 · $month/$day $dayOfWeek")
            views.setTextViewText(R.id.widget_title, "今日课程")

            // 深色模式文字颜色
            val infoColor = if (isDark) WIDGET_INFO_DARK else WIDGET_INFO_LIGHT
            val titleColor = if (isDark) WIDGET_TITLE_DARK else WIDGET_TITLE_LIGHT
            val bgColor = if (isDark) WIDGET_BG_DARK else WIDGET_BG_LIGHT
            val emptyColor = if (isDark) WIDGET_INFO_DARK else 0xFF999999.toInt()

            views.setTextColor(R.id.widget_week_info, infoColor)
            views.setTextColor(R.id.widget_title, titleColor)
            views.setTextColor(R.id.widget_empty_text, emptyColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            // 判断空状态文案
            val todayDow = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                           else calendar.get(Calendar.DAY_OF_WEEK) - 1
            val hasTodayCourses = hasCoursesForDay(context, todayDow, currentWeek)
            val emptyText = if (hasTodayCourses) "今天的课上完咯🎉" else "今天没课🎉"
            views.setTextViewText(R.id.widget_empty_text, emptyText)

            val intent = Intent(context, WidgetCourseListService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list_view, intent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

            // 点击小组件打开APP：双保险
            // 1. root 上的 PendingIntent — 空列表时也能点击打开
            // 2. ListView 上的 PendingIntentTemplate — 有课程时点击课程项也能打开
            val appIntent = android.app.PendingIntent.getActivity(
                context, appWidgetId,
                Intent(context, com.schedulex.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appIntent)
            views.setPendingIntentTemplate(R.id.widget_list_view, appIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }

        fun notifyAllUpdate(context: Context) {
            val intent = Intent(context, WidgetSingleReceiver::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, WidgetSingleReceiver::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
