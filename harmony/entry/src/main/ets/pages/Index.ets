import { router } from '@kit.ArkUI';
import { Store } from '../core/Store';

/** 路由入口：按是否完成设置跳转 */
@Entry
@Component
struct Index {
  @State loaded: boolean = false

  aboutToAppear(): void {
    this.route()
  }

  async route(): Promise<void> {
    const store = Store.getInstance()
    const onboarded = await store.getBoolean('onboarded', false)
    if (onboarded) {
      router.replaceUrl({ url: 'pages/ChildHome' })
    } else {
      router.replaceUrl({ url: 'pages/Onboarding' })
    }
  }

  build() {
    Column({ space: 12 }) {
      Text('使用守护')
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .margin({ top: 200 })
      Text('正在加载...')
        .fontSize(14)
        .fontColor('#6B7280')
    }
    .width('100%')
    .height('100%')
  }
}
