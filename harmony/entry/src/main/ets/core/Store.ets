import { preferences } from '@kit.ArkData';
import { common } from '@kit.AbilityKit';
import { Rule, QuotaState } from '../common/Constants';

/** 本地存储：Preferences 封装（纯本地离线） */
export class Store {
  private static instance: Store | null = null
  private pref: preferences.Preferences | null = null

  static getInstance(): Store {
    if (Store.instance === null) {
      Store.instance = new Store()
    }
    return Store.instance
  }

  async init(context: common.UIAbilityContext): Promise<void> {
    if (this.pref === null) {
      this.pref = await preferences.getPreferences(context, 'apptime_store')
    }
  }

  private async get(key: string, def: string): Promise<string> {
    if (this.pref === null) return def
    const value = await this.pref.get(key, def)
    return value as string
  }

  private async put(key: string, value: string): Promise<void> {
    if (this.pref === null) return
    await this.pref.put(key, value)
    await this.pref.flush()
  }

  // ---- 规则 ----

  async getRules(): Promise<Rule[]> {
    const raw = await this.get('rules', '[]')
    try {
      return JSON.parse(raw) as Rule[]
    } catch (e) {
      return []
    }
  }

  async saveRule(rule: Rule): Promise<void> {
    const rules = await this.getRules()
    const idx = rules.findIndex((r) => r.id === rule.id)
    if (idx >= 0) {
      rules[idx] = rule
    } else {
      rule.id = Date.now()
      rules.push(rule)
    }
    await this.put('rules', JSON.stringify(rules))
  }

  async deleteRule(id: number): Promise<void> {
    const rules = await this.getRules()
    await this.put('rules', JSON.stringify(rules.filter((r) => r.id !== id)))
  }

  async replaceRules(rules: Rule[]): Promise<void> {
    await this.put('rules', JSON.stringify(rules))
  }

  // ---- 配额 ----

  async getQuota(date: string): Promise<QuotaState> {
    const raw = await this.get('quota_' + date, '')
    if (raw === '') {
      return {
        date: date, usedMinutes: 0, bonusMinutes: 0, continuousUsedMin: 0,
        restEndElapsed: 0, lockedUntilElapsed: 0, pauseUntilElapsed: 0,
        restMinutes: 0, unlockCount: 0, remindStartElapsed: 0, remindNotified: false
      }
    }
    return JSON.parse(raw) as QuotaState
  }

  async saveQuota(state: QuotaState): Promise<void> {
    await this.put('quota_' + state.date, JSON.stringify(state))
  }

  // ---- 设置 ----

  async getBoolean(key: string, def: boolean): Promise<boolean> {
    return (await this.get(key, def ? '1' : '0')) === '1'
  }

  async setBoolean(key: string, value: boolean): Promise<void> {
    await this.put(key, value ? '1' : '0')
  }

  async getString(key: string, def: string): Promise<string> {
    return this.get(key, def)
  }

  async setString(key: string, value: string): Promise<void> {
    await this.put(key, value)
  }
}
