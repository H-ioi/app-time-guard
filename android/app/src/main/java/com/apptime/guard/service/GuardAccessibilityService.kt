package com.apptime.guard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.apptime.guard.AppTimeApp
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.ui.lock.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 无障碍拦截服务（核心管控执行器）：
 * 监听窗口变化，识别受限应用启动，立即返回桌面并提示。
 */
class GuardAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var checkJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // 忽略自身与系统 UI
        if (pkg == packageName) return
        if (pkg.startsWith("com.android.")) return

        checkJob?.cancel()
        checkJob = scope.launch {
            val app = AppTimeApp.get(this@GuardAccessibilityService)
            val result = app.ruleEngine.evaluate(pkg)
            if (result.state == ControlState.LOCKED && result.blockedApp == pkg) {
                // 受限应用 → 返回桌面 + 启动锁定提示界面
                performGlobalAction(GLOBAL_ACTION_HOME)
                LockActivity.start(this@GuardAccessibilityService, result)
            } else if (result.state == ControlState.LOCKED ||
                result.state == ControlState.COOLING
            ) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                LockActivity.start(this@GuardAccessibilityService, result)
            }
        }
    }

    override fun onInterrupt() {
        // 服务被系统中断：记录，待重启自检恢复
    }
}
