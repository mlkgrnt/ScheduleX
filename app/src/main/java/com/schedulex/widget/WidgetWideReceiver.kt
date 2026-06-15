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

class WidgetWideReceiver : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) update(ctx, mgr, id)
        MidnightReceiver.scheduleNext(ctx)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 系统主题变化时刷新小组件深色模式
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WidgetWideReceiver::class.java))
            if (ids.isNotEmpty()) {
                for (id in ids) update(context, mgr, id)
            }
        }
    }

    override fun onEnabled(context: Context) {
        MidnightReceiver.scheduleNext(context)
    }

    companion object {
        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_course_wide)
            val isDark = isWidgetDarkMode(ctx)

            val week = try {
                val s = runBlocking { loadScheduleSettings(ctx.scheduleDataStore) }
                calculateActualWeek(s)
            } catch (e: Exception) { 1 }

            val cal = Calendar.getInstance()
            val dow = getDayOfWeekStr(cal)
            val m = cal.get(Calendar.MONTH) + 1
            val d = cal.get(Calendar.DAY_OF_MONTH)

            views.setTextViewText(R.id.widget_info, "第${week}周 · $m/$d $dow")
            views.setTextViewText(R.id.header_left, "今日")
            views.setTextViewText(R.id.header_right, "明日")

            // 深色模式文字颜色
            val infoColor = if (isDark) WIDGET_INFO_DARK else WIDGET_INFO_LIGHT
            val titleColor = if (isDark) WIDGET_TITLE_DARK else WIDGET_TITLE_LIGHT
            val bgColor = if (isDark) WIDGET_BG_DARK else WIDGET_BG_LIGHT

            views.setTextColor(R.id.widget_info, infoColor)
            views.setTextColor(R.id.header_left, titleColor)
            views.setTextColor(R.id.header_right, titleColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            val leftIntent = Intent(ctx, WidgetDualService::class.java)
            leftIntent.putExtra("LIST_TYPE", "LEFT")
            leftIntent.setData(Uri.parse("widget://dual/left/$id"))
            views.setRemoteAdapter(R.id.list_left, leftIntent)

            val rightIntent = Intent(ctx, WidgetDualService::class.java)
            rightIntent.putExtra("LIST_TYPE", "RIGHT")
            rightIntent.setData(Uri.parse("widget://dual/right/$id"))
            views.setRemoteAdapter(R.id.list_right, rightIntent)

            // 点击小组件打开APP：双保险
            // 1. root 上的 PendingIntent — 空列表时也能点击打开
            // 2. ListView 上的 PendingIntentTemplate — 有课程时点击课程项也能打开
            val appIntent = android.app.PendingIntent.getActivity(
                ctx, id, Intent(ctx, com.schedulex.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appIntent)
            views.setPendingIntentTemplate(R.id.list_left, appIntent)
            views.setPendingIntentTemplate(R.id.list_right, appIntent)

            mgr.updateAppWidget(id, views)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.list_left)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.list_right)
        }

        fun notifyAll(ctx: Context) {
            val i = Intent(ctx, WidgetWideReceiver::class.java)
            i.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val mgr = AppWidgetManager.getInstance(ctx)
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
                mgr.getAppWidgetIds(ComponentName(ctx, WidgetWideReceiver::class.java)))
            ctx.sendBroadcast(i)
        }
    }
}
