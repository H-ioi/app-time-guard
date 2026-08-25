import { router } from '@kit.ArkUI';

/** 锁定/休息提示页（本 App 内，NEXT 能力分级） */
@Entry
@Component
struct LockScreen {
  @State summary: string = '请休息一下'
  @State waitSeconds: number = 0
  private timerId: number = -1

  aboutToAppear(): void {
    const params = router.getParams() as Record<string, Object>
    if (params) {
      if (params['summary'] !== undefined) {
        this.summary = params['summary'] as string
      }
      if (params['wait'] !== undefined) {
        this.waitSeconds = Number(params['wait'])
      }
    }
    if (this.waitSeconds > 0) {
      this.timerId = setInterval(() => {
        this.waitSeconds -= 1
        if (this.waitSeconds <= 0) {
          clearInterval(this.timerId)
        }
      }, 1000)
    }
  }

  aboutToDisappear(): void {
    clearInterval(this.timerId)
  }

  build() {
    Column({ space: 20 }) {
      Text('⏸')
        .fontSize(64)
        .margin({ top: 120 })
      Text(this.summary)
        .fontSize(24)
        .fontWeight(FontWeight.Bold)
        .fontColor('#FFFFFF')
      if (this.waitSeconds > 0) {
        Text(this.formatCountdown())
          .fontSize(56)
          .fontWeight(FontWeight.Bold)
          .fontColor('#FFFFFF')
      }
      Text('需要继续使用？请家长在家长模式中处理')
        .fontSize(14)
        .fontColor('#FFFFFFCC')
      Button('返回')
        .fontColor('#FFFFFF')
        .backgroundColor('#FFFFFF33')
        .onClick(() => {
          router.back()
        })
        .margin({ top: 40 })
    }
    .width('100%')
    .height('100%')
    .backgroundColor('#4F6DF5')
    .alignItems(HorizontalAlign.Center)
  }

  formatCountdown(): string {
    const m = Math.floor(this.waitSeconds / 60)
    const s = this.waitSeconds % 60
    const mm = m < 10 ? '0' + m : '' + m
    const ss = s < 10 ? '0' + s : '' + s
    return mm + ':' + ss
  }
}
