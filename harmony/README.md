# 使用守护 · HarmonyOS NEXT 版

面向 HarmonyOS NEXT（5.x，纯血鸿蒙）平板/手机。ArkTS + ArkUI（Stage 模型），按能力分级实现（需求 05 章 5.9）。

## 构建

使用 DevEco Studio 5.0+（API 12）打开 `harmony/` 目录，同步后运行 `entry` 模块即可。
产物：HAP 包，上架华为应用市场（AppGallery）。

## 能力分级（5.9.3 落地情况）

| 能力 | NEXT 版实现 |
| --- | --- |
| 家长/孩子双模式 UI、规则配置、模板 | ✅ `pages/` 全部实现 |
| 规则引擎、配额结算、周计划 | ✅ `core/RuleEngine.ets` |
| 提醒、锁定提示页（本 App 内） | ✅ `pages/LockScreen.ets` + 通知 |
| 使用时长统计 | ⚠️ 本 App 内统计 + 引导华为「健康使用手机」 |
| 拦截受限 App | ⚠️ 弱拦截：无障碍检测 + 提醒通知（`service/GuardAbility.ets`） |
| 防卸载 / 全局锁屏 / 禁用设置 | ❌ 不硬做，引导华为系统方案（设置页说明） |

## 模块结构

```
entry/src/main/ets/
├── entryability/     # UIAbility 入口
├── pages/            # Index / Onboarding / ChildHome / ParentHome / LockScreen
├── core/             # Store（Preferences）/ RuleEngine / Templates / TimeTrust
├── service/          # GuardAbility（无障碍弱拦截）
└── common/           # 常量与类型定义
```

## 与 Android 版差异

- 无 Room：数据用 Preferences（JSON）存储；
- 无设备管理员/前台服务常驻：锁定为「本 App 内提示页」；
- 时间篡改防护为 best-effort，强管控依赖华为系统；
- 家长 PIN 使用 SHA-256 哈希存储（Keystore 级保护在 NEXT 上受限）。
