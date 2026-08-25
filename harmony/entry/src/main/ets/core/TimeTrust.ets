/**
 * 时间可信（HarmonyOS NEXT 版）。
 * 说明：NEXT 系统对普通应用不开放系统级时间篡改检测；
 * 本模块基于单调差值检测本地时间跳变（best-effort），
 * 强管控（防改时间）引导家长使用华为系统「健康使用手机」。
 */
export class TimeTrust {
  /** 检测本地时间是否被大跨度回拨（超过 5 分钟） */
  static detectBackwardJump(lastSystemTime: number): boolean {
    const now = Date.now()
    if (lastSystemTime <= 0) return false
    return (now < lastSystemTime - 5 * 60 * 1000)
  }
}
