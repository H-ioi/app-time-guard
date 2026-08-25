package com.apptime.guard.core.engine

import android.content.Context
import com.apptime.guard.core.model.CastPolicy
import com.apptime.guard.core.model.ControlState
import com.apptime.guard.core.model.EngineResult
import com.apptime.guard.core.model.Rule
import com.apptime.guard.core.model.RuleMode
import com.apptime.guard.core.model.ScopeType
import com.apptime.guard.data.db.AppDatabase
import com.apptime.guard.data.prefs.SettingsRepository
import com.apptime.guard.util.Constants
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 规则引擎（核心状态机）。
 *
 * 结算顺序（需求 02 章）：
 * 1. 家长干预（暂停 → 全放行；锁定 → LOCKED）
 * 2. 冷却中（用停交替 / 连续上限 → COOLING）
 * 3. 就寝模式 → LOCKED
 * 4. 可用时间段：有启用规则但当前无规则命中 → LOCKED（时段外）
 * 5. 应用范围判定（白名单/黑名单/分类 → 拦截）
 * 6. 时长结算（多规则取最严格）：每日配额 / 连续上限 / 用停交替 / 按应用限时
 * 7. 投屏策略并入时长结算
 *
 * 多规则同时生效时按"最严格优先"。
 */
class RuleEngine(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val quota: QuotaManager,
    private val timeTrust: TimeTrust
) {

    /** 执行一次结算 */
    suspend fun evaluate(currentPackage: String? = null): EngineResult =
        withContext(Dispatchers.Default) {
            val elapsed = timeTrust.elapsed()
            val trustedNow = timeTrust.trustedNow()
            val state = quota.getState()
            val tamper = timeTrust.checkTamper()

            // 1. 家长干预
            if (state.pauseUntilElapsed > elapsed) {
                return@withContext EngineResult(
                    state = ControlState.AVAILABLE,
                    activeRuleSummary = "管控暂停中",
                    remainingMinutes = -1,
                    anomaly = tamper
                )
            }
            if (state.lockedUntilElapsed > elapsed) {
                val wait = (state.lockedUntilElapsed - elapsed) / 1000
                return@withContext EngineResult(
                    state = ControlState.LOCKED,
                    activeRuleSummary = "家长已锁定设备",
                    waitSeconds = wait,
                    anomaly = tamper
                )
            }

            // 2. 冷却中
            if (state.restEndElapsed > elapsed) {
                val wait = (state.restEndElapsed - elapsed) / 1000
                return@withContext EngineResult(
                    state = ControlState.COOLING,
                    activeRuleSummary = "休息时间",
                    waitSeconds = wait,
                    anomaly = tamper
                )
            }

            val rules = db.ruleDao().getEnabled()
            val nowMin = calendarMinute(trustedNow)
            val weekday = calendarWeekday(trustedNow)

            // 命中当前时间窗的规则
            val active = rules.filter { matchesWindow(it, weekday, nowMin) }

            // 3. 就寝模式优先
            val bedtime = active.firstOrNull { it.mode == RuleMode.BEDTIME }
            if (bedtime != null) {
                return@withContext EngineResult(
                    state = ControlState.LOCKED,
                    activeRuleSummary = bedtime.name.ifEmpty { "就寝时间" },
                    anomaly = tamper
                )
            }

            // 4. 时段外锁定：存在启用规则但当前无命中（B1 可用时间段）
            if (rules.isNotEmpty() && active.isEmpty()) {
                return@withContext EngineResult(
                    state = ControlState.LOCKED,
                    activeRuleSummary = "不在可用时间段",
                    anomaly = tamper
                )
            }

            // 4.5 临时放宽（家长 D2）：放宽期间该应用放行
            var targetPkg = currentPackage
            if (targetPkg != null) {
                val relax = db.relaxationDao().getAll()
                    .firstOrNull { it.packageName == targetPkg }
                if (relax != null && relax.endElapsed > elapsed) {
                    targetPkg = null // 视为不拦截该应用
                } else if (relax != null) {
                    db.relaxationDao().delete(relax.id)
                }
            }

            // 5. 应用范围判定
            if (targetPkg != null && isBlocked(targetPkg, active)) {
                return@withContext EngineResult(
                    state = ControlState.LOCKED,
                    activeRuleSummary = "该应用已被限制",
                    blockedApp = targetPkg,
                    anomaly = tamper
                )
            }

            // 6. 时长结算（多规则取最严格）
            val dailyQuota = active.mapNotNull { it.dailyQuotaMin }.minOrNull()
            val continuousMax = active.mapNotNull { it.continuousMaxMin }.minOrNull()
            val useX = active.mapNotNull { it.useXMin }.minOrNull()
            val restY = active.mapNotNull { it.restYMin }.minOrNull()
            val recoverN = active.mapNotNull { it.recoverNMin }.minOrNull()
            val buffer = active.maxOfOrNull { it.bufferMinutes } ?: 5
            val firstExempt = active.any { it.firstUseExempt }

            val remaining = quota.remainingMinutes(dailyQuota)

            // 6.1 用停交替 X/Y：连续使用达 X → 停 Y
            if (useX != null && restY != null && state.continuousUsedMin >= useX) {
                if (firstExempt && !state.usedToday) {
                    quota.saveState(state.copy(continuousUsedMin = 0))
                } else {
                    quota.startRest(restY)
                    return@withContext EngineResult(
                        state = ControlState.COOLING,
                        activeRuleSummary = "已连续使用 $useX 分钟，休息 $restY 分钟",
                        waitSeconds = restY * 60L,
                        anomaly = tamper
                    )
                }
            }

            // 6.2 连续上限：达上限 → 强制休息
            if (continuousMax != null && state.continuousUsedMin >= continuousMax) {
                val rest = restY ?: 10
                quota.startRest(rest)
                return@withContext EngineResult(
                    state = ControlState.COOLING,
                    activeRuleSummary = "连续使用已达 $continuousMax 分钟",
                    waitSeconds = rest * 60L,
                    anomaly = tamper
                )
            }

            // 6.3 按应用独立限时
            if (targetPkg != null) {
                val limit = perAppLimit(targetPkg, active)
                if (limit != null) {
                    val appUsed = quota.getAppUsed(targetPkg)
                    if (appUsed >= limit) {
                        return@withContext EngineResult(
                            state = ControlState.LOCKED,
                            activeRuleSummary = "该应用今日时限已用完",
                            blockedApp = targetPkg,
                            remainingMinutes = remaining,
                            anomaly = tamper
                        )
                    }
                }
            }

            // 6.4 每日配额 + 缓冲 + 冷却恢复
            if (dailyQuota != null && remaining != null && remaining <= 0) {
                val bufferMs = buffer * 60_000L
                val remindStart = if (state.remindStartElapsed > 0) state.remindStartElapsed else {
                    quota.saveState(state.copy(remindStartElapsed = elapsed))
                    elapsed
                }
                val buffered = elapsed - remindStart >= bufferMs
                return if (recoverN != null && restY != null && buffered) {
                    // 配额耗尽后：停 Y 恢复 N（循环）
                    quota.startRest(restY)
                    EngineResult(
                        state = ControlState.COOLING,
                        activeRuleSummary = "今日额度已用完，休息 $restY 分钟可再使用 $recoverN 分钟",
                        remainingMinutes = 0,
                        waitSeconds = restY * 60L,
                        anomaly = tamper
                    )
                } else if (buffered) {
                    EngineResult(
                        state = ControlState.LOCKED,
                        activeRuleSummary = "今日使用时间已用完",
                        remainingMinutes = 0,
                        anomaly = tamper
                    )
                } else {
                    EngineResult(
                        state = ControlState.REMINDING,
                        activeRuleSummary = "今日额度已用完（缓冲剩余 ${(bufferMs - (elapsed - remindStart)) / 1000} 秒后锁定）",
                        remainingMinutes = 0,
                        remindSeconds = (bufferMs - (elapsed - remindStart)) / 1000,
                        anomaly = tamper
                    )
                }
            }

            // 6.5 即将到期提醒
            val lead = settings.getRemindLead().coerceAtLeast(1)
            if (dailyQuota != null && remaining != null && remaining <= lead && remaining > 0) {
                return@withContext EngineResult(
                    state = ControlState.REMINDING,
                    activeRuleSummary = "今日额度剩余 $remaining 分钟",
                    remainingMinutes = remaining,
                    anomaly = tamper
                )
            }

            EngineResult(
                state = ControlState.AVAILABLE,
                activeRuleSummary = active.firstOrNull()?.name ?: "可用",
                remainingMinutes = remaining ?: -1,
                anomaly = tamper
            )
        }

    /** 时间窗匹配（含跨天规则） */
    private fun matchesWindow(rule: Rule, weekday: Int, nowMin: Int): Boolean {
        if (!rule.enabled) return false
        if ((rule.weekdayMask and (1 shl weekday)) == 0) return false
        return if (rule.endMin < rule.startMin) {
            // 跨天（如 21:00-07:00）
            nowMin >= rule.startMin || nowMin < rule.endMin
        } else {
            nowMin in rule.startMin..rule.endMin
        }
    }

    /** 应用是否被拦截（任一规则命中拦截即拦截） */
    private suspend fun isBlocked(pkg: String, active: List<Rule>): Boolean {
        if (active.isEmpty()) return false
        // 系统例外：系统组件与自身永不拦截
        if (pkg.startsWith("com.android.") || pkg == context.packageName) return false

        for (rule in active) {
            when (rule.scopeType) {
                ScopeType.ALL -> {
                    // 全部应用受控：非系统例外即拦截（白名单由 WHITELIST 规则表达）
                    return true
                }
                ScopeType.WHITELIST -> {
                    if (pkg !in rule.scopePackages.split(",")) return true
                }
                ScopeType.BLACKLIST -> {
                    if (pkg in rule.scopePackages.split(",")) return true
                }
                ScopeType.CATEGORY -> {
                    val cat = categoryOf(pkg)
                    if (cat in rule.scopeCategories.split(",")) return true
                }
                ScopeType.SYSTEM_EXEMPT -> { /* 仅用于豁免，不拦截 */ }
            }
        }
        return false
    }

    /** 按应用独立限时查询 */
    private suspend fun perAppLimit(pkg: String, active: List<Rule>): Int? {
        for (rule in active) {
            val item = rule.perAppLimits.split(",")
                .firstOrNull { it.startsWith("$pkg:") }
            if (item != null) {
                val limit = item.substringAfter(":").toIntOrNull()
                if (limit != null && limit > 0) return limit
            }
        }
        return null
    }

    /** 应用分类（含家长修正覆盖） */
    suspend fun categoryOf(pkg: String): String {
        val overrides = db.categoryDao().getAll().associate { it.packageName to it.category }
        return overrides[pkg] ?: Constants.guessCategory(pkg)
    }

    /** 当前是否投屏使用（结合家长策略决定是否计入） */
    suspend fun isCastActive(): Boolean = CastDetector.isCastConnected(context)

    /** 供 GuardService 每分钟 tick 时判断"当前是否在使用中"（用于配额累加） */
    suspend fun isInUse(): Boolean {
        val policy = settings.getCastPolicy()
        return when (policy) {
            CastPolicy.COUNT_AS_USE -> isCastActive()
            CastPolicy.LOCK_ON_CAST -> false // 由 evaluate 拦截处理
            CastPolicy.ALLOW_IGNORE -> false
        }
    }

    private fun calendarMinute(ts: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** 周一=0 ... 周日=6（与 weekdayMask 位 0..6 对齐） */
    private fun calendarWeekday(ts: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
