package com.apptime.guard.core.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.session.MediaSessionManager
import android.os.SystemClock
import com.apptime.guard.data.prefs.SettingsRepository

/**
 * 投屏检测（需求 02 章 2.2.4）：
 * - DisplayManager 监听外部显示/无线投屏连接；
 * - 兜底启发式：息屏 + 音频播放中判定为活跃；
 * - 华为 Cast+/多屏协同是否触发显示回调需真机验证（文档 5.6.1 验证项）。
 */
class CastDetector(
    private val context: Context,
    private val settings: SettingsRepository
) {

    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    /** 当前是否有外部显示连接（投屏中） */
    fun hasExternalDisplay(): Boolean {
        return displayManager.displays.any { it.displayId != DisplayManager.DEFAULT_DISPLAY }
    }

    /** 兜底：息屏 + 有音频播放 → 疑似投屏观看 */
    fun isAudioPlayingWhileScreenOff(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE)
            as android.os.PowerManager
        if (powerManager.isInteractive) return false // 亮屏不算
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val sessions = msm.activeSessions?.toList().orEmpty()
        return sessions.any { s -> s.isActive }
    }

    companion object {
        @Volatile
        private var lastCastState = false

        @Volatile
        private var lastChecked = 0L

        /** 综合判定是否"投屏使用中"（带 5s 缓存，供高频调用） */
        fun isCastConnected(context: Context): Boolean {
            val now = SystemClock.elapsedRealtime()
            if (now - lastChecked < 5000L) return lastCastState
            lastChecked = now
            val detector = CastDetector(context, SettingsRepository(context))
            lastCastState = detector.hasExternalDisplay() || detector.isAudioPlayingWhileScreenOff()
            return lastCastState
        }
    }
}
