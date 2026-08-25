import { router } from '@kit.ArkUI';
import { promptAction } from '@kit.ArkUI';
import { Store } from '../core/Store';
import { RuleEngine } from '../core/RuleEngine';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { ControlState } from '../common/Constants';

/** 孩子端主界面：时间 + 今日剩余 + 状态（长按进入家长模式） */
@Entry
@Component
struct ChildHome {
  @State timeText: string = '--:--'
  @State remaining: number = -1
  @State summary: string = '可用'
  @State state: string = 'available'
  @State pinInput: string = ''
  @State showPin: boolean = false
  private store: Store = Store.getInstance()
  private engine: RuleEngine = new RuleEngine()
  private timerId: number = -1

  aboutToAppear(): void {
    this.refreshClock()
    this.refreshState()
    this.timerId = setInterval(() => {
      this.refreshClock()
      this.refreshState()
    }, 30000)
  }

  aboutToDisappear(): void {
    clearInterval(this.timerId)
  }

  refreshClock(): void {
    const d = new Date()
    const h = d.getHours() < 10 ? '0' + d.getHours() : '' + d.getHours()
    const m = d.getMinutes() < 10 ? '0' + d.getMinutes() : '' + d.getMinutes()
    this.timeText = h + ':' + m
  }

  async refreshState(): Promise<void> {
    const result = await this.engine.evaluate(null)
    this.state = result.state
    this.summary = result.summary
    this.remaining = result.remainingMinutes
    if (result.state === ControlState.LOCKED || result.state === ControlState.COOLING) {
      router.replaceUrl({
        url: 'pages/LockScreen',
        params: { summary: result.summary, wait: result.waitSeconds }
      })
    }
  }

  async verifyPin(pin: string): Promise<boolean> {
    const hash = await this.store.getString('pin_hash', '')
    const md = cryptoFramework.createMd('SHA256')
    const data = new Uint8Array(pin.length)
    for (let i = 0; i < pin.length; i++) {
      data[i] = pin.charCodeAt(i)
    }
    md.update({ data: data })
    const result = await md.digest()
    let hex = ''
    for (let i = 0; i < result.data.length; i++) {
      hex += result.data[i].toString(16).padStart(2, '0')
    }
    return hex === hash
  }

  async onLongPressClock(): Promise<void> {
    if (this.showPin) return
    this.showPin = true
  }

  build() {
    Column({ space: 24 }) {
      // 大时钟（长按进入家长模式）
      Column({ space: 4 }) {
        Text(this.timeText)
          .fontSize(72)
          .fontWeight(FontWeight.Bold)
          .fontColor('#4F6DF5')
        Text('长按时钟进入家长模式')
          .fontSize(12)
          .fontColor('#9CA3AF')
      }
      .onLongPress(() => {
        this.onLongPressClock()
      })
      .margin({ top: 80 })

      // 今日剩余
      Column({ space: 8 }) {
        Text(this.remaining >= 0 ? '今日剩余 ' + this.remaining + ' 分钟' : '今日不限')
          .fontSize(22)
          .fontWeight(FontWeight.SemiBold)
        Text(this.summary)
          .fontSize(14)
          .fontColor(this.state === 'locked' ? '#E5484D' : '#4F6DF5')
      }
      .width('85%')
      .padding(20)
      .borderRadius(16)
      .backgroundColor('#FFFFFF')

      // 使用说明
      Text('使用时达到限额会自动提醒并休息，请合理安排时间。')
        .fontSize(13)
        .fontColor('#9CA3AF')
        .margin({ top: 20 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#F7F8FC')
    .alignItems(HorizontalAlign.Center)

    // PIN 验证对话框
    if (this.showPin) {
      Column({ space: 12 }) {
        Text('家长验证')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
        TextInput({ placeholder: '输入家长 PIN', text: this.pinInput })
          .type(InputType.NUMBER_PASSWORD)
          .width('70%')
          .onChange((v: string) => {
            this.pinInput = v
          })
        Row({ space: 16 }) {
          Button('取消')
            .onClick(() => {
              this.showPin = false
              this.pinInput = ''
            })
          Button('确定')
            .onClick(async () => {
              const ok = await this.verifyPin(this.pinInput)
              if (ok) {
                this.showPin = false
                this.pinInput = ''
                router.pushUrl({ url: 'pages/ParentHome' })
              } else {
                promptAction.showToast({ message: 'PIN 错误' })
              }
            })
        }
      }
      .width('85%')
      .padding(20)
      .borderRadius(16)
      .backgroundColor('#FFFFFF')
      .position({ x: '7.5%', y: '35%' })
    }
  }
}
