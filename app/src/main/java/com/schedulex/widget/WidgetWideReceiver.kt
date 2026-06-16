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

            val settings = try {
                runBlocking { loadScheduleSettings(ctx.scheduleDataStore) }
            } catch (e: Exception) { null }

            val week = try {
                if (settings != null) calculateActualWeek(settings) else 1
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
            val emptyColor = if (isDark) WIDGET_INFO_DARK else 0xFF999999.toInt()

            views.setTextColor(R.id.widget_info, infoColor)
            views.setTextColor(R.id.header_left, titleColor)
            views.setTextColor(R.id.header_right, titleColor)
            views.setTextColor(R.id.empty_left, emptyColor)
            views.setTextColor(R.id.empty_right, emptyColor)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            // 判断空状态文案
            val todayDow = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7
                           else cal.get(Calendar.DAY_OF_WEEK) - 1
            val tomorrowDow = if (todayDow == 7) 1 else todayDow + 1
            val tomorrowWeek = if (todayDow == 7) {
                (week + 1).coerceAtMost(settings?.totalWeeks ?: week)
            } else week

            val hasTodayCourses = hasCoursesForDay(ctx, todayDow, week)
            val hasTomorrowCourses = hasCoursesForDay(ctx, tomorrowDow, tomorrowWeek)

            // 左列(今日)空状态
            val todayEmptyText = if (hasTodayCourses) "今天的课上完咯🎉" else "今天没课🎉"
            views.setTextViewText(R.id.empty_left, todayEmptyText)

            // 右列(明日)空状态 + 彩蛋
            val tomorrowEmptyText = when {
                !hasTodayCourses && !hasTomorrowCourses -> "哇哦，明天也没课🥳"
                hasTomorrowCourses -> "明天的课上完咯🎉"  // 理论上明天的课不会"上完"，但保持一致逻辑
                else -> "明天没课🎉"
            }
            views.setTextViewText(R.id.empty_right, tomorrowEmptyText)

            val leftIntent = Intent(ctx, WidgetDualService::class.java)
            leftIntent.putExtra("LIST_TYPE", "LEFT")
            leftIntent.setData(Uri.parse("widget://dual/left/$id"))
            views.setRemoteAdapter(R.id.list_left, leftIntent)

            val rightIntent = Intent(ctx, WidgetDualService::class.java)
            rightIntent.putExtra("LIST_TYPE", "RIGHT")
            rightIntent.setData(Uri.parse("widget://dual/right/$id"))
            views.setRemoteAdapter(R.id.list_right, rightIntent)

            // 空列表时显示空状态文案
            views.setEmptyView(R.id.list_left, R.id.empty_left)
            views.setEmptyView(R.id.list_right, R.id.empty_right)

            // 点击小组件打开APP：双保险
            // 1. root 上的 PendingIntent — 空列表时也能点击打开
            // 2. ListView 上的 PendingIntentTemplate — 有课程时点击课程项也能打开
            val appIntent = android.app.PendingIntent.getActivity(
                ctx, id, Intent(ctx, com.schedulex.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, appIntent)
            views.setOnClickPendingIntent(R.id.widget_info, appIntent)
            views.setOnClickPendingIntent(R.id.header_left, appIntent)
            views.setOnClickPendingIntent(R.id.header_right, appIntent)
            views.setOnClickPendingIntent(R.id.empty_left, appIntent)
            views.setOnClickPendingIntent(R.id.empty_right, appIntent)
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
