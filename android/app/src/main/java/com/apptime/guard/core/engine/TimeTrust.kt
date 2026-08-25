package com.apptime.guard.core.engine

import android.os.SystemClock
import com.apptime.guard.data.prefs.SettingsRepository
import com.apptime.guard.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 时间可信（纯本地，无网络依赖）：
 * - 系统时间 System.currentTimeMillis() 可被用户修改；
 * - 单调时钟 SystemClock.elapsedRealtime() 自开机递增、用户无法修改（重启清零）；
 * - 维护偏移 offset = 系统时间 - 单调时间，检测偏移回拨判定篡改；
 * - 配额结算一律基于单调时间，杜绝"改时间重置配额"。
 */
class TimeTrust(private val settings: SettingsRepository) {

    /** 可信"系统时间"：单调时间 + 上次记录的偏移 */
    suspend fun trustedNow(): Long = withContext(Dispatchers.Default) {
        SystemClock.elapsedRealtime() + settings.getTimeOffset()
    }

    /** 单调时钟（毫秒） */
    fun elapsed(): Long = SystemClock.elapsedRealtime()

    /**
     * 初始化/校正偏移基准。
     * 每次启动、重启后调用：记录当前偏移。
     */
    suspend fun init() = withContext(Dispatchers.Default) {
        val sys = System.currentTimeMillis()
        val el = SystemClock.elapsedRealtime()
        val stored = settings.getTimeOffset()
        if (stored == 0L) {
            // 首次：直接以当前偏移为基准
            settings.setTimeOffset(sys - el, el)
        }
        // 记录最近一次系统时间，供回拨检测
        val last = settings.getLastSystemTime()
        if (last > 0L && sys < last - Constants.TAMPER_THRESHOLD_MS) {
            settings.setTampered(true)
        }
        settings.setLastSystemTime(sys)
    }

    /**
     * 检测时间篡改。在结算/开机时调用：
     * - 若系统时间相对单调时间的偏移明显回拨（超过阈值）→ 判定篡改
     * - 篡改后按"最保守"结算：不重置基准，保留旧偏移，即以旧时间继续
     */
    suspend fun checkTamper(): String? = withContext(Dispatchers.Default) {
        val sys = System.currentTimeMillis()
        val el = SystemClock.elapsedRealtime()
        val offset = settings.getTimeOffset()
        if (offset == 0L) return@withContext null
        val drift = sys - el - offset
        // 只检测"回拨"（往过去改），改未来不惩罚（防止误伤时区/自动校正）
        if (drift < -Constants.TAMPER_THRESHOLD_MS) {
            settings.setTampered(true)
            // 最保守：保持旧偏移不变，相当于时间"冻结"在篡改前
            return@withContext "检测到系统时间回拨（${(-drift) / 1000} 秒），已按最保守时间结算"
        }
        null
    }

    /** 检测系统时间是否被改到未来（超过一天），用于严格模式告警 */
    suspend fun checkFutureJump(): Boolean = withContext(Dispatchers.Default) {
        val sys = System.currentTimeMillis()
        val el = SystemClock.elapsedRealtime()
        val offset = settings.getTimeOffset()
        if (offset == 0L) return@withContext false
        val drift = sys - el - offset
        drift > 24 * 3600 * 1000L
    }
}
