package com.apptime.guard.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** 使用统计辅助：获取当前前台应用（无需特殊权限之外的系统授权） */
object UsageStatsHelper {

    fun hasUsagePermission(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 10_000L
        val events = usm.queryEvents(begin, end)
        return try {
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
            }
            true
        } catch (ex: SecurityException) {
            false
        }
    }

    /** 获取当前（或最近）前台应用包名 */
    fun getTopPackage(context: Context): String? {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 60_000L
            val events = usm.queryEvents(begin, end)
            val e = UsageEvents.Event()
            var lastApp: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    e.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    lastApp = e.packageName
                }
            }
            lastApp
        } catch (ex: Exception) {
            null
        }
    }
}
