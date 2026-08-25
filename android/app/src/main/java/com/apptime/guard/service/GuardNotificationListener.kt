package com.apptime.guard.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 通知监听：
 * - 探测系统通知被禁用（孩子关掉本 App 通知 → 告警降级逻辑）；
 * - 转发"请求家长"等关键通知。
 */
class GuardNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 预留：监控受限应用的通知（如游戏提醒）并记录审计
    }

    override fun onListenerDisconnected() {
        // 监听被断开（孩子可能在系统设置中关闭）→ 记审计，下次开机横幅告警
        try {
            val app = com.apptime.guard.AppTimeApp.get(this)
            app.appScope.launch {
                app.database.auditDao().insert(
                    com.apptime.guard.core.model.AuditLog(
                        type = "SECURITY",
                        detail = "通知监听服务被断开"
                    )
                )
            }
        } catch (ex: Exception) {
            // 应用进程不可用时忽略
        }
    }

    companion object {
        fun isNotificationEnabled(context: android.content.Context): Boolean {
            val nm = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            return nm.areNotificationsEnabled()
        }
    }
}
