package com.schedulex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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

            val currentWeek = try {
                val settings = runBlocking { loadScheduleSettings(context.scheduleDataStore) }
                calculateActualWeek(settings)
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

            val intent = Intent(context, WidgetCourseListService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.data = Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_list_view, intent)
            views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_text)

            val clickIntent = Intent(context, com.schedulex.ui.MainActivity::class.java)
            val pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, clickIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list_view)
        }

        fun notifyAllUpdate(context: Context) {
            val intent = Intent(context, WidgetSingleReceiver::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, WidgetSingleReceiver::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
