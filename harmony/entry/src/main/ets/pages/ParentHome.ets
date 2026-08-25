import { router } from '@kit.ArkUI';
import { promptAction } from '@kit.ArkUI';
import { Store } from '../core/Store';
import { RuleEngine } from '../core/RuleEngine';
import { Templates } from '../core/Templates';
import { Rule, QuotaState, SecurityLevel, TemplateConfig } from '../common/Constants';

/** 家长端：首页 / 规则 / 统计 / 设置（Tabs） */
@Entry
@Component
struct ParentHome {
  @State tab: number = 0
  @State remaining: number = -1
  @State used: number = 0
  @State bonus: number = 0
  @State rest: number = 0
  @State rules: Rule[] = []
  @State ruleName: string = ''
  @State ruleQuota: string = ''
  @State templateId: string = 'primary'
  private store: Store = Store.getInstance()
  private engine: RuleEngine = new RuleEngine()

  aboutToAppear(): void {
    this.refresh()
  }

  async refresh(): Promise<void> {
    this.remaining = await this.engine.remainingMinutes()
    const today = this.engine.todayKey()
    const quota = await this.store.getQuota(today)
    this.used = quota.usedMinutes
    this.bonus = quota.bonusMinutes
    this.rest = quota.restMinutes
    this.rules = await this.store.getRules()
    this.templateId = await this.store.getString('template_id', 'primary')
  }

  async lock(minutes: number): Promise<void> {
    const today = this.engine.todayKey()
    const quota = await this.store.getQuota(today)
    quota.lockedUntilElapsed = Date.now() + minutes * 60000
    await this.store.saveQuota(quota)
    promptAction.showToast({ message: '已锁定 ' + minutes + ' 分钟' })
    this.refresh()
  }

  async unlock(): Promise<void> {
    const today = this.engine.todayKey()
    const quota = await this.store.getQuota(today)
    quota.lockedUntilElapsed = 0
    await this.store.saveQuota(quota)
    promptAction.showToast({ message: '已解锁' })
    this.refresh()
  }

  async pause(minutes: number): Promise<void> {
    const today = this.engine.todayKey()
    const quota = await this.store.getQuota(today)
    quota.pauseUntilElapsed = Date.now() + minutes * 60000
    await this.store.saveQuota(quota)
    promptAction.showToast({ message: '已暂停 ' + minutes + ' 分钟' })
    this.refresh()
  }

  async bonus(minutes: number): Promise<void> {
    const today = this.engine.todayKey()
    const quota = await this.store.getQuota(today)
    quota.bonusMinutes += minutes
    await this.store.saveQuota(quota)
    promptAction.showToast({ message: '已发放奖励 ' + minutes + ' 分钟' })
    this.refresh()
  }

  async applyTemplate(id: string): Promise<void> {
    const template = Templates.byId(id)
    await this.store.replaceRules(Templates.buildRules(template))
    await this.store.setString('template_id', id)
    promptAction.showToast({ message: '已应用模板：' + template.name })
    this.refresh()
  }

  async addRule(): Promise<void> {
    const quota = parseInt(this.ruleQuota)
    if (this.ruleName === '') {
      promptAction.showToast({ message: '请输入规则名称' })
      return
    }
    const rule: Rule = {
      id: 0, name: this.ruleName, weekdayMask: 0x7F, startMin: 0, endMin: 1439,
      mode: 'normal', scopeType: 'all', scopePackages: '', scopeCategories: '',
      dailyQuotaMin: isNaN(quota) ? null : quota, continuousMaxMin: null,
      useXMin: null, restYMin: null, firstUseExempt: true,
      remindLeadMinutes: 5, bufferMinutes: 5, enabled: true, priority: 10
    }
    await this.store.saveRule(rule)
    this.ruleName = ''
    this.ruleQuota = ''
    promptAction.showToast({ message: '规则已保存' })
    this.refresh()
  }

  async deleteRule(id: number): Promise<void> {
    await this.store.deleteRule(id)
    this.refresh()
  }

