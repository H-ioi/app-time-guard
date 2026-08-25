import { Rule, RuleMode, ScopeType, QuotaState, EngineResult, ControlState, guessCategory } from '../common/Constants';
import { Store } from './Store';

/** 规则引擎（与 Android 版逻辑对齐：最严格优先） */
export class RuleEngine {
  private store: Store = Store.getInstance()

  /** 当前日期键 yyyy-MM-dd */
  todayKey(): string {
    const d = new Date()
    const y = d.getFullYear()
    const m = (d.getMonth() + 1) < 10 ? '0' + (d.getMonth() + 1) : (d.getMonth() + 1)
    const day = d.getDate() < 10 ? '0' + d.getDate() : d.getDate()
    return y + '-' + m + '-' + day
  }

  private weekdayIndex(d: Date): number {
    // 周一=0
    return (d.getDay() + 6) % 7
  }

  private minuteOfDay(d: Date): number {
    return d.getHours() * 60 + d.getMinutes()
  }

  private matchWindow(rule: Rule, weekday: number, nowMin: number): boolean {
    if (!rule.enabled) return false
    if ((rule.weekdayMask & (1 << weekday)) === 0) return false
    if (rule.endMin < rule.startMin) {
      // 跨天
      return nowMin >= rule.startMin || nowMin < rule.endMin
    }
    return nowMin >= rule.startMin && nowMin <= rule.endMin
  }

  private isBlocked(pkg: string, active: Rule[]): boolean {
    for (const rule of active) {
      if (rule.scopeType === ScopeType.ALL) {
        return true
      } else if (rule.scopeType === ScopeType.WHITELIST) {
        if (!rule.scopePackages.split(',').includes(pkg)) return true
      } else if (rule.scopeType === ScopeType.BLACKLIST) {
        if (rule.scopePackages.split(',').includes(pkg)) return true
      } else if (rule.scopeType === ScopeType.CATEGORY) {
        if (rule.scopeCategories.split(',').includes(guessCategory(pkg))) return true
      }
    }
    return false
  }

