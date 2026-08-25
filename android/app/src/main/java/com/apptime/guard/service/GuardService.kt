package com.apptime.guard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.PowerManager
import com.apptime.guard.AppTimeApp
import com.apptime.guard.MainActivity
import com.apptime.guard.R
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.core.model.EngineResult
import com.apptime.guard.ui.lock.LockActivity
import com.apptime.guard.util.Constants
import com.apptime.guard.util.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 前台管控服务：常驻后台，每分钟执行规则结算与配额累加。
 * - 结算状态变化时：发送广播、展示通知、控制锁定界面；
 * - 处理家长指令（锁定/暂停/放宽/解锁）。
 */
class GuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundCompat()
        observeScreen()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_LOCK_NOW -> {
                val minutes = intent.getIntExtra("minutes", 30)
                scope.launch {
                    AppTimeApp.get(this@GuardService).quotaManager.lockUntil(minutes)
                    evaluateAndNotify()
                }
            }
            Constants.ACTION_PAUSE -> {
                val minutes = intent.getIntExtra(Constants.EXTRA_PAUSE_MIN, 10)
                scope.launch {
                    AppTimeApp.get(this@GuardService).quotaManager.pauseUntil(minutes)
                    evaluateAndNotify()
                }
            }
            Constants.ACTION_UNPAUSE -> {
                scope.launch {
                    AppTimeApp.get(this@GuardService).quotaManager.resumeNow()
                    evaluateAndNotify()
                }
            }
            else -> {
                ensureTicker()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        tickJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    private fun observeScreen() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
    }

    private val screenReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> scope.launch {
                    AppTimeApp.get(this@GuardService).quotaManager.addUnlock()
                }
                else -> { /* 无需处理 */ }
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = buildGuardNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Constants.NOTIF_ID_GUARD, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Constants.NOTIF_ID_GUARD, notification)
        }
    }

    private fun buildGuardNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, Constants.CHANNEL_GUARD)
            .setContentTitle(getString(R.string.guard_notification_title))
            .setContentText(getString(R.string.guard_notification_text))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_GUARD,
                getString(R.string.guard_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.guard_channel_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_REMIND,
                "时长提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        nm.createNotificationChannel(
            NotificationChannel(
                Constants.CHANNEL_ALERT,
                "家长告警",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun ensureTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                evaluateAndNotify()
                delay(60_000L)
            }
        }
    }

    private suspend fun evaluateAndNotify() {
        val app = AppTimeApp.get(this)
        val result = app.ruleEngine.evaluate(UsageStatsHelper.getTopPackage(this))

        // 使用累加（每分钟）
        if (result.state == ControlState.AVAILABLE || result.state == ControlState.REMINDING) {
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenOn = power.isInteractive
            val castActive = app.ruleEngine.isInUse()
            if (screenOn || castActive) {
                app.quotaManager.addUsed(1L)
                UsageStatsHelper.getTopPackage(this)?.let {
                    app.quotaManager.addAppUsed(it, 1L)
                }
            }
        } else if (result.state == ControlState.COOLING) {
            app.quotaManager.addRest(1L)
        }

        // 异常告警
        if (result.anomaly != null && !app.settings.getSilentMode()) {
            notifyAlert(result.anomaly)
        }

        // 提醒通知（进入 REMINDING 时只发一次）
        if (result.state == ControlState.REMINDING) {
            val state = app.quotaManager.getState()
            if (!state.remindNotified) {
                notifyRemind(result.activeRuleSummary)
                app.quotaManager.saveState(state.copy(remindNotified = true))
            }
        }

        // 锁定界面控制
        broadcastState(result)
    }

    private fun broadcastState(result: EngineResult) {
        val locked = result.state == ControlState.LOCKED || result.state == ControlState.COOLING
        val intent = Intent(Constants.ACTION_UPDATE)
            .putExtra("state", result.state.name)
            .putExtra("summary", result.activeRuleSummary)
            .putExtra("remaining", result.remainingMinutes)
            .putExtra("wait", result.waitSeconds)
            .putExtra("locked", locked)
            .setPackage(packageName)
        sendBroadcast(intent)

        if (locked) {
            LockActivity.start(this, result)
        } else {
            sendBroadcast(Intent("com.apptime.guard.action.CLOSE_LOCK").setPackage(packageName))
        }
    }

    private fun notifyRemind(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            Constants.NOTIF_ID_REMIND,
            Notification.Builder(this, Constants.CHANNEL_REMIND)
                .setContentTitle("使用时长提醒")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notifyAlert(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(
            Constants.NOTIF_ID_ALERT,
            Notification.Builder(this, Constants.CHANNEL_ALERT)
                .setContentTitle("守护异常")
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GuardService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
