package com.apptime.guard.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** 设备管理员：提供锁屏与防卸载能力（严格级安全） */
class GuardDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // 设备管理员被关闭（孩子尝试解除）→ 家长告警
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        nm.notify(
            2001,
            android.app.Notification.Builder(context, "alert_channel")
                .setContentTitle("守护异常")
                .setContentText("设备管理员权限已被关闭，管控防卸载能力失效，请家长重新启用")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        fun getComponentName(context: Context) =
            android.content.ComponentName(context, GuardDeviceAdminReceiver::class.java)
    }
}
