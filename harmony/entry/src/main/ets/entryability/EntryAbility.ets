import { AbilityConstant, UIAbility, Want } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { window } from '@kit.ArkUI';
import { Store } from '../core/Store';

const TAG: string = 'AppTimeGuard'

export default class EntryAbility extends UIAbility {
  onCreate(want: Want, launchParam: AbilityConstant.LaunchParam): void {
    hilog.info(0x0000, TAG, 'Ability onCreate')
  }

  onWindowStageCreate(windowStage: window.WindowStage): void {
    windowStage.loadContent('pages/Index', async (err) => {
      if (err.code) {
        hilog.error(0x0000, TAG, 'Failed to load content: %{public}s', JSON.stringify(err))
        return
      }
      await Store.getInstance().init(this.context)
      hilog.info(0x0000, TAG, 'Store initialized')
    })
  }

  onDestroy(): void {
    hilog.info(0x0000, TAG, 'Ability onDestroy')
  }
}
