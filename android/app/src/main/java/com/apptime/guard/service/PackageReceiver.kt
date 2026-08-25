package com.apptime.guard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apptime.guard.AppTimeApp
import com.apptime.guard.core.model.AuditLog
import kotlinx.coroutines.launch

/** 应用安装/移除监听：新装应用默认按白名单模式判为禁止（家长需确认） */
class PackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.encodedSchemeSpecificPart ?: return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val replaced = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (replaced) return
                val app = AppTimeApp.get(context)
                app.appScope.launch {
                    app.database.auditDao().insert(
                        AuditLog(
                            type = "INSTALL",
                            detail = "新安装应用：$pkg（白名单模式下默认禁止，请家长确认）"
                        )
                    )
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                val app = AppTimeApp.get(context)
                app.appScope.launch {
                    app.database.auditDao().insert(
                        AuditLog(type = "REMOVE", detail = "应用被移除：$pkg")
                    )
                }
            }
        }
    }
}