  /** 主结算 */
  async evaluate(currentPackage: string | null): Promise<EngineResult> {
    const now = Date.now()
    const today = this.todayKey()
    const state = await this.store.getQuota(today)

    // 1. 家长干预
    if (state.pauseUntilElapsed > now) {
      return { state: ControlState.AVAILABLE, summary: '管控暂停中', remainingMinutes: -1, waitSeconds: 0, blockedApp: null }
    }
    if (state.lockedUntilElapsed > now) {
      return {
        state: ControlState.LOCKED, summary: '家长已锁定设备',
        remainingMinutes: state.usedMinutes, waitSeconds: Math.floor((state.lockedUntilElapsed - now) / 1000),
        blockedApp: null
      }
    }

    // 2. 冷却中
    if (state.restEndElapsed > now) {
      return {
        state: ControlState.COOLING, summary: '休息时间',
        remainingMinutes: state.usedMinutes, waitSeconds: Math.floor((state.restEndElapsed - now) / 1000),
        blockedApp: null
      }
    }

    const rules = await this.store.getRules()
    const enabled = rules.filter((r) => r.enabled)
    const nowMin = this.minuteOfDay(new Date(now))
    const weekday = this.weekdayIndex(new Date(now))
    const active = enabled.filter((r) => this.matchWindow(r, weekday, nowMin))

    // 3. 就寝
    const bedtime = active.find((r) => r.mode === RuleMode.BEDTIME)
    if (bedtime !== undefined) {
      return { state: ControlState.LOCKED, summary: bedtime.name || '就寝时间', remainingMinutes: -1, waitSeconds: 0, blockedApp: null }
    }

    // 4. 时段外
    if (enabled.length > 0 && active.length === 0) {
      return { state: ControlState.LOCKED, summary: '不在可用时间段', remainingMinutes: -1, waitSeconds: 0, blockedApp: null }
    }

    // 5. 应用拦截（NEXT 版为弱拦截：检测到受限应用返回 blockedApp，由 UI 提示）
    if (currentPackage !== null && this.isBlocked(currentPackage, active)) {
      return {
        state: ControlState.LOCKED, summary: '该应用已被限制',
        remainingMinutes: -1, waitSeconds: 0, blockedApp: currentPackage
      }
    }

    // 6. 时长结算
    const quotas = active.map((r) => r.dailyQuotaMin).filter((v) => v !== null) as number[]
    const dailyQuota = quotas.length > 0 ? Math.min(...quotas) : null
    const remaining = dailyQuota === null ? -1 : Math.max(0, dailyQuota - (state.usedMinutes - state.bonusMinutes))

    // 6.1 用停交替
    const useX = active.map((r) => r.useXMin).filter((v) => v !== null) as number[]
    const restY = active.map((r) => r.restYMin).filter((v) => v !== null) as number[]
    const x = useX.length > 0 ? Math.min(...useX) : null
    const y = restY.length > 0 ? Math.min(...restY) : null
    if (x !== null && y !== null && state.continuousUsedMin >= x) {
      if (state.usedMinutes === 0 && active.some((r) => r.firstUseExempt)) {
        state.continuousUsedMin = 0
        await this.store.saveQuota(state)
      } else {
        state.restEndElapsed = now + y * 60000
        state.continuousUsedMin = 0
        await this.store.saveQuota(state)
        return {
          state: ControlState.COOLING, summary: '已连续使用 ' + x + ' 分钟，休息 ' + y + ' 分钟',
          remainingMinutes: remaining, waitSeconds: y * 60, blockedApp: null
        }
      }
    }

    // 6.2 每日配额 + 缓冲
    if (dailyQuota !== null && remaining <= 0) {
      const bufferMs = 5 * 60000
      const start = state.remindStartElapsed > 0 ? state.remindStartElapsed : now
      if (state.remindStartElapsed === 0) {
        state.remindStartElapsed = now
        await this.store.saveQuota(state)
      }
      if (now - start >= bufferMs) {
        return { state: ControlState.LOCKED, summary: '今日使用时间已用完', remainingMinutes: 0, waitSeconds: 0, blockedApp: null }
      }
      return {
        state: ControlState.REMINDING, summary: '今日额度已用完（缓冲中）',
        remainingMinutes: 0, waitSeconds: Math.floor((bufferMs - (now - start)) / 1000), blockedApp: null
      }
    }

    if (dailyQuota !== null && remaining <= 5 && remaining > 0) {
      return { state: ControlState.REMINDING, summary: '今日额度剩余 ' + remaining + ' 分钟', remainingMinutes: remaining, waitSeconds: 0, blockedApp: null }
    }

    return {
      state: ControlState.AVAILABLE, summary: active.length > 0 ? active[0].name : '可用',
      remainingMinutes: remaining, waitSeconds: 0, blockedApp: null
    }
  }

  /** 每分钟 tick：使用中累加配额 */
  async tick(inUse: boolean): Promise<void> {
    const today = this.todayKey()
    const state = await this.store.getQuota(today)
    if (inUse) {
      state.usedMinutes += 1
      state.continuousUsedMin += 1
      state.restMinutes = state.restMinutes
    } else if (state.restEndElapsed > 0) {
      state.restMinutes += 1
    }
    await this.store.saveQuota(state)
  }

  /** 今日剩余（供 UI） */
  async remainingMinutes(): Promise<number> {
    const today = this.todayKey()
    const state = await this.store.getQuota(today)
    const rules = await this.store.getRules()
    const enabled = rules.filter((r) => r.enabled && r.mode !== RuleMode.BEDTIME)
    const quotas = enabled.map((r) => r.dailyQuotaMin).filter((v) => v !== null) as number[]
    if (quotas.length === 0) return -1
    const daily = Math.min(...quotas)
    return Math.max(0, daily - (state.usedMinutes - state.bonusMinutes))
  }
}
