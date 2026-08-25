import { router, promptAction } from '@kit.ArkUI';
import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { notificationManager } from '@kit.NotificationKit';
import { Store } from '../core/Store';
import { Templates } from '../core/Templates';
import { TemplateConfig } from '../common/Constants';

/** 设置向导（NEXT 版简化：PIN + 权限引导 + 模板） */
@Entry
@Component
struct Onboarding {
  @State step: number = 0
  @State pin: string = ''
  @State pin2: string = ''
  @State templateId: string = 'primary'
  @State pinDone: boolean = false
  @State grantDone: boolean = false
  private store: Store = Store.getInstance()

  async sha256(text: string): Promise<string> {
    const md = cryptoFramework.createMd('SHA256')
    const data = new Uint8Array(text.length)
    for (let i = 0; i < text.length; i++) {
      data[i] = text.charCodeAt(i)
    }
    md.update({ data: data })
    const result = await md.digest()
    const bytes = result.data
    let hex = ''
    for (let i = 0; i < bytes.length; i++) {
      hex += bytes[i].toString(16).padStart(2, '0')
    }
    return hex
  }

  async setPin(): Promise<void> {
    if (this.pin.length < 4 || this.pin.length > 6) {
      promptAction.showToast({ message: 'PIN 需为 4~6 位数字' })
      return
    }
    if (this.pin !== this.pin2) {
      promptAction.showToast({ message: '两次输入不一致' })
      return
    }
    const hash = await this.sha256(this.pin)
    await this.store.setString('pin_hash', hash)
    this.pinDone = true
    promptAction.showToast({ message: 'PIN 已设置' })
  }

  async finish(): Promise<void> {
    const template = Templates.byId(this.templateId)
    await this.store.replaceRules(Templates.buildRules(template))
    await this.store.setString('template_id', templateId)
    await this.store.setBoolean('onboarded', true)
    router.replaceUrl({ url: 'pages/ChildHome' })
  }

  build() {
    Column({ space: 16 }) {
      Text('使用守护 · 设置向导')
        .fontSize(24)
        .fontWeight(FontWeight.Bold)
        .margin({ top: 24, bottom: 8 })

      Text('第 ' + (this.step + 1) + ' 步 / 共 3 步')
        .fontSize(14)
        .fontColor('#6B7280')

      if (this.step === 0) {
        this.StepPin()
      } else if (this.step === 1) {
        this.StepPermission()
      } else {
        this.StepTemplate()
      }

      Row({ space: 12 }) {
        if (this.step > 0) {
          Button('上一步')
            .layoutWeight(1)
            .onClick(() => {
              this.step -= 1
            })
        }
        Button(this.step === 2 ? '完成并开始' : '下一步')
          .layoutWeight(1)
          .enabled(this.step !== 0 || this.pinDone)
          .onClick(() => {
            if (this.step === 0 && !this.pinDone) {
              return
            }
            if (this.step < 2) {
              this.step += 1
            } else {
              this.finish()
            }
          })
      }
      .width('90%')
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  StepPin() {
    Column({ space: 12 }) {
      Text('第一步：设置家长 PIN（4~6 位数字）')
        .fontSize(16)
      TextInput({ placeholder: '输入 PIN', text: this.pin })
        .type(InputType.NUMBER_PASSWORD)
        .width('80%')
        .onChange((v: string) => {
          this.pin = v
        })
      TextInput({ placeholder: '再次输入确认', text: this.pin2 })
        .type(InputType.NUMBER_PASSWORD)
        .width('80%')
        .onChange((v: string) => {
          this.pin2 = v
        })
      if (this.pinDone) {
        Text('✅ PIN 已设置')
          .fontColor('#34A853')
      } else {
        Button('确认 PIN')
          .onClick(() => {
            this.setPin()
          })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  StepPermission() {
    Column({ space: 12 }) {
      Text('第二步：开启守护能力')
        .fontSize(16)
        .fontWeight(FontWeight.SemiBold)
      Button('开启无障碍（应用守护）')
        .width('80%')
        .onClick(() => {
          // 引导到系统无障碍设置（NEXT 需用户在系统设置中开启）
          promptAction.showToast({ message: '请在系统设置 > 辅助功能 > 无障碍中开启「使用守护」' })
        })
      Button('开启通知（时长提醒）')
        .width('80%')
        .onClick(() => {
          notificationManager.requestEnableNotification().then((res: boolean) => {
            promptAction.showToast({ message: res ? '通知已开启' : '通知未开启' })
          }).catch(() => {
            promptAction.showToast({ message: '请在系统设置中开启通知' })
          })
        })
      Text('说明：NEXT 系统对第三方应用的管控能力有限，强管控请配合华为「健康使用手机」使用（后续页面会引导）。')
        .fontSize(13)
        .fontColor('#6B7280')
        .padding({ left: 24, right: 24 })
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  StepTemplate() {
    Column({ space: 10 }) {
      Text('第三步：选择年龄模板')
        .fontSize(16)
        .fontWeight(FontWeight.SemiBold)
      ForEach(Templates.list, (t: object) => {
        const template = t as TemplateConfig
        Column({ space: 4 }) {
          Text(template.name)
            .fontSize(16)
            .fontWeight(FontWeight.SemiBold)
          Text(template.description)
            .fontSize(12)
            .fontColor('#6B7280')
        }
        .width('85%')
        .padding(14)
        .borderRadius(12)
        .backgroundColor(this.templateId === template.id ? '#E3E8FF' : '#FFFFFF')
        .onClick(() => {
          this.templateId = template.id
        })
      }, (t: object) => (t as TemplateConfig).id)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Center)
  }
}
