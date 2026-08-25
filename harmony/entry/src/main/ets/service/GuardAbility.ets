import { AccessibilityExtensionAbility, AccessibilityEvent } from '@kit.AccessibilityKit';
import { notificationManager } from '@kit.NotificationKit';
import { RuleEngine } from '../core/RuleEngine';

/**
 * 无障碍守护（NEXT 版能力分级：弱拦截）。
 * NEXT 系统不允许第三方应用强制拦截其他应用；
 * 本服务检测到受限应用处于前台时，发布提醒通知引导退出，
 * 强管控由华为系统「健康使用手机」承担（见需求 05 章 5.9.4）。
 */
export default class GuardAbility extends AccessibilityExtensionAbility {
  private engine: RuleEngine = new RuleEngine()

  onConnect(): void {
    // 服务连接
  }

  onDisconnect(): void {
    // 服务断开
  }

  onEvent(event: AccessibilityEvent): void {
    if (event.eventType === AccessibilityEvent.EVENT_TYPE_WINDOW_STATE_CHANGED) {
      const bundleName = event.bundleName ?? ''
      if (bundleName === '' || bundleName === 'com.apptime.guard') {
        return
      }
      this.checkBlock(bundleName)
    }
  }

  private async checkBlock(bundleName: string): Promise<void> {
    const result = await this.engine.evaluate(bundleName)
    if (result.blockedApp !== null) {
      // 弱拦截：发布提示通知（无法强停，引导家长使用系统方案）
      const request: notificationManager.NotificationRequest = {
        id: 1001,
        content: {
          notificationContentType: notificationManager.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,
          normal: {
            title: '受限应用提醒',
            text: '「' + bundleName + '」已被限制使用，请退出。如需调整请联系家长。',
            additionalText: '强管控请配合华为「健康使用手机」'
          }
        }
      }
      notificationManager.publish(request).catch(() => {
      })
    }
  }
}
