package com.apptime.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机自启：重启后恢复管控服务与时间基准 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                GuardService.start(context)
            }
        }
    }
}
