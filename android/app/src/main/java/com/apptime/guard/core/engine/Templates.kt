package com.apptime.guard.core.engine

import com.apptime.guard.core.model.Rule
import com.apptime.guard.core.model.RuleMode
import com.apptime.guard.core.model.ScopeType
import com.apptime.guard.core.model.TemplateConfig

/**
 * 一键模板（需求 03 章）：学龄前 / 小学生 / 中学生 / 假期。
 * 默认上学日（周一~五）与周末（周六日）两套规则 + 就寝规则。
 */
object Templates {

    val list = listOf(
        TemplateConfig(
            id = "preschool",
            name = "学龄前（3-6 岁）",
            target = "3-6 岁",
            dailyQuotaWeekday = 30,
            dailyQuotaWeekend = 45,
            continuousMax = 15,
            useX = 15,
            restY = 20,
            bedtimeStart = 20 * 60 + 30,
            bedtimeEnd = 7 * 60,
            blockCategories = listOf("game", "shopping", "social"),
            description = "每天 30 分钟，连续 15 分钟休息 20 分钟，20:30 就寝"
        ),
        TemplateConfig(
            id = "primary",
            name = "小学生（7-12 岁）",
            target = "7-12 岁",
            dailyQuotaWeekday = 60,
            dailyQuotaWeekend = 120,
            continuousMax = 30,
            useX = 30,
            restY = 15,
            bedtimeStart = 21 * 60,
            bedtimeEnd = 6 * 60 + 30,
            blockCategories = listOf("game", "shopping"),
            description = "上学日每天 60 分钟，连续 30 分钟休息 15 分钟，21:00 就寝"
        ),
        TemplateConfig(
            id = "middle",
            name = "中学生（13-15 岁）",
            target = "13-15 岁",
            dailyQuotaWeekday = 90,
            dailyQuotaWeekend = 180,
            continuousMax = 45,
            useX = 45,
            restY = 10,
            bedtimeStart = 22 * 60,
            bedtimeEnd = 6 * 60,
            blockCategories = listOf("game", "shopping"),
            description = "上学日每天 90 分钟，连续 45 分钟休息 10 分钟，22:00 就寝"
        ),
        TemplateConfig(
            id = "holiday",
            name = "假期模式",
            target = "通用",
            dailyQuotaWeekday = 120,
            dailyQuotaWeekend = 120,
            continuousMax = 40,
            useX = 40,
            restY = 15,
            bedtimeStart = 21 * 60 + 30,
            bedtimeEnd = 7 * 60,
            blockCategories = listOf("shopping"),
            description = "每天 120 分钟，连续 40 分钟休息 15 分钟，21:30 就寝"
        ),
        TemplateConfig(
            id = "custom",
            name = "自定义",
            target = "家长手动配置",
            dailyQuotaWeekday = 60,
            dailyQuotaWeekend = 120,
            continuousMax = 30,
            useX = 30,
            restY = 15,
            bedtimeStart = 21 * 60,
            bedtimeEnd = 7 * 60,
            blockCategories = emptyList(),
            description = "从空白规则开始，完全按家长意愿配置"
        )
    )

    fun byId(id: String): TemplateConfig = list.firstOrNull { it.id == id } ?: list[1]

    /** 将模板展开为可落库的规则集合 */
    fun buildRules(template: TemplateConfig): List<Rule> {
        val weekday = 0b0011111 // 周一~五
        val weekend = 0b1100000 // 周六日

        fun baseRule(
            name: String,
            mask: Int,
            quota: Int
        ) = Rule(
            name = name,
            weekdayMask = mask,
            startMin = 0,
            endMin = 1439,
            mode = RuleMode.NORMAL,
            scopeType = ScopeType.ALL,
            dailyQuotaMin = quota,
            continuousMaxMin = template.continuousMax,
            useXMin = template.useX,
            restYMin = template.restY,
            firstUseExempt = true,
            remindLeadMinutes = 5,
            lockStyle = com.apptime.guard.core.model.LockStyle.FULLSCREEN,
            allowSaveBuffer = true,
            bufferMinutes = 5,
            priority = 10
        )

        val list = mutableListOf<Rule>()
        if (template.id != "custom") {
            list += baseRule("上学日（${template.name}）", weekday, template.dailyQuotaWeekday)
            list += baseRule("周末（${template.name}）", weekend, template.dailyQuotaWeekend)
        } else {
            list += baseRule("每日可用（${template.name}）", 0x7F, template.dailyQuotaWeekday)
        }

        // 分类禁用规则（如游戏）
        if (template.blockCategories.isNotEmpty()) {
            list += Rule(
                name = "分类限制（${template.blockCategories.joinToString("/")}）",
                weekdayMask = 0x7F,
                startMin = 0,
                endMin = 1439,
                mode = RuleMode.NORMAL,
                scopeType = ScopeType.CATEGORY,
                scopeCategories = template.blockCategories.joinToString(","),
                priority = 20
            )
        }

        // 就寝规则
        list += Rule(
            name = "就寝时间",
            weekdayMask = 0x7F,
            startMin = template.bedtimeStart,
            endMin = template.bedtimeEnd,
            mode = RuleMode.BEDTIME,
            scopeType = ScopeType.ALL,
            showClock = true,
            priority = 30
        )

        return list
    }
}
