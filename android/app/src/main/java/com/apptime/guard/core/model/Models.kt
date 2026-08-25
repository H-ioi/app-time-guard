package com.apptime.guard.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 规则模式 */
enum class RuleMode(val label: String) {
    NORMAL("普通时段"),
    BEDTIME("就寝时段"),
    FOCUS("专注时段")
}

/** 应用范围类型 */
enum class ScopeType(val label: String) {
    ALL("全部应用"),
    WHITELIST("白名单"),
    BLACKLIST("黑名单"),
    CATEGORY("按类别"),
    SYSTEM_EXEMPT("系统例外")
}

/** 锁定形态 */
enum class LockStyle(val label: String) {
    FULLSCREEN("全屏拦截"),
    NOTIFY_ONLY("仅提醒")
}

/** 设备状态 */
enum class ControlState(val label: String) {
    AVAILABLE("可用"),
    REMINDING("提醒中"),
    COOLING("冷却中"),
    LOCKED("锁定中")
}

/** 投屏处理策略 */
enum class CastPolicy(val label: String) {
    COUNT_AS_USE("投屏计为使用"),
    LOCK_ON_CAST("投屏即锁定"),
    ALLOW_IGNORE("允许投屏不计数")
}

/** 安全等级 */
enum class SecurityLevel(val label: String) {
    STANDARD("标准"),
    STRICT("严格"),
    RELAXED("宽松")
}

/** 规则实体 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    /** 周一=1<<0 ... 周日=1<<6 */
    val weekdayMask: Int = 0x7F,
    /** 开始分钟（0..1439），跨天规则 endMin < startMin */
    val startMin: Int = 0,
    val endMin: Int = 1439,
    val mode: RuleMode = RuleMode.NORMAL,
    val scopeType: ScopeType = ScopeType.ALL,
    /** 白名单/黑名单包名集合（逗号分隔） */
    val scopePackages: String = "",
    /** 分类集合（逗号分隔：game,video,social,shopping,store,browser,reading） */
    val scopeCategories: String = "",
    /** 每日总时长配额（分钟，null=不限） */
    val dailyQuotaMin: Int? = null,
    /** 单次连续上限（分钟，null=不限） */
    val continuousMaxMin: Int? = null,
    /** 用停交替：用 X 分钟 */
    val useXMin: Int? = null,
    /** 用停交替：停 Y 分钟 */
    val restYMin: Int? = null,
    /** 冷却恢复制：停 Y 后恢复 N 分钟（null=不启用） */
    val recoverNMin: Int? = null,
    /** 首段豁免：每天第一次使用跳过休息 */
    val firstUseExempt: Boolean = false,
    /** 按应用独立限时 pkg:min,pkg2:min */
    val perAppLimits: String = "",
    /** 提前提醒分钟数 */
    val remindLeadMinutes: Int = 5,
    val lockStyle: LockStyle = LockStyle.FULLSCREEN,
    val allowSaveBuffer: Boolean = true,
    /** 缓冲分钟数（默认 5，可配 0） */
    val bufferMinutes: Int = 5,
    /** 就寝模式紧急应用（逗号分隔包名） */
    val emergencyApps: String = "",
    /** 就寝模式是否显示时钟 */
    val showClock: Boolean = true,
    val enabled: Boolean = true,
    /** 家长优先级（越大越优先，同层级用） */
    val priority: Int = 0
)

/** 配额状态（按自然日一条） */
@Entity(tableName = "quota_states")
data class QuotaState(
    @PrimaryKey val date: String,
    /** 已用分钟（含奖励消耗） */
    val usedMinutes: Long = 0,
    /** 按应用已用 pkg:min,逗号 */
    val perAppUsed: String = "",
    /** 今日奖励追加分钟 */
    val bonusMinutes: Long = 0,
    /** 今日累计连续使用分钟 */
    val continuousUsedMin: Int = 0,
    /** 冷却结束时间戳（毫秒，单调时钟） */
    val restEndElapsed: Long = 0,
    /** 家长锁定结束时间戳（单调时钟） */
    val lockedUntilElapsed: Long = 0,
    /** 今日是否已用过（首段豁免判断） */
    val usedToday: Boolean = false,
    /** 暂停管控结束时间戳（单调时钟，0=未暂停） */
    val pauseUntilElapsed: Long = 0,
    /** 今日使用分钟原始累计（不含奖励） */
    val rawUsedMinutes: Long = 0,
    /** 今日休息/冷却分钟 */
    val restMinutes: Long = 0,
    /** 今日解锁次数 */
    val unlockCount: Int = 0,
    /** 到期缓冲/提醒起始时间戳（单调时钟，0=未开始） */
    val remindStartElapsed: Long = 0,
    /** 本次提醒是否已通知过 */
    val remindNotified: Boolean = false
)

/** 临时放宽（家长 D2） */
@Entity(tableName = "temp_relaxations")
data class TempRelaxation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    /** 结束时间戳（单调时钟） */
    val endElapsed: Long
)

/** 审计日志 */
@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long = System.currentTimeMillis(),
    val type: String = "",
    val detail: String = ""
)

/** 分类修正（家长手动调整单个 App 类别） */
@Entity(tableName = "category_overrides")
data class CategoryOverride(
    @PrimaryKey val packageName: String,
    val category: String
)

/** 应用信息（非持久化，运行时从系统读取） */
data class AppInfo(
    val packageName: String,
    val label: String,
    val category: String = "other",
    val isSystem: Boolean = false,
    val icon: Any? = null
)

/** 规则引擎的一次结算结果 */
data class EngineResult(
    val state: ControlState,
    /** 当前生效规则摘要 */
    val activeRuleSummary: String = "",
    /** 今日剩余分钟 */
    val remainingMinutes: Long = 0,
    /** 今日已用分钟 */
    val usedMinutes: Long = 0,
    /** 冷却/锁定剩余秒数 */
    val waitSeconds: Long = 0,
    /** 提醒剩余秒数（REMINDING 时） */
    val remindSeconds: Long = 0,
    /** 是否拦截指定应用 */
    val blockedApp: String? = null,
    /** 当前规则（用于孩子端展示） */
    val activeRule: Rule? = null,
    /** 异常标记（如检测到时间篡改） */
    val anomaly: String? = null
)

/** 模板配置 */
data class TemplateConfig(
    val id: String,
    val name: String,
    val target: String,
    val dailyQuotaWeekday: Int,
    val dailyQuotaWeekend: Int,
    val continuousMax: Int,
    val useX: Int,
    val restY: Int,
    val bedtimeStart: Int,
    val bedtimeEnd: Int,
    val blockCategories: List<String>,
    val description: String
)