  build() {
    Tabs({ barPosition: BarPosition.End }) {
      TabContent() {
        this.HomeTab()
      }
      .tabBar('首页')

      TabContent() {
        this.RulesTab()
      }
      .tabBar('规则')

      TabContent() {
        this.StatsTab()
      }
      .tabBar('统计')

      TabContent() {
        this.SettingsTab()
      }
      .tabBar('设置')
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  HomeTab() {
    Scroll() {
      Column({ space: 16 }) {
        Text('家长模式')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .alignSelf(ItemAlign.Start)
          .margin({ top: 16 })

        Column({ space: 6 }) {
          Text(this.remaining >= 0 ? '今日剩余 ' + this.remaining + ' 分钟' : '今日不限')
            .fontSize(20)
            .fontWeight(FontWeight.SemiBold)
          Text('已用 ' + this.used + ' 分钟（含奖励 ' + this.bonus + ' 分钟）')
            .fontSize(14)
            .fontColor('#6B7280')
          Text('休息 ' + this.rest + ' 分钟')
            .fontSize(14)
            .fontColor('#6B7280')
        }
        .width('90%')
        .padding(20)
        .borderRadius(16)
        .backgroundColor('#FFFFFF')
        .alignItems(HorizontalAlign.Center)

        Row({ space: 12 }) {
          Button('锁定 30 分').layoutWeight(1).onClick(() => this.lock(30))
          Button('解锁').layoutWeight(1).onClick(() => this.unlock())
        }
        .width('90%')

        Row({ space: 12 }) {
          Button('暂停 10 分').layoutWeight(1).onClick(() => this.pause(10))
          Button('+奖励 15 分').layoutWeight(1).onClick(() => this.bonus(15))
        }
        .width('90%')

        Text('提示：本版为能力分级版，强管控（防卸载/全局锁定）请使用华为「健康使用手机」。')
          .fontSize(13)
          .fontColor('#9CA3AF')
          .width('85%')
      }
      .width('100%')
    }
    .scrollBar(BarState.Off)
  }

  @Builder
  RulesTab() {
    Scroll() {
      Column({ space: 12 }) {
        Text('规则管理')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .alignSelf(ItemAlign.Start)
          .margin({ top: 16 })

        Text('一键模板')
          .fontSize(16)
          .fontWeight(FontWeight.SemiBold)
          .alignSelf(ItemAlign.Start)
        Row({ space: 8 }) {
          ForEach(Templates.list, (t: object) => {
            const template = t as TemplateConfig
            Button(template.name)
              .fontSize(12)
              .backgroundColor(this.templateId === template.id ? '#4F6DF5' : '#E5E7EB')
              .fontColor(this.templateId === template.id ? '#FFFFFF' : '#1F2430')
              .onClick(() => this.applyTemplate(template.id))
          }, (t: object) => (t as TemplateConfig).id)
        }
        .width('100%')
        .justifyContent(FlexAlign.Start)

        Text('当前规则')
          .fontSize(16)
          .fontWeight(FontWeight.SemiBold)
          .alignSelf(ItemAlign.Start)
        ForEach(this.rules, (r: object) => {
          const rule = r as Rule
          Row({ space: 8 }) {
            Column({ space: 2 }) {
              Text(rule.name).fontSize(15).fontWeight(FontWeight.Medium)
              Text('每日 ' + (rule.dailyQuotaMin ?? '不限') + ' 分钟')
                .fontSize(12)
                .fontColor('#6B7280')
            }
            .layoutWeight(1)
            .alignItems(HorizontalAlign.Start)
            Button('删除')
              .fontSize(12)
              .onClick(() => this.deleteRule(rule.id))
          }
          .width('90%')
          .padding(12)
          .borderRadius(12)
          .backgroundColor('#FFFFFF')
        }, (r: object) => (r as Rule).id)

        Text('新建规则')
          .fontSize(16)
          .fontWeight(FontWeight.SemiBold)
          .alignSelf(ItemAlign.Start)
        TextInput({ placeholder: '规则名称', text: this.ruleName })
          .width('90%')
          .onChange((v: string) => {
            this.ruleName = v
          })
        TextInput({ placeholder: '每日时长（分钟，留空不限）', text: this.ruleQuota })
          .type(InputType.NUMBER)
          .width('90%')
          .onChange((v: string) => {
            this.ruleQuota = v
          })
        Button('添加规则')
          .width('90%')
          .onClick(() => this.addRule())
      }
      .width('100%')
    }
    .scrollBar(BarState.Off)
  }

  @Builder
  StatsTab() {
    Scroll() {
      Column({ space: 12 }) {
        Text('统计报告')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .alignSelf(ItemAlign.Start)
          .margin({ top: 16 })
        Text('今日已用 ' + this.used + ' 分钟 · 休息 ' + this.rest + ' 分钟 · 解锁 ' + 'N 次')
          .fontSize(15)
          .fontColor('#374151')
          .width('90%')
          .padding(16)
          .borderRadius(12)
          .backgroundColor('#FFFFFF')
        Text('说明：本版使用统计为「本 App 内 + 引导华为系统统计」。完整全设备统计请查看华为「健康使用手机」。')
          .fontSize(13)
          .fontColor('#6B7280')
          .width('85%')
      }
      .width('100%')
    }
    .scrollBar(BarState.Off)
  }

  @Builder
  SettingsTab() {
    Scroll() {
      Column({ space: 12 }) {
        Text('系统设置')
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .alignSelf(ItemAlign.Start)
          .margin({ top: 16 })
        Text('安全等级：标准级（NEXT 能力分级）')
          .fontSize(15)
          .width('90%')
          .padding(16)
          .borderRadius(12)
          .backgroundColor('#FFFFFF')
        Text('强管控引导：请开启华为系统「健康使用手机」（应用限额/停用时间）与「家长守护」，与本 App 配置并行。')
          .fontSize(13)
          .fontColor('#6B7280')
          .width('85%')
        Text('忘记 PIN：卸载重装后重新设置（数据清除）。')
          .fontSize(13)
          .fontColor('#6B7280')
          .width('85%')
        Button('退出家长模式')
          .width('90%')
          .onClick(() => {
            router.back()
          })
      }
      .width('100%')
    }
    .scrollBar(BarState.Off)
  }
}
