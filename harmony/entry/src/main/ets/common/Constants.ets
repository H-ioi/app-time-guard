/** 全局常量与类型定义（HarmonyOS NEXT 版） */

export enum RuleMode {
  NORMAL = 'normal',
  BEDTIME = 'bedtime',
  FOCUS = 'focus'
}

export enum ScopeType {
  ALL = 'all',
  WHITELIST = 'whitelist',
  BLACKLIST = 'blacklist',
  CATEGORY = 'category'
}

export enum ControlState {
  AVAILABLE = 'available',
  REMINDING = 'reminding',
  COOLING = 'cooling',
  LOCKED = 'locked'
}

export enum CastPolicy {
  COUNT_AS_USE = 'count_as_use',
  LOCK_ON_CAST = 'lock_on_cast',
  ALLOW_IGNORE = 'allow_ignore'
}

export enum SecurityLevel {
  STANDARD = 'standard',
  STRICT = 'strict'
}

/** 规则（与 Android 版对齐） */
export interface Rule {
  id: number
  name: string
  weekdayMask: number
  startMin: number
  endMin: number
  mode: RuleMode
  scopeType: ScopeType
  scopePackages: string
  scopeCategories: string
  dailyQuotaMin: number | null
  continuousMaxMin: number | null
  useXMin: number | null
  restYMin: number | null
  firstUseExempt: boolean
  remindLeadMinutes: number
  bufferMinutes: number
  enabled: boolean
  priority: number
}

/** 今日配额状态 */
export interface QuotaState {
  date: string
  usedMinutes: number
  bonusMinutes: number
  continuousUsedMin: number
  restEndElapsed: number
  lockedUntilElapsed: number
  pauseUntilElapsed: number
  restMinutes: number
  unlockCount: number
  remindStartElapsed: number
  remindNotified: boolean
}

/** 引擎结算结果 */
export interface EngineResult {
  state: ControlState
  summary: string
  remainingMinutes: number
  waitSeconds: number
  blockedApp: string | null
}

/** 模板 */
export interface TemplateConfig {
  id: string
  name: string
  target: string
  dailyQuotaWeekday: number
  dailyQuotaWeekend: number
  continuousMax: number
  useX: number
  restY: number
  bedtimeStart: number
  bedtimeEnd: number
  blockCategories: string[]
  description: string
}

/** 分类定义 */
export const CATEGORIES: string[][] = [
  ['game', '游戏'],
  ['video', '视频'],
  ['social', '社交'],
  ['shopping', '购物'],
  ['store', '应用商店'],
  ['browser', '浏览器'],
  ['reading', '阅读'],
  ['learning', '学习'],
  ['other', '其他']
]

export function categoryLabel(cat: string): string {
  for (const item of CATEGORIES) {
    if (item[0] === cat) {
      return item[1]
    }
  }
  return cat
}

/** 分类猜测（简化规则库） */
export function guessCategory(pkg: string): string {
  const p = pkg.toLowerCase()
  if (p.includes('game')) return 'game'
  if (p.includes('video') || p.includes('tv') || p.includes('bilibili') || p.includes('douyin')) return 'video'
  if (p.includes('weixin') || p.includes('wechat') || p.includes('qq') || p.includes('sina')) return 'social'
  if (p.includes('taobao') || p.includes('jd') || p.includes('pinduoduo')) return 'shopping'
  if (p.includes('market') || p.includes('store')) return 'store'
  if (p.includes('browser') || p.includes('chrome') || p.includes('uc')) return 'browser'
  if (p.includes('reader') || p.includes('book') || p.includes('novel')) return 'reading'
  if (p.includes('learn') || p.includes('edu') || p.includes('study') || p.includes('class')) return 'learning'
  return 'other'
}
