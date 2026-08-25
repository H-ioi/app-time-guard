package com.apptime.guard.core.engine

import android.content.Context
import com.apptime.guard.core.model.QuotaState
import com.apptime.guard.data.db.AppDatabase
import com.apptime.guard.data.prefs.SettingsRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 配额管理：按"天"维护使用状态。
 * "天"以家长配置的重置时刻（默认 00:00）为界，避免孩子卡在重置时刻作弊。
 */
class QuotaManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val timeTrust: TimeTrust
) {

    /** 当前配额"天"的日期键（yyyy-MM-dd），按重置时刻切分 */
    suspend fun todayKey(): String = withContext(Dispatchers.Default) {
        val (h, m) = settings.getResetTime()
        val now = Date(timeTrust.trustedNow())
        val cal = Calendar.getInstance().apply {
            time = now
        }
        val minutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val resetMinutes = h * 60 + m
        if (minutesNow < resetMinutes) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    suspend fun getState(): QuotaState = withContext(Dispatchers.Default) {
        val key = todayKey()
        db.quotaDao().getByDate(key) ?: QuotaState(date = key).also {
            db.quotaDao().upsert(it)
        }
    }

    suspend fun saveState(state: QuotaState) = withContext(Dispatchers.Default) {
        db.quotaDao().upsert(state)
    }

    /** 今日剩余分钟（配额 - 已用 + 奖励），quota 为 null 返回 null（不限） */
    suspend fun remainingMinutes(dailyQuota: Int?): Long? = withContext(Dispatchers.Default) {
        if (dailyQuota == null) return@withContext null
        val s = getState()
        (dailyQuota - (s.usedMinutes - s.bonusMinutes)).coerceAtLeast(0L)
    }

    /** 增加已用分钟（配额结算），并累计连续使用 */
    suspend fun addUsed(minutes: Long = 1) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(
            s.copy(
                usedMinutes = s.usedMinutes + minutes,
                rawUsedMinutes = s.rawUsedMinutes + minutes,
                continuousUsedMin = s.continuousUsedMin + minutes.toInt(),
                usedToday = true
            )
        )
    }

    /** 奖励时长（家长 D2 发放） */
    suspend fun addBonus(minutes: Long) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(s.copy(bonusMinutes = s.bonusMinutes + minutes))
    }

    /** 累计休息/冷却分钟 */
    suspend fun addRest(minutes: Long = 1) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(s.copy(restMinutes = s.restMinutes + minutes))
    }

    /** 记录解锁次数 */
    suspend fun addUnlock() = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(s.copy(unlockCount = s.unlockCount + 1))
    }

    /** 按应用已用分钟 */
    suspend fun getAppUsed(pkg: String): Int = withContext(Dispatchers.Default) {
        val s = getState()
        s.perAppUsed.split(",").firstOrNull { it.startsWith("$pkg:") }
            ?.substringAfter(":")?.toIntOrNull() ?: 0
    }

    suspend fun addAppUsed(pkg: String, minutes: Long = 1) = withContext(Dispatchers.Default) {
        val s = getState()
        val map = s.perAppUsed.split(",")
            .filter { it.isNotEmpty() }
            .associate { it.substringBefore(":") to (it.substringAfter(":").toLongOrNull() ?: 0) }
            .toMutableMap()
        map[pkg] = (map[pkg] ?: 0) + minutes
        val serialized = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        db.quotaDao().upsert(s.copy(perAppUsed = serialized))
    }

    /** 进入冷却：设置冷却结束时间（单调时钟），休息时长计入统计 */
    suspend fun startRest(restMinutes: Int) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(
            s.copy(
                restEndElapsed = timeTrust.elapsed() + restMinutes * 60_000L,
                continuousUsedMin = 0,
                remindStartElapsed = 0
            )
        )
    }

    /** 家长锁定/解锁 */
    suspend fun lockUntil(minutes: Int) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(
            s.copy(lockedUntilElapsed = timeTrust.elapsed() + minutes * 60_000L)
        )
    }

    suspend fun unlockNow() = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(s.copy(lockedUntilElapsed = 0))
    }

    /** 暂停/恢复管控（D1） */
    suspend fun pauseUntil(minutes: Int) = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(
            s.copy(pauseUntilElapsed = timeTrust.elapsed() + minutes * 60_000L)
        )
    }

    suspend fun resumeNow() = withContext(Dispatchers.Default) {
        val s = getState()
        db.quotaDao().upsert(s.copy(pauseUntilElapsed = 0))
    }

    /** 清理过期数据（保留近 90 天配额、近 30 天审计） */
    suspend fun cleanup() = withContext(Dispatchers.Default) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -90) }
        val key90 = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        db.quotaDao().deleteOlderThan(key90)
        val ts30 = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        db.auditDao().deleteOlderThan(ts30)
    }
}
