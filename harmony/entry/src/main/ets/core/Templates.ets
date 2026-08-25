import { Rule, RuleMode, ScopeType, TemplateConfig } from '../common/Constants';

/** 一键模板（与 Android 版一致） */
export class Templates {
  static readonly list: TemplateConfig[] = [
    {
      id: 'preschool', name: '学龄前（3-6 岁）', target: '3-6 岁',
      dailyQuotaWeekday: 30, dailyQuotaWeekend: 45, continuousMax: 15,
      useX: 15, restY: 20, bedtimeStart: 1230, bedtimeEnd: 420,
      blockCategories: ['game', 'shopping', 'social'],
      description: '每天 30 分钟，连续 15 分钟休息 20 分钟，20:30 就寝'
    },
    {
      id: 'primary', name: '小学生（7-12 岁）', target: '7-12 岁',
      dailyQuotaWeekday: 60, dailyQuotaWeekend: 120, continuousMax: 30,
      useX: 30, restY: 15, bedtimeStart: 1260, bedtimeEnd: 390,
      blockCategories: ['game', 'shopping'],
      description: '上学日每天 60 分钟，连续 30 分钟休息 15 分钟，21:00 就寝'
    },
    {
      id: 'middle', name: '中学生（13-15 岁）', target: '13-15 岁',
      dailyQuotaWeekday: 90, dailyQuotaWeekend: 180, continuousMax: 45,
      useX: 45, restY: 10, bedtimeStart: 1320, bedtimeEnd: 360,
      blockCategories: ['game', 'shopping'],
      description: '上学日每天 90 分钟，连续 45 分钟休息 10 分钟，22:00 就寝'
    },
    {
      id: 'holiday', name: '假期模式', target: '通用',
      dailyQuotaWeekday: 120, dailyQuotaWeekend: 120, continuousMax: 40,
      useX: 40, restY: 15, bedtimeStart: 1290, bedtimeEnd: 420,
      blockCategories: ['shopping'],
      description: '每天 120 分钟，连续 40 分钟休息 15 分钟，21:30 就寝'
    },
    {
      id: 'custom', name: '自定义', target: '家长手动配置',
      dailyQuotaWeekday: 60, dailyQuotaWeekend: 120, continuousMax: 30,
      useX: 30, restY: 15, bedtimeStart: 1260, bedtimeEnd: 420,
      blockCategories: [],
      description: '从空白规则开始，完全按家长意愿配置'
    }
  ]

  static byId(id: string): TemplateConfig {
    for (const t of Templates.list) {
      if (t.id === id) {
        return t
      }
    }
    return Templates.list[1]
  }

  static buildRules(template: TemplateConfig): Rule[] {
    const weekday = 0b0011111
    const weekend = 0b1100000
    const rules: Rule[] = []

    const base = (name: string, mask: number, quota: number): Rule => ({
      id: 0, name: name, weekdayMask: mask, startMin: 0, endMin: 1439,
      mode: RuleMode.NORMAL, scopeType: ScopeType.ALL, scopePackages: '',
      scopeCategories: '', dailyQuotaMin: quota, continuousMaxMin: template.continuousMax,
      useXMin: template.useX, restYMin: template.restY, firstUseExempt: true,
      remindLeadMinutes: 5, bufferMinutes: 5, enabled: true, priority: 10
    })

    if (template.id !== 'custom') {
      rules.push(base('上学日（' + template.name + '）', weekday, template.dailyQuotaWeekday))
      rules.push(base('周末（' + template.name + '）', weekend, template.dailyQuotaWeekend))
    } else {
      rules.push(base('每日可用（' + template.name + '）', 0x7F, template.dailyQuotaWeekday))
    }

    if (template.blockCategories.length > 0) {
      rules.push({
        id: 0, name: '分类限制（' + template.blockCategories.join('/') + '）',
        weekdayMask: 0x7F, startMin: 0, endMin: 1439, mode: RuleMode.NORMAL,
        scopeType: ScopeType.CATEGORY, scopePackages: '',
        scopeCategories: template.blockCategories.join(','),
        dailyQuotaMin: null, continuousMaxMin: null, useXMin: null, restYMin: null,
        firstUseExempt: false, remindLeadMinutes: 5, bufferMinutes: 5,
        enabled: true, priority: 20
      })
    }

    rules.push({
      id: 0, name: '就寝时间', weekdayMask: 0x7F,
      startMin: template.bedtimeStart, endMin: template.bedtimeEnd,
      mode: RuleMode.BEDTIME, scopeType: ScopeType.ALL, scopePackages: '',
      scopeCategories: '', dailyQuotaMin: null, continuousMaxMin: null,
      useXMin: null, restYMin: null, firstUseExempt: false,
      remindLeadMinutes: 5, bufferMinutes: 5, enabled: true, priority: 30
    })

    return rules
  }
}
